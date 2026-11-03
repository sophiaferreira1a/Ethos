package br.com.projeto.ocorrencias.turma;

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
import br.com.projeto.ocorrencias.usuario.UsuarioLogado;

/**
 * Camada HTTP de turmas, sem banco: contrato da listagem e acesso por perfil.
 */
@WebMvcTest(controllers = TurmaController.class)
@Import({ SecurityConfig.class, RestExceptionHandler.class, UsuarioLogado.class })
class TurmaControllerTest {

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
    void semLoginDevolve401() throws Exception {
        mvc.perform(get("/turmas")).andExpect(status().isUnauthorized());
    }
}
