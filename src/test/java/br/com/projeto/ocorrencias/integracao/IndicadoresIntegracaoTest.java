package br.com.projeto.ocorrencias.integracao;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/**
 * Indicadores contra PostgreSQL real, com massa fixa:
 *
 * <pre>
 *   turma  tipo            data        status
 *   2A     COMPORTAMENTAL  2026-10-05  NAO_LANCADA
 *   2A     COMPORTAMENTAL  2026-10-07  NAO_LANCADA
 *   2A     PEDAGOGICA      2026-10-09  LANCADA
 *   3B     PEDAGOGICA      2026-11-03  LANCADA
 * </pre>
 */
@TesteIntegracao
class IndicadoresIntegracaoTest extends BaseIntegracaoTest {

    private MockHttpSession coordenacao;

    @BeforeEach
    void preparar() throws Exception {
        limparTabelas();
        UUID turma2A = criarTurma("2A");
        UUID turma3B = criarTurma("3B");
        UUID joao = criarAluno("Joao Silva", turma2A);
        UUID ana = criarAluno("Ana Souza", turma3B);

        criarOcorrencia(joao, turma2A, "COMPORTAMENTAL", LocalDate.of(2026, 10, 5), false);
        criarOcorrencia(joao, turma2A, "COMPORTAMENTAL", LocalDate.of(2026, 10, 7), false);
        criarOcorrencia(joao, turma2A, "PEDAGOGICA", LocalDate.of(2026, 10, 9), true);
        criarOcorrencia(ana, turma3B, "PEDAGOGICA", LocalDate.of(2026, 11, 3), true);

        coordenacao = login(EMAIL_COORDENACAO);
    }

    @Test
    void totaisGerais() throws Exception {
        mvc.perform(get("/indicadores").session(coordenacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.naoLancadas").value(2))
                .andExpect(jsonPath("$.lancadas").value(2))
                .andExpect(jsonPath("$.filtro.agrupamento").value("MES"));
    }

    @Test
    void agrupaPorTurmaOrdenandoPelasComMaisOcorrencias() throws Exception {
        mvc.perform(get("/indicadores").session(coordenacao))
                .andExpect(jsonPath("$.porTurma.length()").value(2))
                .andExpect(jsonPath("$.porTurma[0].turma").value("2A"))
                .andExpect(jsonPath("$.porTurma[0].total").value(3))
                .andExpect(jsonPath("$.porTurma[0].naoLancadas").value(2))
                .andExpect(jsonPath("$.porTurma[0].lancadas").value(1))
                .andExpect(jsonPath("$.porTurma[1].turma").value("3B"))
                .andExpect(jsonPath("$.porTurma[1].total").value(1))
                .andExpect(jsonPath("$.porTurma[1].lancadas").value(1));
    }

    @Test
    void agrupaPorTipo() throws Exception {
        mvc.perform(get("/indicadores").session(coordenacao))
                .andExpect(jsonPath("$.porTipo.length()").value(2))
                // empate em total (2 e 2): desempata por nome do tipo
                .andExpect(jsonPath("$.porTipo[0].tipo").value("COMPORTAMENTAL"))
                .andExpect(jsonPath("$.porTipo[0].naoLancadas").value(2))
                .andExpect(jsonPath("$.porTipo[1].tipo").value("PEDAGOGICA"))
                .andExpect(jsonPath("$.porTipo[1].lancadas").value(2));
    }

    @Test
    void agrupaPorMesPorPadraoEmOrdemCronologica() throws Exception {
        mvc.perform(get("/indicadores").session(coordenacao))
                .andExpect(jsonPath("$.porPeriodo.length()").value(2))
                .andExpect(jsonPath("$.porPeriodo[0].periodo").value("2026-10"))
                .andExpect(jsonPath("$.porPeriodo[0].total").value(3))
                .andExpect(jsonPath("$.porPeriodo[1].periodo").value("2026-11"))
                .andExpect(jsonPath("$.porPeriodo[1].total").value(1));
    }

    @Test
    void agrupaPorSemanaRotuladaPelaSegundaFeira() throws Exception {
        mvc.perform(get("/indicadores").param("agrupamento", "SEMANA").session(coordenacao))
                .andExpect(jsonPath("$.porPeriodo.length()").value(2))
                .andExpect(jsonPath("$.porPeriodo[0].periodo").value("2026-10-05"))
                .andExpect(jsonPath("$.porPeriodo[0].total").value(3))
                .andExpect(jsonPath("$.porPeriodo[1].periodo").value("2026-11-02"));
    }

    @Test
    void agrupaPorDia() throws Exception {
        mvc.perform(get("/indicadores").param("agrupamento", "DIA").session(coordenacao))
                .andExpect(jsonPath("$.porPeriodo.length()").value(4))
                .andExpect(jsonPath("$.porPeriodo[0].periodo").value("2026-10-05"))
                .andExpect(jsonPath("$.porPeriodo[3].periodo").value("2026-11-03"));
    }

    @Test
    void filtraPorDataInicial() throws Exception {
        mvc.perform(get("/indicadores").param("de", "2026-10-06").session(coordenacao))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.naoLancadas").value(1))
                .andExpect(jsonPath("$.lancadas").value(2))
                .andExpect(jsonPath("$.filtro.de").value("2026-10-06"));
    }

    @Test
    void filtraPorDataFinalInclusiva() throws Exception {
        mvc.perform(get("/indicadores").param("ate", "2026-10-05").session(coordenacao))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.porTurma[0].turma").value("2A"));
    }

    @Test
    void filtraPorIntervaloInclusivoNasDuasPontas() throws Exception {
        mvc.perform(get("/indicadores").param("de", "2026-10-05").param("ate", "2026-10-09")
                        .session(coordenacao))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.porTurma.length()").value(1))
                .andExpect(jsonPath("$.porPeriodo.length()").value(1));
    }

    @Test
    void periodoSemOcorrenciasDevolveZerosEListasVazias() throws Exception {
        mvc.perform(get("/indicadores").param("de", "2027-01-01").session(coordenacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.naoLancadas").value(0))
                .andExpect(jsonPath("$.lancadas").value(0))
                .andExpect(jsonPath("$.porTurma").isEmpty())
                .andExpect(jsonPath("$.porTipo").isEmpty())
                .andExpect(jsonPath("$.porPeriodo").isEmpty());
    }

    @Test
    void consultaNaoAlteraOsDados() throws Exception {
        long antes = contar("ocorrencias");

        mvc.perform(get("/indicadores").session(coordenacao)).andExpect(status().isOk());

        assertEquals(antes, contar("ocorrencias"));
        assertEquals(0L, contar("historicos"));
    }

    // ----------------------------------------------------------- parametros ruins

    @Test
    void dataForaDoFormatoDevolve400() throws Exception {
        mvc.perform(get("/indicadores").param("de", "07/10/2026").session(coordenacao))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(containsString("'de'")));
    }

    @Test
    void agrupamentoDesconhecidoDevolve400() throws Exception {
        mvc.perform(get("/indicadores").param("agrupamento", "ANO").session(coordenacao))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dataInicialDepoisDaFinalDevolve400() throws Exception {
        mvc.perform(get("/indicadores").param("de", "2026-11-01").param("ate", "2026-10-01")
                        .session(coordenacao))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(containsString("data inicial")));
    }

    // -------------------------------------------------------------- permissoes

    @Test
    void professorNaoAcessaIndicadores() throws Exception {
        mvc.perform(get("/indicadores").session(login(EMAIL_PROFESSOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void semLoginDevolve401() throws Exception {
        mvc.perform(get("/indicadores")).andExpect(status().isUnauthorized());
    }
}
