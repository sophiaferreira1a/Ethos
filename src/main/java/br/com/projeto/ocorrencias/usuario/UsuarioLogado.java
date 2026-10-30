package br.com.projeto.ocorrencias.usuario;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Ponto unico para descobrir quem esta autenticado.
 *
 * <p>Use nos services/controllers no lugar de ler o {@code SecurityContextHolder}
 * na mao:</p>
 *
 * <pre>{@code
 * UUID autor = usuarioLogado.id();
 * historicoService.registrar(ocorrenciaId, autor, AcaoHistorico.OCORRENCIA_CRIADA.name(), null);
 * }</pre>
 */
@Component
public class UsuarioLogado {

    public Usuario usuario() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !autenticacao.isAuthenticated()
                || !(autenticacao.getPrincipal() instanceof Usuario usuario)) {
            throw new IllegalStateException("Nenhum usuario autenticado na requisicao");
        }
        return usuario;
    }

    public UUID id() {
        return usuario().getId();
    }

    public String email() {
        return usuario().getEmail();
    }

    public String nome() {
        return usuario().getNome();
    }

    public Role role() {
        return usuario().getRole();
    }

    public boolean isCoordenacao() {
        return role() == Role.COORDENACAO;
    }
}
