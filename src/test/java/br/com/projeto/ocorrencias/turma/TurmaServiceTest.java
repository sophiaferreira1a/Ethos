package br.com.projeto.ocorrencias.turma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class TurmaServiceTest {

    @Mock
    private TurmaRepository repositorio;

    private TurmaService servico;

    @BeforeEach
    void preparar() {
        servico = new TurmaService(repositorio);
    }

    @Test
    void listarDevolveOQueORepositorioOrdena() {
        Turma turma = turma("6A");
        when(repositorio.findAllByOrderByNomeAsc()).thenReturn(List.of(turma));

        assertThat(servico.listar()).containsExactly(turma);
    }

    @Test
    void buscarPorIdInexistenteLancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(repositorio.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.buscarPorId(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void buscarPorNomeUsaONomeSemEspacos() {
        Turma turma = turma("6A");
        when(repositorio.findByNomeIgnoreCase("6A")).thenReturn(Optional.of(turma));

        assertThat(servico.buscarPorNome("  6A ")).isSameAs(turma);
        verify(repositorio).findByNomeIgnoreCase("6A");
    }

    @Test
    void buscarPorNomeEmBrancoLancaNaoEncontrado() {
        assertThatThrownBy(() -> servico.buscarPorNome("  "))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private static Turma turma(String nome) {
        Turma turma = new Turma(nome);
        ReflectionTestUtils.setField(turma, "id", UUID.randomUUID());
        return turma;
    }
}
