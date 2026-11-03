package br.com.projeto.ocorrencias.aluno;

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

import br.com.projeto.ocorrencias.turma.Turma;
import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class AlunoServiceTest {

    @Mock
    private AlunoRepository repositorio;

    private AlunoService servico;

    @BeforeEach
    void preparar() {
        servico = new AlunoService(repositorio);
    }

    @Test
    void buscarPorIdDevolveAlunoComTurma() {
        Aluno aluno = aluno();
        when(repositorio.findComTurmaById(aluno.getId())).thenReturn(Optional.of(aluno));

        assertThat(servico.buscarPorId(aluno.getId())).isSameAs(aluno);
    }

    @Test
    void buscarPorIdInexistenteLancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(repositorio.findComTurmaById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.buscarPorId(id))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void listarNormalizaBuscaETurmaEmBranco() {
        when(repositorio.filtrar("ana", "6A")).thenReturn(List.of(aluno()));

        assertThat(servico.listar("  ana ", " 6A ")).hasSize(1);
        verify(repositorio).filtrar("ana", "6A");
    }

    @Test
    void listarSemFiltroEnviaNulo() {
        when(repositorio.filtrar(null, null)).thenReturn(List.of());

        assertThat(servico.listar("  ", null)).isEmpty();
        verify(repositorio).filtrar(null, null);
    }

    private static Aluno aluno() {
        Turma turma = new Turma("6A");
        ReflectionTestUtils.setField(turma, "id", UUID.randomUUID());
        Aluno aluno = new Aluno("Ana Clara Souza", turma);
        ReflectionTestUtils.setField(aluno, "id", UUID.randomUUID());
        return aluno;
    }
}
