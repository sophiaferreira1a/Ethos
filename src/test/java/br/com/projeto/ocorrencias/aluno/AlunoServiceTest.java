package br.com.projeto.ocorrencias.aluno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.projeto.ocorrencias.aluno.dto.AlunoRequest;
import br.com.projeto.ocorrencias.aluno.dto.MoverAlunosRequest;
import br.com.projeto.ocorrencias.turma.Turma;
import br.com.projeto.ocorrencias.turma.TurmaRepository;
import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class AlunoServiceTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Mock
    private AlunoRepository repositorio;

    @Mock
    private TurmaRepository turmas;

    private Clock relogio;
    private AlunoService servico;
    private Turma turma;

    @BeforeEach
    void preparar() {
        relogio = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), FUSO);
        servico = new AlunoService(repositorio, turmas, relogio);
        turma = turma("6A");
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

    @Test
    void criarNormalizaNomeESalvaNaTurma() {
        when(turmas.findById(turma.getId())).thenReturn(Optional.of(turma));
        when(repositorio.save(any(Aluno.class))).thenAnswer(chamada -> chamada.getArgument(0));

        Aluno criado = servico.criar(new AlunoRequest("  Ana Clara Souza  ", turma.getId()));

        assertThat(criado.getNome()).isEqualTo("Ana Clara Souza");
        assertThat(criado.getTurma()).isSameAs(turma);
    }

    @Test
    void atualizarTrocaNomeETurma() {
        Aluno aluno = aluno();
        Turma destino = turma("7A");
        when(repositorio.findComTurmaById(aluno.getId())).thenReturn(Optional.of(aluno));
        when(turmas.findById(destino.getId())).thenReturn(Optional.of(destino));

        Aluno atualizado = servico.atualizar(aluno.getId(),
                new AlunoRequest("  Ana Souza ", destino.getId()));

        assertThat(atualizado.getNome()).isEqualTo("Ana Souza");
        assertThat(atualizado.getTurma()).isSameAs(destino);
    }

    @Test
    void excluirFazExclusaoLogica() {
        Aluno aluno = aluno();
        when(repositorio.findComTurmaById(aluno.getId())).thenReturn(Optional.of(aluno));

        servico.excluir(aluno.getId());

        assertThat(aluno.getExcluidoEm()).isEqualTo(Instant.now(relogio));
        verify(repositorio, never()).delete(any(Aluno.class));
        verify(repositorio, never()).deleteById(any());
    }

    @Test
    void moverEmLoteAtualizaTodosParaOTurmaDestino() {
        Aluno primeiro = aluno();
        Aluno segundo = aluno("Bruno Henrique Lima");
        Turma destino = turma("7A");
        when(turmas.findById(destino.getId())).thenReturn(Optional.of(destino));
        when(repositorio.findAllComTurmaByIdIn(List.of(primeiro.getId(), segundo.getId())))
                .thenReturn(List.of(primeiro, segundo));

        List<Aluno> movidos = servico.moverEmLote(
                new MoverAlunosRequest(List.of(primeiro.getId(), segundo.getId()), destino.getId()));

        assertThat(movidos).containsExactly(primeiro, segundo);
        assertThat(primeiro.getTurma()).isSameAs(destino);
        assertThat(segundo.getTurma()).isSameAs(destino);
    }

    @Test
    void moverEmLoteComAlunoInexistenteNaoMoveNinguem() {
        Aluno primeiro = aluno();
        Turma destino = turma("7A");
        UUID inexistente = UUID.randomUUID();
        when(turmas.findById(destino.getId())).thenReturn(Optional.of(destino));
        when(repositorio.findAllComTurmaByIdIn(List.of(primeiro.getId(), inexistente)))
                .thenReturn(List.of(primeiro));

        assertThatThrownBy(() -> servico.moverEmLote(
                new MoverAlunosRequest(List.of(primeiro.getId(), inexistente), destino.getId())))
                .isInstanceOf(EntityNotFoundException.class);
        assertThat(primeiro.getTurma()).isSameAs(turma);
    }

    private Aluno aluno() {
        return aluno("Ana Clara Souza");
    }

    private Aluno aluno(String nome) {
        Aluno aluno = new Aluno(nome, turma);
        ReflectionTestUtils.setField(aluno, "id", UUID.randomUUID());
        return aluno;
    }

    private static Turma turma(String nome) {
        Turma turma = new Turma(nome);
        ReflectionTestUtils.setField(turma, "id", UUID.randomUUID());
        return turma;
    }
}
