package br.com.projeto.ocorrencias.integracao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Base dos testes de integracao: liga o banco de teste, limpa os dados entre testes,
 * cria dados ficticios e faz login real (BCrypt + sessao).
 *
 * <p>Os usuarios usados ({@code coordenacao@escola.com}, {@code professor@escola.com}) vem da
 * migration {@code V30__seed_usuarios_dev.sql}, aplicada pelo Flyway no banco de teste.</p>
 *
 * <p><b>Trava de seguranca:</b> estes testes apagam dados. Eles se recusam a rodar contra
 * qualquer URL que contenha "supabase".</p>
 */
public abstract class BaseIntegracaoTest {

    public static final String VARIAVEL_URL = "TEST_DB_URL";

    protected static final String EMAIL_COORDENACAO = "coordenacao@escola.com";
    protected static final String EMAIL_PROFESSOR = "professor@escola.com";
    protected static final String SENHA_DEV = "Ocorrencia@2026";

    @DynamicPropertySource
    static void bancoDeTeste(DynamicPropertyRegistry registro) {
        String url = System.getenv(VARIAVEL_URL);
        if (url == null || url.isBlank()) {
            return; // classes ficam desabilitadas por @EnabledIfEnvironmentVariable
        }
        if (url.toLowerCase(Locale.ROOT).contains("supabase")) {
            throw new IllegalStateException(
                    "TEST_DB_URL aponta para o Supabase. Testes de integracao apagam dados: use um "
                            + "PostgreSQL separado (docker-compose.test.yml).");
        }
        registro.add("spring.datasource.url", () -> url);
        registro.add("spring.datasource.username", () -> env("TEST_DB_USER", "postgres"));
        registro.add("spring.datasource.password", () -> env("TEST_DB_PASSWORD", "postgres"));
    }

    private static String env(String nome, String padrao) {
        String valor = System.getenv(nome);
        return (valor == null || valor.isBlank()) ? padrao : valor;
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private PlatformTransactionManager gerenciadorTransacao;

    @PersistenceContext
    protected EntityManager em;

    // ------------------------------------------------------------------ infraestrutura

    protected TransactionTemplate transacao() {
        return new TransactionTemplate(gerenciadorTransacao);
    }

    /** Apaga os dados de negocio (mantem {@code usuarios}, que vem do seed). */
    protected void limparTabelas() {
        transacao().executeWithoutResult(status ->
                em.createNativeQuery("truncate table historicos, ocorrencias, alunos, turmas").executeUpdate());
    }

    protected long contar(String tabela) {
        // 'tabela' e sempre uma constante dos proprios testes.
        return ((Number) em.createNativeQuery("select count(*) from " + tabela).getSingleResult()).longValue();
    }

    // ---------------------------------------------------------------------- login

    /** Login real em {@code POST /login}; devolve a sessao (cookie JSESSIONID) para as proximas chamadas. */
    protected MockHttpSession login(String email) throws Exception {
        MvcResult resultado = mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA_DEV + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    // ------------------------------------------------------------ dados ficticios

    protected UUID usuarioId(String email) {
        return (UUID) em.createNativeQuery("select id from usuarios where email = :email")
                .setParameter("email", email)
                .getSingleResult();
    }

    protected UUID criarTurma(String nome) {
        UUID id = UUID.randomUUID();
        transacao().executeWithoutResult(status -> em
                .createNativeQuery("insert into turmas (id, nome) values (:id, :nome)")
                .setParameter("id", id)
                .setParameter("nome", nome)
                .executeUpdate());
        return id;
    }

    protected UUID criarAluno(String nome, UUID turmaId) {
        UUID id = UUID.randomUUID();
        transacao().executeWithoutResult(status -> em
                .createNativeQuery("insert into alunos (id, nome, turma_id) values (:id, :nome, :turma)")
                .setParameter("id", id)
                .setParameter("nome", nome)
                .setParameter("turma", turmaId)
                .executeUpdate());
        return id;
    }

    protected UUID criarOcorrencia(UUID alunoId, UUID turmaId, String tipo, LocalDate data, boolean lancada) {
        UUID id = UUID.randomUUID();
        inserirOcorrencia(id, alunoId, turmaId, tipo, data, lancada);
        return id;
    }

    /** Participa da transacao em andamento, se houver (usado nos testes de atomicidade). */
    protected void inserirOcorrencia(UUID id, UUID alunoId, UUID turmaId, String tipo, LocalDate data,
            boolean lancada) {
        UUID professor = usuarioId(EMAIL_PROFESSOR);
        UUID coordenacao = usuarioId(EMAIL_COORDENACAO);
        String colunas = "id, aluno_id, turma_id, professor_id, tipo, descricao, data";
        String valores = ":id, :aluno, :turma, :professor, :tipo, 'Descricao ficticia', :data";
        if (lancada) {
            colunas += ", status_lancamento, data_lancamento, responsavel_lancamento_id";
            valores += ", 'LANCADA', now(), :responsavel";
        }
        String sql = "insert into ocorrencias (" + colunas + ") values (" + valores + ")";
        transacao().executeWithoutResult(status -> {
            var consulta = em.createNativeQuery(sql)
                    .setParameter("id", id)
                    .setParameter("aluno", alunoId)
                    .setParameter("turma", turmaId)
                    .setParameter("professor", professor)
                    .setParameter("tipo", tipo)
                    .setParameter("data", data);
            if (lancada) {
                consulta.setParameter("responsavel", coordenacao);
            }
            consulta.executeUpdate();
        });
    }

    protected void criarHistorico(UUID ocorrenciaId, UUID usuarioId, String acao, String detalhes,
            Instant dataHora) {
        transacao().executeWithoutResult(status -> em
                .createNativeQuery("insert into historicos (ocorrencia_id, usuario_id, acao, detalhes, data_hora) "
                        + "values (:ocorrencia, :usuario, :acao, :detalhes, :dataHora)")
                .setParameter("ocorrencia", ocorrenciaId)
                .setParameter("usuario", usuarioId)
                .setParameter("acao", acao)
                .setParameter("detalhes", detalhes)
                .setParameter("dataHora", dataHora)
                .executeUpdate());
    }
}
