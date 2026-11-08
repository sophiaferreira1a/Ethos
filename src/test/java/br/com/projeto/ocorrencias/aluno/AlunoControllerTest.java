package br.com.projeto.ocorrencias.aluno;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import br.com.projeto.ocorrencias.aluno.dto.AlunoRequest;
import br.com.projeto.ocorrencias.aluno.dto.MoverAlunosRequest;
import br.com.projeto.ocorrencias.config.RestExceptionHandler;
import br.com.projeto.ocorrencias.config.SecurityConfig;
import br.com.projeto.ocorrencias.turma.Turma;
import br.com.projeto.ocorrencias.usuario.UsuarioLogado;

/**
 * Camada HTTP de alunos, sem banco: busca, CRUD, movimentacao e perfil.
 */
@WebMvcTest(controllers = AlunoController.class)
@Import({ SecurityConfig.class, RestExceptionHandler.class, UsuarioLogado.class })
class AlunoControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TURMA_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthenticationManager gerenciadorAutenticacao;

    @MockitoBean
    private AlunoService service;

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void professorBuscaAlunoPorNomeETurma() throws Exception {
        when(service.listar("ana", "6A")).thenReturn(List.of(new Aluno("Ana Clara Souza", new Turma("6A"))));

        mvc.perform(get("/alunos").param("busca", "ana").param("turma", "6A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Ana Clara Souza"))
                .andExpect(jsonPath("$[0].turma").value("6A"));

        verify(service).listar("ana", "6A");
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void coordenacaoTambemConsultaAlunos() throws Exception {
        when(service.listar(null, null)).thenReturn(List.of());

        mvc.perform(get("/alunos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void coordenacaoCriaAluno() throws Exception {
        when(service.criar(any(AlunoRequest.class))).thenReturn(aluno());

        mvc.perform(post("/alunos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Ana Clara Souza")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.nome").value("Ana Clara Souza"))
                .andExpect(jsonPath("$.turma").value("6A"));
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void alunoInvalidoDevolve400() throws Exception {
        mvc.perform(post("/alunos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").exists())
                .andExpect(jsonPath("$.campos.turmaId").exists());

        verify(service, never()).criar(any());
    }

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void professorNaoCriaAluno() throws Exception {
        mvc.perform(post("/alunos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Ana Clara Souza")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void coordenacaoAtualizaAluno() throws Exception {
        when(service.atualizar(eq(ID), any(AlunoRequest.class))).thenReturn(aluno());

        mvc.perform(put("/alunos/" + ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Ana Souza")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ana Clara Souza"));
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void coordenacaoExcluiAlunoComExclusaoLogica() throws Exception {
        mvc.perform(delete("/alunos/" + ID)).andExpect(status().isNoContent());

        verify(service).excluir(ID);
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void coordenacaoMoveAlunosEmLote() throws Exception {
        when(service.moverEmLote(any(MoverAlunosRequest.class))).thenReturn(List.of(aluno()));

        mvc.perform(patch("/alunos/turma")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alunoIds\":[\"" + ID + "\"],\"turmaId\":\"" + TURMA_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].turma").value("6A"));
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void movimentacaoSemAlunosDevolve400() throws Exception {
        mvc.perform(patch("/alunos/turma")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alunoIds\":[],\"turmaId\":\"" + TURMA_ID + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.alunoIds").exists());

        verify(service, never()).moverEmLote(any());
    }

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void professorNaoMoveAlunos() throws Exception {
        mvc.perform(patch("/alunos/turma")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alunoIds\":[\"" + ID + "\"],\"turmaId\":\"" + TURMA_ID + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void semLoginDevolve401() throws Exception {
        mvc.perform(get("/alunos")).andExpect(status().isUnauthorized());
    }

    private static String corpo(String nome) {
        return "{\"nome\":\"" + nome + "\",\"turmaId\":\"" + TURMA_ID + "\"}";
    }

    private static Aluno aluno() {
        Turma turma = new Turma("6A");
        org.springframework.test.util.ReflectionTestUtils.setField(turma, "id", TURMA_ID);
        Aluno aluno = new Aluno("Ana Clara Souza", turma);
        org.springframework.test.util.ReflectionTestUtils.setField(aluno, "id", ID);
        return aluno;
    }
}
