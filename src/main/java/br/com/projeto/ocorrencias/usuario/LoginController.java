package br.com.projeto.ocorrencias.usuario;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.com.projeto.ocorrencias.usuario.dto.LoginRequest;
import br.com.projeto.ocorrencias.usuario.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Login por sessao (cookie JSESSIONID). Unico endpoint publico da API.
 */
@RestController
public class LoginController {

    private final AuthenticationManager gerenciadorAutenticacao;
    private final SecurityContextRepository repositorioContexto;
    private final UsuarioLogado usuarioLogado;
    private final String destinoProfessor;
    private final String destinoCoordenacao;

    public LoginController(AuthenticationManager gerenciadorAutenticacao,
                           SecurityContextRepository repositorioContexto,
                           UsuarioLogado usuarioLogado,
                           @Value("${app.auth.redirect.professor}") String destinoProfessor,
                           @Value("${app.auth.redirect.coordenacao}") String destinoCoordenacao) {
        this.gerenciadorAutenticacao = gerenciadorAutenticacao;
        this.repositorioContexto = repositorioContexto;
        this.usuarioLogado = usuarioLogado;
        this.destinoProfessor = destinoProfessor;
        this.destinoCoordenacao = destinoCoordenacao;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest pedido,
                               HttpServletRequest requisicao,
                               HttpServletResponse resposta) {
        Authentication autenticacao = gerenciadorAutenticacao.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(pedido.email(), pedido.senha()));

        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacao);
        SecurityContextHolder.setContext(contexto);
        // Com o SecurityContextHolderFilter, salvar explicitamente grava a sessao.
        repositorioContexto.saveContext(contexto, requisicao, resposta);

        return paraResposta((Usuario) autenticacao.getPrincipal());
    }

    @GetMapping("/me")
    public LoginResponse usuarioAtual() {
        return paraResposta(usuarioLogado.usuario());
    }

    private LoginResponse paraResposta(Usuario usuario) {
        String destino = usuario.getRole() == Role.COORDENACAO ? destinoCoordenacao : destinoProfessor;
        return new LoginResponse(usuario.getNome(), usuario.getEmail(), usuario.getRole().name(), destino);
    }
}
