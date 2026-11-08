package br.com.projeto.ocorrencias.turma;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.projeto.ocorrencias.config.ConflitoCadastroException;
import br.com.projeto.ocorrencias.config.RestExceptionHandler;
import br.com.projeto.ocorrencias.config.SecurityConfig;
import br.com.projeto.ocorrencias.turma.dto.TurmaRequest;
import br.com.projeto.ocorrencias.usuario.UsuarioLogado;

/**
 * Camada HTTP de turmas, sem banco: contrato da listagem e CRUD.
 */
@WebMvcTest(controllers = TurmaController.class)
@Import({ SecurityConfig.class, RestExceptionHandler.class, UsuarioLogado.class })
class TurmaControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthenticationManager gerenciadorAutenticacao;

    @MockitoBean
    private TurmaService service;

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void listaTurmasEmOrdem() throws Exception {
        when(service.listar()).thenReturn(List.of(new Turma("6A"), new Turma("7B")));

        mvc.perform(get("/turmas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("6A"))
                .andExpect(jsonPath("$[1].nome").value("7B"));
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void coordenacaoTambemListaTurmas() throws Exception {
        when(service.listar()).thenReturn(List.of());

        mvc.perform(get("/turmas")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void coordenacaoCriaTurma() throws Exception {
        when(service.criar(any(TurmaRequest.class))).thenReturn(turma("6A"));

        mvc.perform(post("/turmas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"6A\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.nome").value("6A"));
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void nomeEmBrancoDevolve400() throws Exception {
        mvc.perform(post("/turmas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").exists());

        verify(service, never()).criar(any());
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void turmaDuplicadaDevolve409() throws Exception {
        when(service.criar(any(TurmaRequest.class)))
                .thenThrow(new ConflitoCadastroException("Ja existe uma turma com esse nome"));

        mvc.perform(post("/turmas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"6A\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Ja existe uma turma com esse nome"));
    }

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void professorNaoCriaTurma() throws Exception {
        mvc.perform(post("/turmas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"6A\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void coordenacaoAtualizaTurma() throws Exception {
        when(service.atualizar(eq(ID), any(TurmaRequest.class))).thenReturn(turma("6B"));

        mvc.perform(put("/turmas/" + ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"6B\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("6B"));
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void coordenacaoExcluiTurma() throws Exception {
        mvc.perform(delete("/turmas/" + ID)).andExpect(status().isNoContent());

        verify(service).excluir(ID);
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void turmaComVinculosDevolve409() throws Exception {
        org.mockito.Mockito.doThrow(new ConflitoCadastroException("Turma possui alunos"))
                .when(service).excluir(ID);

        mvc.perform(delete("/turmas/" + ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Turma possui alunos"));
    }

    @Test
    void semLoginDevolve401() throws Exception {
        mvc.perform(get("/turmas")).andExpect(status().isUnauthorized());
    }

    private static Turma turma(String nome) {
        Turma turma = new Turma(nome);
        org.springframework.test.util.ReflectionTestUtils.setField(turma, "id", ID);
        return turma;
    }
}
