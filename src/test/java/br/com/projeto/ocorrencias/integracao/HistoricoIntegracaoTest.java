package br.com.projeto.ocorrencias.integracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.IllegalTransactionStateException;

import br.com.projeto.ocorrencias.historico.AcaoHistorico;
import br.com.projeto.ocorrencias.historico.HistoricoService;

/**
 * Historico contra PostgreSQL real: gravacao, atomicidade com a acao, consulta ordenada
 * e permissoes (login real, sessao e BCrypt).
 */
@TesteIntegracao
class HistoricoIntegracaoTest extends BaseIntegracaoTest {

    @Autowired
    private HistoricoService historicoService;

    private UUID turmaId;
    private UUID alunoId;

    @BeforeEach
    void preparar() {
        limparTabelas();
        turmaId = criarTurma("2A");
        alunoId = criarAluno("Joao Silva", turmaId);
    }

    // ---------------------------------------------------------------- gravacao

    @Test
    void registrarGravaEventoDentroDaTransacaoDoChamador() {
        UUID ocorrenciaId = criarOcorrencia(alunoId, turmaId, "COMPORTAMENTAL", LocalDate.of(2026, 10, 7), false);
        UUID professor = usuarioId(EMAIL_PROFESSOR);

        transacao().executeWithoutResult(status ->
                historicoService.registrar(ocorrenciaId, professor,
                        AcaoHistorico.OCORRENCIA_CRIADA.name(), "criada pelo professor"));

        assertEquals(1L, contar("historicos"));
        assertEquals(1L, ((Number) em.createNativeQuery(
                        "select count(*) from historicos where acao = 'OCORRENCIA_CRIADA' "
                                + "and detalhes = 'criada pelo professor' and data_hora is not null")
                .getSingleResult()).longValue());
    }

    @Test
    void registrarSemTransacaoFalhaEmVezDeGravarSolto() {
        UUID ocorrenciaId = criarOcorrencia(alunoId, turmaId, "COMPORTAMENTAL", LocalDate.of(2026, 10, 7), false);

        assertThrows(IllegalTransactionStateException.class, () ->
                historicoService.registrar(ocorrenciaId, usuarioId(EMAIL_PROFESSOR),
                        AcaoHistorico.OCORRENCIA_CRIADA.name(), null));

        assertEquals(0L, contar("historicos"));
    }

    @Test
    void acaoInvalidaNaoGravaNada() {
        UUID ocorrenciaId = criarOcorrencia(alunoId, turmaId, "COMPORTAMENTAL", LocalDate.of(2026, 10, 7), false);
        UUID professor = usuarioId(EMAIL_PROFESSOR);

        assertThrows(IllegalArgumentException.class, () -> transacao().executeWithoutResult(status ->
                historicoService.registrar(ocorrenciaId, professor, "ACAO_QUE_NAO_EXISTE", null)));

        assertEquals(0L, contar("historicos"));
    }

    @Test
    void falhaNoHistoricoDesfazAOcorrenciaNaMesmaTransacao() {
        UUID ocorrenciaId = UUID.randomUUID();
        UUID usuarioInexistente = UUID.randomUUID(); // viola a FK de historicos.usuario_id

        assertThrows(DataAccessException.class, () -> transacao().executeWithoutResult(status -> {
            inserirOcorrencia(ocorrenciaId, alunoId, turmaId, "COMPORTAMENTAL", LocalDate.of(2026, 10, 7), false);
            historicoService.registrar(ocorrenciaId, usuarioInexistente,
                    AcaoHistorico.OCORRENCIA_CRIADA.name(), "nao deve ficar");
        }));

        // "Se algo falhar, nenhum dos dois e gravado" (secao 6 da documentacao).
        assertEquals(0L, contar("ocorrencias"));
        assertEquals(0L, contar("historicos"));
    }

    // ---------------------------------------------------------------- consulta

    @Test
    void coordenacaoVeLinhaDoTempoEmOrdemCronologicaComNomeDoUsuario() throws Exception {
        UUID ocorrenciaId = criarOcorrencia(alunoId, turmaId, "COMPORTAMENTAL", LocalDate.of(2026, 10, 7), true);
        Instant inicio = Instant.parse("2026-10-07T12:00:00Z");
        // Inseridos fora de ordem de proposito: a API deve ordenar por data_hora.
        criarHistorico(ocorrenciaId, usuarioId(EMAIL_COORDENACAO), "OCORRENCIA_LANCADA",
                "lancada no sistema oficial", inicio.plusSeconds(3600));
        criarHistorico(ocorrenciaId, usuarioId(EMAIL_PROFESSOR), "OCORRENCIA_CRIADA",
                "criada pelo professor", inicio);

        mvc.perform(get("/ocorrencias/" + ocorrenciaId + "/historico").session(login(EMAIL_COORDENACAO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].acao").value("OCORRENCIA_CRIADA"))
                .andExpect(jsonPath("$[0].usuario.nome").value("Maria Silva"))
                .andExpect(jsonPath("$[0].detalhes").value("criada pelo professor"))
                .andExpect(jsonPath("$[0].dataHora").exists())
                .andExpect(jsonPath("$[1].acao").value("OCORRENCIA_LANCADA"))
                .andExpect(jsonPath("$[1].usuario.nome").value("Ana Coordenacao"));
    }

    @Test
    void historicoNaoMisturaOcorrencias() throws Exception {
        UUID uma = criarOcorrencia(alunoId, turmaId, "COMPORTAMENTAL", LocalDate.of(2026, 10, 7), false);
        UUID outra = criarOcorrencia(alunoId, turmaId, "PEDAGOGICA", LocalDate.of(2026, 10, 8), false);
        criarHistorico(uma, usuarioId(EMAIL_PROFESSOR), "OCORRENCIA_CRIADA", "da primeira",
                Instant.parse("2026-10-07T12:00:00Z"));
        criarHistorico(outra, usuarioId(EMAIL_PROFESSOR), "OCORRENCIA_CRIADA", "da segunda",
                Instant.parse("2026-10-08T12:00:00Z"));

        mvc.perform(get("/ocorrencias/" + uma + "/historico").session(login(EMAIL_COORDENACAO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].detalhes").value("da primeira"));
    }

    @Test
    void ocorrenciaSemEventosDevolveListaVazia() throws Exception {
        UUID ocorrenciaId = criarOcorrencia(alunoId, turmaId, "COMPORTAMENTAL", LocalDate.of(2026, 10, 7), false);

        mvc.perform(get("/ocorrencias/" + ocorrenciaId + "/historico").session(login(EMAIL_COORDENACAO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void ocorrenciaInexistenteDevolve404() throws Exception {
        mvc.perform(get("/ocorrencias/" + UUID.randomUUID() + "/historico").session(login(EMAIL_COORDENACAO)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Ocorrencia nao encontrada"));
    }

    @Test
    void idInvalidoDevolve400() throws Exception {
        mvc.perform(get("/ocorrencias/abc/historico").session(login(EMAIL_COORDENACAO)))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------- permissoes

    @Test
    void professorNaoLeHistorico() throws Exception {
        UUID ocorrenciaId = criarOcorrencia(alunoId, turmaId, "COMPORTAMENTAL", LocalDate.of(2026, 10, 7), false);

        mvc.perform(get("/ocorrencias/" + ocorrenciaId + "/historico").session(login(EMAIL_PROFESSOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void semLoginDevolve401() throws Exception {
        mvc.perform(get("/ocorrencias/" + UUID.randomUUID() + "/historico"))
                .andExpect(status().isUnauthorized());
    }
}
