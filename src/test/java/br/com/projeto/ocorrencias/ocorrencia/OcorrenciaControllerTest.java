package br.com.projeto.ocorrencias.ocorrencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.projeto.ocorrencias.config.RestExceptionHandler;
import br.com.projeto.ocorrencias.config.SecurityConfig;
import br.com.projeto.ocorrencias.ocorrencia.dto.OcorrenciaRequest;
import br.com.projeto.ocorrencias.ocorrencia.dto.OcorrenciaResponse;
import br.com.projeto.ocorrencias.usuario.UsuarioLogado;
import jakarta.persistence.EntityNotFoundException;

/**
 * Camada HTTP de ocorrencias, sem banco: contrato, filtros e permissao por perfil.
 */
@WebMvcTest(controllers = OcorrenciaController.class)
@Import({ SecurityConfig.class, RestExceptionHandler.class, OcorrenciaExceptionHandler.class, UsuarioLogado.class })
class OcorrenciaControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthenticationManager gerenciadorAutenticacao;

    @MockitoBean
    private OcorrenciaService service;

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void professorCriaOcorrencia() throws Exception {
        when(service.criar(any(OcorrenciaRequest.class))).thenReturn(resposta());

        mvc.perform(post("/ocorrencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.alunoNome").value("Ana Clara Souza"))
                .andExpect(jsonPath("$.turmaNome").value("6A"))
                .andExpect(jsonPath("$.statusLancamento").value("NAO_LANCADA"))
                .andExpect(jsonPath("$.tipo").value("INDISCIPLINA"));
    }

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void corpoInvalidoDevolve400SemChamarService() throws Exception {
        mvc.perform(post("/ocorrencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.aluno").exists())
                .andExpect(jsonPath("$.campos.tipo").exists())
                .andExpect(jsonPath("$.campos.descricao").exists());

        verify(service, never()).criar(any());
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void coordenacaoNaoCriaOcorrencia() throws Exception {
        mvc.perform(post("/ocorrencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo()))
                .andExpect(status().isForbidden());
    }

    @Test
    void criarExigeLogin() throws Exception {
        mvc.perform(post("/ocorrencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void listarRepassaFiltros() throws Exception {
        when(service.listar(any(FiltroOcorrencia.class))).thenReturn(List.of(resposta()));
        UUID turmaId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID alunoId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        mvc.perform(get("/ocorrencias")
                        .param("turmaId", turmaId.toString())
                        .param("alunoId", alunoId.toString())
                        .param("tipo", "CELULAR")
                        .param("status", "NAO_LANCADA")
                        .param("dataInicio", "2026-10-01")
                        .param("dataFim", "2026-10-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ID.toString()));

        ArgumentCaptor<FiltroOcorrencia> filtro = ArgumentCaptor.forClass(FiltroOcorrencia.class);
        verify(service).listar(filtro.capture());
        assertThat(filtro.getValue().turmaId()).isEqualTo(turmaId);
        assertThat(filtro.getValue().alunoId()).isEqualTo(alunoId);
        assertThat(filtro.getValue().tipo()).isEqualTo(TipoOcorrencia.CELULAR);
        assertThat(filtro.getValue().status()).isEqualTo(StatusLancamento.NAO_LANCADA);
        assertThat(filtro.getValue().dataInicio()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(filtro.getValue().dataFim()).isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void filtroInvalidoDevolve400() throws Exception {
        mvc.perform(get("/ocorrencias").param("tipo", "COMPORTAMENTAL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(org.hamcrest.Matchers.containsString("tipo")));

        mvc.perform(get("/ocorrencias").param("dataInicio", "07-10-2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void professorNaoListaOcorrencias() throws Exception {
        mvc.perform(get("/ocorrencias")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void buscarPorId() throws Exception {
        when(service.buscar(ID)).thenReturn(resposta());

        mvc.perform(get("/ocorrencias/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professorNome").value("Maria Silva"));
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void idQueNaoEUuidDevolve400() throws Exception {
        mvc.perform(get("/ocorrencias/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(org.hamcrest.Matchers.containsString("id")));
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void inexistenteDevolve404() throws Exception {
        when(service.buscar(ID)).thenThrow(new EntityNotFoundException("Ocorrencia nao encontrada: " + ID));

        mvc.perform(get("/ocorrencias/" + ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Ocorrencia nao encontrada: " + ID));
    }

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void professorAtualizaECancela() throws Exception {
        when(service.atualizar(org.mockito.ArgumentMatchers.eq(ID), any(OcorrenciaRequest.class)))
                .thenReturn(resposta());

        mvc.perform(put("/ocorrencias/" + ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo()))
                .andExpect(status().isOk());

        mvc.perform(delete("/ocorrencias/" + ID))
                .andExpect(status().isNoContent());

        verify(service).excluir(ID);
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void coordenacaoLanca() throws Exception {
        when(service.lancar(ID)).thenReturn(resposta());

        mvc.perform(patch("/ocorrencias/" + ID + "/lancada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusLancamento").value("NAO_LANCADA"));
    }

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void professorNaoLanca() throws Exception {
        mvc.perform(patch("/ocorrencias/" + ID + "/lancada"))
                .andExpect(status().isForbidden());

        verify(service, never()).lancar(any());
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void conflitoDeEstadoDevolve409() throws Exception {
        when(service.lancar(ID)).thenThrow(new ConflitoOcorrenciaException("Ocorrencia ja lancada"));

        mvc.perform(patch("/ocorrencias/" + ID + "/lancada"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Ocorrencia ja lancada"));
    }

    private static String corpo() {
        return """
                {
                  "aluno": {"id": "33333333-3333-3333-3333-333333333333", "nome": "Ana Clara Souza"},
                  "turma": "6A",
                  "tipo": "INDISCIPLINA",
                  "descricao": "Aluno interrompeu a aula varias vezes.",
                  "medidasTomadas": "Conversa com o aluno",
                  "data": "2026-10-30"
                }
                """;
    }

    private static OcorrenciaResponse resposta() {
        return new OcorrenciaResponse(
                ID,
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "Ana Clara Souza",
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "6A",
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "Maria Silva",
                TipoOcorrencia.INDISCIPLINA,
                "Aluno interrompeu a aula varias vezes.",
                "Conversa com o aluno",
                LocalDate.of(2026, 10, 30),
                StatusLancamento.NAO_LANCADA,
                null,
                null,
                null,
                Instant.parse("2026-10-30T15:00:00Z"));
    }
}
