package br.com.projeto.ocorrencias.integracao;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.projeto.ocorrencias.config.RestExceptionHandler;
import br.com.projeto.ocorrencias.config.SecurityConfig;
import br.com.projeto.ocorrencias.historico.HistoricoConsultaService;
import br.com.projeto.ocorrencias.historico.HistoricoController;
import br.com.projeto.ocorrencias.historico.dto.HistoricoResponse;
import br.com.projeto.ocorrencias.historico.dto.HistoricoResponse.UsuarioResumo;
import br.com.projeto.ocorrencias.indicadores.Agrupamento;
import br.com.projeto.ocorrencias.indicadores.FiltroInvalidoException;
import br.com.projeto.ocorrencias.indicadores.IndicadoresController;
import br.com.projeto.ocorrencias.indicadores.IndicadoresService;
import br.com.projeto.ocorrencias.indicadores.dto.IndicadoresResponse;
import br.com.projeto.ocorrencias.indicadores.dto.IndicadoresResponse.FiltroAplicado;
import br.com.projeto.ocorrencias.indicadores.dto.IndicadoresResponse.PorTurma;
import br.com.projeto.ocorrencias.usuario.UsuarioLogado;
import jakarta.persistence.EntityNotFoundException;

/**
 * Camada web dos endpoints do Bernardo (sem banco): contrato JSON, validacao de
 * parametros e permissao por perfil. Roda sempre no {@code mvnw test}.
 */
@WebMvcTest(controllers = { IndicadoresController.class, HistoricoController.class })
@Import({ SecurityConfig.class, RestExceptionHandler.class, UsuarioLogado.class })
class HistoricoEIndicadoresControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthenticationManager gerenciadorAutenticacao;

    @MockitoBean
    private IndicadoresService indicadoresService;

    @MockitoBean
    private HistoricoConsultaService historicoConsultaService;

    // ---------------------------------------------------------------- indicadores

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void indicadoresDevolveContratoJson() throws Exception {
        LocalDate de = LocalDate.of(2026, 10, 1);
        LocalDate ate = LocalDate.of(2026, 10, 31);
        when(indicadoresService.consultar(de, ate, Agrupamento.SEMANA)).thenReturn(new IndicadoresResponse(
                4, 3, 1, new FiltroAplicado(de, ate, "SEMANA"),
                List.of(new PorTurma("2A", 4, 3, 1)), List.of(), List.of()));

        mvc.perform(get("/indicadores")
                        .param("de", "2026-10-01").param("ate", "2026-10-31").param("agrupamento", "SEMANA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.naoLancadas").value(3))
                .andExpect(jsonPath("$.lancadas").value(1))
                .andExpect(jsonPath("$.filtro.agrupamento").value("SEMANA"))
                .andExpect(jsonPath("$.porTurma[0].turma").value("2A"))
                .andExpect(jsonPath("$.porTurma[0].naoLancadas").value(3))
                .andExpect(jsonPath("$.porTipo").isArray())
                .andExpect(jsonPath("$.porPeriodo").isArray());
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void semParametrosUsaMesEPeriodoAberto() throws Exception {
        when(indicadoresService.consultar(null, null, Agrupamento.MES)).thenReturn(new IndicadoresResponse(
                0, 0, 0, new FiltroAplicado(null, null, "MES"), List.of(), List.of(), List.of()));

        mvc.perform(get("/indicadores")).andExpect(status().isOk());

        verify(indicadoresService).consultar(null, null, Agrupamento.MES);
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void dataForaDoFormatoDevolve400() throws Exception {
        mvc.perform(get("/indicadores").param("de", "07/10/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(containsString("'de'")));
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void agrupamentoDesconhecidoDevolve400() throws Exception {
        mvc.perform(get("/indicadores").param("agrupamento", "ANO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(containsString("DIA, SEMANA ou MES")));
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void filtroIncoerenteDevolve400ComMensagem() throws Exception {
        when(indicadoresService.consultar(any(), any(), any()))
                .thenThrow(new FiltroInvalidoException("A data inicial nao pode ser posterior a data final"));

        mvc.perform(get("/indicadores").param("de", "2026-11-01").param("ate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A data inicial nao pode ser posterior a data final"));
    }

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void professorNaoAcessaIndicadores() throws Exception {
        mvc.perform(get("/indicadores")).andExpect(status().isForbidden());
    }

    @Test
    void indicadoresExigeLogin() throws Exception {
        mvc.perform(get("/indicadores")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ historico

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void historicoDevolveLinhaDoTempoComUsuarioAninhado() throws Exception {
        UUID ocorrenciaId = UUID.randomUUID();
        when(historicoConsultaService.listarPorOcorrencia(ocorrenciaId)).thenReturn(List.of(
                new HistoricoResponse(UUID.randomUUID(), "OCORRENCIA_CRIADA", "criada pelo professor",
                        Instant.parse("2026-10-07T15:30:00Z"), new UsuarioResumo(UUID.randomUUID(), "Maria Silva"))));

        mvc.perform(get("/ocorrencias/" + ocorrenciaId + "/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].acao").value("OCORRENCIA_CRIADA"))
                .andExpect(jsonPath("$[0].detalhes").value("criada pelo professor"))
                .andExpect(jsonPath("$[0].dataHora").exists())
                .andExpect(jsonPath("$[0].usuario.nome").value("Maria Silva"));
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void ocorrenciaInexistenteDevolve404() throws Exception {
        UUID ocorrenciaId = UUID.randomUUID();
        when(historicoConsultaService.listarPorOcorrencia(ocorrenciaId))
                .thenThrow(new EntityNotFoundException("Ocorrencia nao encontrada"));

        mvc.perform(get("/ocorrencias/" + ocorrenciaId + "/historico"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Ocorrencia nao encontrada"));
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void idQueNaoEUuidDevolve400() throws Exception {
        mvc.perform(get("/ocorrencias/abc/historico"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Identificador de ocorrencia invalido"));
    }

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void professorNaoLeHistorico() throws Exception {
        mvc.perform(get("/ocorrencias/" + UUID.randomUUID() + "/historico"))
                .andExpect(status().isForbidden());
    }

    @Test
    void historicoExigeLogin() throws Exception {
        mvc.perform(get("/ocorrencias/" + UUID.randomUUID() + "/historico"))
                .andExpect(status().isUnauthorized());
    }
}
