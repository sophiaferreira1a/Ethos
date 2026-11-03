package br.com.projeto.ocorrencias.aluno;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import br.com.projeto.ocorrencias.turma.Turma;
import br.com.projeto.ocorrencias.usuario.UsuarioLogado;

/**
 * Camada HTTP de alunos, sem banco: contrato da busca e acesso por perfil.
 */
@WebMvcTest(controllers = AlunoController.class)
@Import({ SecurityConfig.class, RestExceptionHandler.class, UsuarioLogado.class })
class AlunoControllerTest {

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
    void semLoginDevolve401() throws Exception {
        mvc.perform(get("/alunos")).andExpect(status().isUnauthorized());
    }
}
