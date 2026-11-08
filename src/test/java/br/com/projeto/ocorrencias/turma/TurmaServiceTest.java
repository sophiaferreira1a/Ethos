package br.com.projeto.ocorrencias.turma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

import br.com.projeto.ocorrencias.aluno.AlunoRepository;
import br.com.projeto.ocorrencias.config.ConflitoCadastroException;
import br.com.projeto.ocorrencias.ocorrencia.OcorrenciaRepository;
import br.com.projeto.ocorrencias.turma.dto.TurmaRequest;
import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class TurmaServiceTest {

    @Mock
    private TurmaRepository repositorio;

    @Mock
    private AlunoRepository alunos;

    @Mock
    private OcorrenciaRepository ocorrencias;

    private TurmaService servico;

    @BeforeEach
    void preparar() {
        servico = new TurmaService(repositorio, alunos, ocorrencias);
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

    @Test
    void criarNormalizaNomeESalva() {
        when(repositorio.existsByNomeIgnoreCase("6A")).thenReturn(false);
        when(repositorio.save(any(Turma.class))).thenAnswer(chamada -> chamada.getArgument(0));

        Turma criada = servico.criar(new TurmaRequest("  6A  "));

        assertThat(criada.getNome()).isEqualTo("6A");
        verify(repositorio).existsByNomeIgnoreCase("6A");
    }

    @Test
    void criarComNomeDuplicadoLancaConflito() {
        when(repositorio.existsByNomeIgnoreCase("6A")).thenReturn(true);

        assertThatThrownBy(() -> servico.criar(new TurmaRequest(" 6A ")))
                .isInstanceOf(ConflitoCadastroException.class);
        verify(repositorio, never()).save(any(Turma.class));
    }

    @Test
    void atualizarRenomeiaETroca() {
        Turma turma = turma("6A");
        UUID id = turma.getId();
        when(repositorio.findById(id)).thenReturn(Optional.of(turma));
        when(repositorio.existsByNomeIgnoreCaseAndIdNot("6B", id)).thenReturn(false);

        Turma atualizada = servico.atualizar(id, new TurmaRequest(" 6B "));

        assertThat(atualizada.getNome()).isEqualTo("6B");
    }

    @Test
    void excluirTurmaComAlunosOuOcorrenciasLancaConflito() {
        Turma turma = turma("6A");
        UUID id = turma.getId();
        when(repositorio.findById(id)).thenReturn(Optional.of(turma));
        when(alunos.existsByTurmaId(id)).thenReturn(true);

        assertThatThrownBy(() -> servico.excluir(id))
                .isInstanceOf(ConflitoCadastroException.class);
        verify(repositorio, never()).deleteById(id);

        when(alunos.existsByTurmaId(id)).thenReturn(false);
        when(ocorrencias.existsByTurmaId(id)).thenReturn(true);

        assertThatThrownBy(() -> servico.excluir(id))
                .isInstanceOf(ConflitoCadastroException.class);
    }

    @Test
    void excluirTurmaSemVinculosApaga() {
        Turma turma = turma("6A");
        UUID id = turma.getId();
        when(repositorio.findById(id)).thenReturn(Optional.of(turma));
        when(alunos.existsByTurmaId(id)).thenReturn(false);
        when(ocorrencias.existsByTurmaId(id)).thenReturn(false);

        servico.excluir(id);

        verify(repositorio).deleteById(id);
    }

    private static Turma turma(String nome) {
        Turma turma = new Turma(nome);
        ReflectionTestUtils.setField(turma, "id", UUID.randomUUID());
        return turma;
    }
}
