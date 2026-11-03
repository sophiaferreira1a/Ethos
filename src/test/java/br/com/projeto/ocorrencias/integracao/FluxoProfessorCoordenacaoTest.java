package br.com.projeto.ocorrencias.integracao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

/**
 * Fluxo completo da documentacao (secoes 1 e 14): Professor busca o aluno e registra ->
 * Coordenacao visualiza e lanca -> historico e indicadores refletem. Por fim, uma
 * ocorrencia cancelada (DELETE) some da lista e das contagens.
 */
@TesteIntegracao
class FluxoProfessorCoordenacaoTest extends BaseIntegracaoTest {

    private static final String CORPO = """
            {
              "aluno": { "id": "%s", "nome": "Joao Silva" },
              "turma": "2A",
              "tipo": "%s",
              "descricao": "Conversa excessiva durante a aula",
              "medidasTomadas": "Conversa com o aluno",
              "data": "%s"
            }
            """;

    @Test
    void professorRegistraCoordenacaoLancaEHistoricoEIndicadoresAcompanham() throws Exception {
        limparTabelas();
        UUID turmaId = criarTurma("2A");
        UUID alunoId = criarAluno("Joao Silva", turmaId);
        String ontem = LocalDate.now().minusDays(1).toString();

        MockHttpSession professor = login(EMAIL_PROFESSOR);
        MockHttpSession coordenacao = login(EMAIL_COORDENACAO);

        // 1. Professor encontra o aluno pela busca e registra.
        mvc.perform(get("/alunos").param("busca", "joao").param("turma", "2A").session(professor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(alunoId.toString()))
                .andExpect(jsonPath("$[0].turma").value("2A"));

        MvcResult criada = mvc.perform(post("/ocorrencias").session(professor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO.formatted(alunoId, "INDISCIPLINA", ontem)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusLancamento").value("NAO_LANCADA"))
                .andExpect(jsonPath("$.professorNome").value("Maria Silva"))
                .andReturn();
        String ocorrenciaId = JsonPath.read(criada.getResponse().getContentAsString(), "$.id");

        // 2. Professor nao executa acoes da coordenacao.
        mvc.perform(patch("/ocorrencias/" + ocorrenciaId + "/lancada").session(professor))
                .andExpect(status().isForbidden());
        mvc.perform(get("/ocorrencias/" + ocorrenciaId + "/historico").session(professor))
                .andExpect(status().isForbidden());

        // 3. Antes de lancar: 1 nao lancada e historico so com a criacao.
        mvc.perform(get("/ocorrencias").session(coordenacao))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/indicadores").session(coordenacao))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.naoLancadas").value(1))
                .andExpect(jsonPath("$.lancadas").value(0));
        mvc.perform(get("/ocorrencias/" + ocorrenciaId + "/historico").session(coordenacao))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].acao").value("OCORRENCIA_CRIADA"))
                .andExpect(jsonPath("$[0].usuario.nome").value("Maria Silva"));

        // 4. Coordenacao lanca.
        mvc.perform(patch("/ocorrencias/" + ocorrenciaId + "/lancada").session(coordenacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusLancamento").value("LANCADA"))
                .andExpect(jsonPath("$.responsavelLancamentoNome").value("Ana Coordenacao"));

        // 5. Depois: indicadores e historico refletem o lancamento.
        mvc.perform(get("/indicadores").session(coordenacao))
                .andExpect(jsonPath("$.naoLancadas").value(0))
                .andExpect(jsonPath("$.lancadas").value(1));
        mvc.perform(get("/ocorrencias/" + ocorrenciaId + "/historico").session(coordenacao))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].acao").value("OCORRENCIA_LANCADA"))
                .andExpect(jsonPath("$[1].usuario.nome").value("Ana Coordenacao"));

        // 6. Uma segunda ocorrencia, registrada por engano e cancelada, nao conta em lugar nenhum.
        MvcResult engano = mvc.perform(post("/ocorrencias").session(professor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO.formatted(alunoId, "CELULAR", ontem)))
                .andExpect(status().isCreated())
                .andReturn();
        String enganoId = JsonPath.read(engano.getResponse().getContentAsString(), "$.id");

        mvc.perform(delete("/ocorrencias/" + enganoId).session(coordenacao))
                .andExpect(status().isNoContent());
        mvc.perform(get("/ocorrencias").session(coordenacao))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/indicadores").session(coordenacao))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.naoLancadas").value(0))
                .andExpect(jsonPath("$.lancadas").value(1));
    }
}
