package br.com.projeto.ocorrencias.seguranca;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.projeto.ocorrencias.config.RestExceptionHandler;
import br.com.projeto.ocorrencias.config.SecurityConfig;
import br.com.projeto.ocorrencias.usuario.LoginController;
import br.com.projeto.ocorrencias.usuario.Role;
import br.com.projeto.ocorrencias.usuario.Usuario;
import br.com.projeto.ocorrencias.usuario.UsuarioLogado;

/**
 * Testes de tentativa de acesso indevido (item 6 do Bloco 2).
 */
@WebMvcTest(controllers = LoginController.class)
@Import({ SecurityConfig.class, RestExceptionHandler.class, UsuarioLogado.class })
class AutenticacaoEAutorizacaoTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthenticationManager gerenciadorAutenticacao;

    @Test
    void loginComCredenciaisValidasDevolvePerfilEDestino() throws Exception {
        Usuario professor = new Usuario("Maria Silva", "maria@escola.com", "hash", Role.PROFESSOR);
        when(gerenciadorAutenticacao.authenticate(any())).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(professor, null, professor.getAuthorities()));

        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"maria@escola.com\",\"senha\":\"senha123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.role").value("PROFESSOR"))
                .andExpect(jsonPath("$.redirectTo").value("/professor"))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(request().sessionAttribute("SPRING_SECURITY_CONTEXT", notNullValue()));
    }

    @Test
    void loginComSenhaErradaDevolve401SemRevelarCausa() throws Exception {
        when(gerenciadorAutenticacao.authenticate(any()))
                .thenThrow(new BadCredentialsException("senha invalida"));

        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"maria@escola.com\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("E-mail ou senha invalidos"));
    }

    @Test
    void loginComEmailInvalidoDevolve400() throws Exception {
        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nao-e-email\",\"senha\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.email").exists())
                .andExpect(jsonPath("$.campos.senha").exists());
    }

    @Test
    void semAutenticacaoIndicadoresDevolve401() throws Exception {
        mvc.perform(get("/indicadores"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void professorNaoAcessaIndicadores() throws Exception {
        mvc.perform(get("/indicadores"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("Acesso negado para o seu perfil"));
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void coordenacaoPassaPeloFiltroDeIndicadores() throws Exception {
        // Ainda nao existe controller de indicadores: 404 prova que o filtro liberou.
        mvc.perform(get("/indicadores"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "COORDENACAO")
    void coordenacaoNaoCriaOcorrencia() throws Exception {
        mvc.perform(post("/ocorrencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void professorNaoMarcaOcorrenciaComoLancada() throws Exception {
        mvc.perform(patch("/ocorrencias/" + UUID.randomUUID() + "/lancada"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void professorNaoLeHistorico() throws Exception {
        mvc.perform(get("/ocorrencias/" + UUID.randomUUID() + "/historico"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void professorPodeCriarOcorrencia() throws Exception {
        // Sem controller ainda: 404 prova que o filtro nao barrou o perfil.
        mvc.perform(post("/ocorrencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void preflightCorsLiberaFrontendLocal() throws Exception {
        mvc.perform(options("/login")
                        .header("Origin", "http://localhost:5500")
                        .header("Access-Control-Request-Method", HttpMethod.POST.name()))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", containsString("http://localhost:5500")));
    }
}
