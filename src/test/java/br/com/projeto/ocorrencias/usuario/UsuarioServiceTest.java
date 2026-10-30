package br.com.projeto.ocorrencias.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository repositorio;

    private final PasswordEncoder codificador = new BCryptPasswordEncoder();

    private UsuarioService servico;

    @BeforeEach
    void preparar() {
        servico = new UsuarioService(repositorio, codificador);
    }

    @Test
    void criarGuardaSomenteHashBcryptNuncaASenha() {
        when(repositorio.existsByEmailIgnoreCase("maria@escola.com")).thenReturn(false);
        when(repositorio.save(any(Usuario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        Usuario criado = servico.criar("Maria Silva", "  MARIA@Escola.com ", "senha123", Role.PROFESSOR);

        assertThat(criado.getSenhaHash()).startsWith("$2");
        assertThat(criado.getSenhaHash()).isNotEqualTo("senha123");
        assertThat(codificador.matches("senha123", criado.getSenhaHash())).isTrue();
        assertThat(criado.getEmail()).isEqualTo("maria@escola.com");
        assertThat(criado.getRole()).isEqualTo(Role.PROFESSOR);
        assertThat(criado.isAtivo()).isTrue();
    }

    @Test
    void criarComEmailRepetidoLancaConflitoDeNegocio() {
        when(repositorio.existsByEmailIgnoreCase("maria@escola.com")).thenReturn(true);

        assertThatThrownBy(() -> servico.criar("Outra", "maria@escola.com", "senha123", Role.PROFESSOR))
                .isInstanceOf(EmailJaCadastradoException.class);
    }

    @Test
    void loginComEmailInexistenteLancaUsernameNotFound() {
        when(repositorio.findByEmailIgnoreCase("ninguem@escola.com")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> servico.loadUserByUsername("ninguem@escola.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void autoridadesSeguemOPadraoRoleDoSpringSecurity() {
        Usuario coordenacao = new Usuario("Ana", "ana@escola.com", "hash", Role.COORDENACAO);

        assertThat(coordenacao.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_COORDENACAO");
        assertThat(coordenacao.getUsername()).isEqualTo("ana@escola.com");
        assertThat(coordenacao.isEnabled()).isTrue();
    }
}
