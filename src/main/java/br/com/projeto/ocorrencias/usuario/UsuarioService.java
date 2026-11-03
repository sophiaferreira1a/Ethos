package br.com.projeto.ocorrencias.usuario;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

/**
 * Regras de usuario e ponte com o Spring Security ({@link UserDetailsService}).
 *
 * <p>A senha em texto puro nunca e guardada: o service recebe a senha, gera o hash BCrypt
 * e persiste apenas o hash.</p>
 */
@Service
@Transactional(readOnly = true)
public class UsuarioService implements UserDetailsService {

    private final UsuarioRepository repositorio;
    private final PasswordEncoder codificadorSenha;

    public UsuarioService(UsuarioRepository repositorio, PasswordEncoder codificadorSenha) {
        this.repositorio = repositorio;
        this.codificadorSenha = codificadorSenha;
    }

    @Transactional
    public Usuario criar(String nome, String email, String senha, Role role) {
        String emailNormalizado = Usuario.normalizarEmail(email);
        if (repositorio.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new EmailJaCadastradoException(emailNormalizado);
        }
        String hash = codificadorSenha.encode(senha);
        return repositorio.save(new Usuario(nome, emailNormalizado, hash, role));
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return buscarPorEmail(email);
    }

    public Usuario buscarPorEmail(String email) {
        return repositorio.findByEmailIgnoreCase(Usuario.normalizarEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Usuario nao encontrado: " + email));
    }

    public Usuario buscarPorId(UUID id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuario nao encontrado: " + id));
    }

    public List<Usuario> listar() {
        return repositorio.findAllByOrderByNomeAsc();
    }

    @Transactional
    public void desativar(UUID id) {
        buscarPorId(id).desativar();
    }
}
