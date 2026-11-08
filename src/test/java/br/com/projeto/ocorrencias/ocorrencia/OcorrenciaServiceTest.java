package br.com.projeto.ocorrencias.ocorrencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import br.com.projeto.ocorrencias.aluno.Aluno;
import br.com.projeto.ocorrencias.aluno.AlunoRepository;
import br.com.projeto.ocorrencias.historico.AcaoHistorico;
import br.com.projeto.ocorrencias.historico.HistoricoService;
import br.com.projeto.ocorrencias.ocorrencia.dto.OcorrenciaLoteRequest;
import br.com.projeto.ocorrencias.ocorrencia.dto.OcorrenciaRequest;
import br.com.projeto.ocorrencias.ocorrencia.dto.OcorrenciaResponse;
import br.com.projeto.ocorrencias.turma.Turma;
import br.com.projeto.ocorrencias.usuario.Role;
import br.com.projeto.ocorrencias.usuario.Usuario;
import br.com.projeto.ocorrencias.usuario.UsuarioLogado;
import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class OcorrenciaServiceTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Mock
    private OcorrenciaRepository repositorio;

    @Mock
    private AlunoRepository alunos;

    @Mock
    private UsuarioLogado usuarioLogado;

    @Mock
    private HistoricoService historico;

    private Clock relogio;
    private OcorrenciaService servico;
    private Turma turma;
    private Aluno aluno;
    private Aluno outroAluno;
    private Usuario professor;
    private UUID idGerado;

    @BeforeEach
    void preparar() {
        relogio = Clock.fixed(Instant.parse("2026-10-30T15:00:00Z"), FUSO);
        servico = new OcorrenciaService(repositorio, alunos, usuarioLogado, historico, relogio);
        turma = new Turma("6A");
        ReflectionTestUtils.setField(turma, "id", UUID.randomUUID());
        aluno = new Aluno("Ana Clara Souza", turma);
        ReflectionTestUtils.setField(aluno, "id", UUID.randomUUID());
        outroAluno = new Aluno("Bruno Henrique Lima", turma);
        ReflectionTestUtils.setField(outroAluno, "id", UUID.randomUUID());
        professor = usuario("Maria Silva", "professor@escola.com", Role.PROFESSOR);
        idGerado = UUID.randomUUID();

        lenient().when(usuarioLogado.role()).thenReturn(Role.PROFESSOR);
        lenient().when(usuarioLogado.usuario()).thenReturn(professor);
        lenient().when(usuarioLogado.id()).thenReturn(professor.getId());
        lenient().when(usuarioLogado.isCoordenacao()).thenReturn(false);
    }

    @Test
    void criarGravaNaoLancadaComProfessorDaSessaoERegistraHistorico() {
        when(alunos.findComTurmaById(aluno.getId())).thenReturn(Optional.of(aluno));
        when(repositorio.saveAndFlush(any(Ocorrencia.class))).thenAnswer(chamada -> {
            Ocorrencia ocorrencia = chamada.getArgument(0);
            ReflectionTestUtils.setField(ocorrencia, "id", idGerado);
            return ocorrencia;
        });

        OcorrenciaResponse resposta = servico.criar(pedido("  6a ", "  Aluno interrompeu a aula.  ",
                "  Conversa com o aluno  ", "  Suspensão  ", LocalDate.of(2026, 10, 30)));

        ArgumentCaptor<Ocorrencia> captor = ArgumentCaptor.forClass(Ocorrencia.class);
        verify(repositorio).saveAndFlush(captor.capture());
        Ocorrencia salva = captor.getValue();
        assertThat(salva.getStatusLancamento()).isEqualTo(StatusLancamento.NAO_LANCADA);
        assertThat(salva.getDataLancamento()).isNull();
        assertThat(salva.getResponsavelLancamento()).isNull();
        assertThat(salva.getProfessor()).isSameAs(professor);
        assertThat(salva.getTurma()).isSameAs(turma);
        assertThat(salva.getDescricao()).isEqualTo("Aluno interrompeu a aula.");
        assertThat(salva.getMedidasTomadas()).isEqualTo("Conversa com o aluno");
        assertThat(salva.getMedidasSugeridas()).isEqualTo("Suspensão");
        verify(historico).registrar(idGerado, professor.getId(),
                AcaoHistorico.OCORRENCIA_CRIADA.name(), "criada pelo professor");
        assertThat(resposta.id()).isEqualTo(idGerado);
        assertThat(resposta.statusLancamento()).isEqualTo(StatusLancamento.NAO_LANCADA);
        assertThat(resposta.medidasSugeridas()).isEqualTo("Suspensão");
        assertThat(resposta.dataLancamento()).isNull();
        assertThat(resposta.responsavelLancamentoId()).isNull();
    }

    @Test
    void criarComSugestaoEmBrancoGravaNull() {
        when(alunos.findComTurmaById(aluno.getId())).thenReturn(Optional.of(aluno));
        when(repositorio.saveAndFlush(any(Ocorrencia.class))).thenAnswer(chamada -> {
            Ocorrencia ocorrencia = chamada.getArgument(0);
            ReflectionTestUtils.setField(ocorrencia, "id", idGerado);
            return ocorrencia;
        });

        servico.criar(pedido("6A", "Aluno interrompeu a aula varias vezes.",
                "Conversa com o aluno", "   ", LocalDate.of(2026, 10, 30)));

        ArgumentCaptor<Ocorrencia> captor = ArgumentCaptor.forClass(Ocorrencia.class);
        verify(repositorio).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getMedidasSugeridas()).isNull();
    }

    @Test
    void criarComAlunoInexistenteNaoRegistraHistorico() {
        when(alunos.findComTurmaById(aluno.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.criar(pedidoValido()))
                .isInstanceOf(EntityNotFoundException.class);
        verify(repositorio, never()).saveAndFlush(any());
        verifyNoInteractions(historico);
    }

    @Test
    void criarRecusaDescricaoCurtaDataFuturaTurmaDivergenteEPerfilQueNaoEProfessor() {
        assertThatThrownBy(() -> servico.criar(pedido("6A", "curta", "Conversa", LocalDate.of(2026, 10, 30))))
                .isInstanceOf(DadosOcorrenciaInvalidosException.class)
                .hasMessageContaining("10 caracteres");

        assertThatThrownBy(() -> servico.criar(pedido("6A", "Aluno interrompeu a aula.", "Conversa",
                LocalDate.of(2026, 10, 31))))
                .isInstanceOf(DadosOcorrenciaInvalidosException.class)
                .hasMessageContaining("futura");

        when(alunos.findComTurmaById(aluno.getId())).thenReturn(Optional.of(aluno));
        assertThatThrownBy(() -> servico.criar(pedido("7B", "Aluno interrompeu a aula.", "Conversa",
                LocalDate.of(2026, 10, 30))))
                .isInstanceOf(DadosOcorrenciaInvalidosException.class)
                .hasMessageContaining("turma");

        when(usuarioLogado.role()).thenReturn(Role.COORDENACAO);
        assertThatThrownBy(() -> servico.criar(pedidoValido()))
                .isInstanceOf(AccessDeniedException.class);

        verify(repositorio, never()).saveAndFlush(any());
        verifyNoInteractions(historico);
    }

    @Test
    void falhaNoHistoricoDeCriacaoSePropaga() {
        when(alunos.findComTurmaById(aluno.getId())).thenReturn(Optional.of(aluno));
        when(repositorio.saveAndFlush(any(Ocorrencia.class))).thenAnswer(chamada -> {
            Ocorrencia ocorrencia = chamada.getArgument(0);
            ReflectionTestUtils.setField(ocorrencia, "id", idGerado);
            return ocorrencia;
        });
        doThrow(new IllegalStateException("historico indisponivel")).when(historico)
                .registrar(any(), any(), any(), any());

        assertThatThrownBy(() -> servico.criar(pedidoValido()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("historico indisponivel");
    }

    @Test
    void criarLoteGravaUmaOcorrenciaPorAlunoERegistraUmHistoricoParaCada() {
        when(alunos.findAllComTurmaByIdIn(List.of(aluno.getId(), outroAluno.getId())))
                .thenReturn(List.of(aluno, outroAluno));
        when(repositorio.saveAllAndFlush(anyList())).thenAnswer(chamada -> {
            List<Ocorrencia> lista = chamada.getArgument(0);
            List<Ocorrencia> salvas = new ArrayList<>();
            for (int indice = 0; indice < lista.size(); indice++) {
                Ocorrencia ocorrencia = lista.get(indice);
                ReflectionTestUtils.setField(ocorrencia, "id",
                        indice == 0 ? idGerado : UUID.randomUUID());
                salvas.add(ocorrencia);
            }
            return salvas;
        });

        List<OcorrenciaResponse> respostas = servico.criarLote(loteValido(
                List.of(aluno.getId(), outroAluno.getId()), " 6a "));

        assertThat(respostas).hasSize(2);
        assertThat(respostas.getFirst().alunoNome()).isEqualTo("Ana Clara Souza");
        assertThat(respostas.get(1).alunoNome()).isEqualTo("Bruno Henrique Lima");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ocorrencia>> captor = ArgumentCaptor.forClass(List.class);
        verify(repositorio).saveAllAndFlush(captor.capture());
        List<Ocorrencia> salvas = captor.getValue();
        assertThat(salvas).hasSize(2);
        assertThat(salvas).allSatisfy(ocorrencia -> {
            assertThat(ocorrencia.getTurma()).isSameAs(turma);
            assertThat(ocorrencia.getProfessor()).isSameAs(professor);
            assertThat(ocorrencia.getStatusLancamento()).isEqualTo(StatusLancamento.NAO_LANCADA);
            assertThat(ocorrencia.getDescricao()).isEqualTo("Aluno interrompeu a aula varias vezes.");
        });
        verify(historico, times(2)).registrar(any(), eq(professor.getId()),
                eq(AcaoHistorico.OCORRENCIA_CRIADA.name()), eq("criada pelo professor"));
    }

    @Test
    void criarLoteComAlunosDeTurmasDiferentesNaoGravaNada() {
        Turma outraTurma = new Turma("7A");
        ReflectionTestUtils.setField(outraTurma, "id", UUID.randomUUID());
        Aluno deOutraTurma = new Aluno("Carla Souza", outraTurma);
        ReflectionTestUtils.setField(deOutraTurma, "id", UUID.randomUUID());
        when(alunos.findAllComTurmaByIdIn(List.of(aluno.getId(), deOutraTurma.getId())))
                .thenReturn(List.of(aluno, deOutraTurma));

        assertThatThrownBy(() -> servico.criarLote(loteValido(
                List.of(aluno.getId(), deOutraTurma.getId()), "6A")))
                .isInstanceOf(DadosOcorrenciaInvalidosException.class)
                .hasMessageContaining("mesma turma");

        verify(repositorio, never()).saveAllAndFlush(anyList());
        verifyNoInteractions(historico);
    }

    @Test
    void criarLoteComAlunoInexistenteOuExcluidoNaoGravaNada() {
        UUID excluido = UUID.randomUUID();
        when(alunos.findAllComTurmaByIdIn(List.of(aluno.getId(), excluido)))
                .thenReturn(List.of(aluno));

        assertThatThrownBy(() -> servico.criarLote(loteValido(
                List.of(aluno.getId(), excluido), "6A")))
                .isInstanceOf(EntityNotFoundException.class);

        verify(repositorio, never()).saveAllAndFlush(anyList());
        verifyNoInteractions(historico);
    }

    @Test
    void criarLoteExigePerfilDeProfessorESelecaoNaoVazia() {
        when(usuarioLogado.role()).thenReturn(Role.COORDENACAO);
        assertThatThrownBy(() -> servico.criarLote(loteValido(List.of(aluno.getId()), "6A")))
                .isInstanceOf(AccessDeniedException.class);

        when(usuarioLogado.role()).thenReturn(Role.PROFESSOR);
        assertThatThrownBy(() -> servico.criarLote(loteValido(List.of(), "6A")))
                .isInstanceOf(DadosOcorrenciaInvalidosException.class)
                .hasMessageContaining("aluno");

        verifyNoInteractions(historico);
    }

    @Test
    void lancarPreencheDataEResponsavelERegistraHistorico() {
        Usuario coordenacao = usuario("Ana Coordenacao", "coordenacao@escola.com", Role.COORDENACAO);
        when(usuarioLogado.isCoordenacao()).thenReturn(true);
        when(usuarioLogado.usuario()).thenReturn(coordenacao);
        Ocorrencia ocorrencia = ocorrenciaPersistida();
        when(repositorio.findById(idGerado)).thenReturn(Optional.of(ocorrencia));

        OcorrenciaResponse resposta = servico.lancar(idGerado);

        assertThat(ocorrencia.getStatusLancamento()).isEqualTo(StatusLancamento.LANCADA);
        assertThat(ocorrencia.getDataLancamento()).isEqualTo(Instant.now(relogio));
        assertThat(ocorrencia.getResponsavelLancamento()).isSameAs(coordenacao);
        assertThat(resposta.responsavelLancamentoId()).isEqualTo(coordenacao.getId());
        assertThat(resposta.responsavelLancamentoNome()).isEqualTo("Ana Coordenacao");
        verify(historico).registrar(idGerado, coordenacao.getId(),
                AcaoHistorico.OCORRENCIA_LANCADA.name(), "lancada pela coordenacao");
    }

    @Test
    void professorNaoLancaESegundaTentativaNaoRegistraDeNovo() {
        Ocorrencia ocorrencia = ocorrenciaPersistida();
        when(repositorio.findById(idGerado)).thenReturn(Optional.of(ocorrencia));

        assertThatThrownBy(() -> servico.lancar(idGerado))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(historico);

        Usuario coordenacao = usuario("Ana Coordenacao", "coordenacao@escola.com", Role.COORDENACAO);
        when(usuarioLogado.isCoordenacao()).thenReturn(true);
        when(usuarioLogado.usuario()).thenReturn(coordenacao);
        servico.lancar(idGerado);

        assertThatThrownBy(() -> servico.lancar(idGerado))
                .isInstanceOf(ConflitoOcorrenciaException.class)
                .hasMessage("Ocorrencia ja lancada");
        verify(historico).registrar(idGerado, coordenacao.getId(),
                AcaoHistorico.OCORRENCIA_LANCADA.name(), "lancada pela coordenacao");
    }

    @Test
    void autorAtualizaOcorrenciaNaoLancadaERegistraHistorico() {
        Ocorrencia ocorrencia = ocorrenciaPersistida();
        when(repositorio.findById(idGerado)).thenReturn(Optional.of(ocorrencia));

        OcorrenciaResponse resposta = servico.atualizar(idGerado, pedido("6A",
                "Descricao corrigida da aula.", "Advertencia verbal", LocalDate.of(2026, 10, 29)));

        assertThat(ocorrencia.getDescricao()).isEqualTo("Descricao corrigida da aula.");
        assertThat(ocorrencia.getTipo()).isEqualTo(TipoOcorrencia.INDISCIPLINA);
        assertThat(ocorrencia.getData()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(ocorrencia.getProfessor()).isSameAs(professor);
        assertThat(resposta.statusLancamento()).isEqualTo(StatusLancamento.NAO_LANCADA);
        verify(historico).registrar(idGerado, professor.getId(),
                AcaoHistorico.OCORRENCIA_ATUALIZADA.name(), "atualizada");
    }

    @Test
    void atualizarOcorrenciaDeAlunoMovidoEExcluidoPreservaTurmaDaEpoca() {
        Ocorrencia ocorrencia = ocorrenciaPersistida();
        Turma destino = new Turma("7A");
        ReflectionTestUtils.setField(destino, "id", UUID.randomUUID());
        aluno.moverPara(destino);
        aluno.excluir(Instant.now(relogio));
        when(repositorio.findById(idGerado)).thenReturn(Optional.of(ocorrencia));

        OcorrenciaResponse resposta = servico.atualizar(idGerado, pedido("6A",
                "Descricao corrigida depois da movimentacao.", "Advertencia verbal",
                LocalDate.of(2026, 10, 29)));

        assertThat(ocorrencia.getTurma().getNome()).isEqualTo("6A");
        assertThat(resposta.turmaNome()).isEqualTo("6A");
        assertThat(ocorrencia.getAluno().getTurma().getNome()).isEqualTo("7A");
        assertThat(ocorrencia.getAluno().getExcluidoEm()).isNotNull();
        verify(historico).registrar(idGerado, professor.getId(),
                AcaoHistorico.OCORRENCIA_ATUALIZADA.name(), "atualizada");
    }

    @Test
    void outroProfessorNaoAlteraNemExclui() {
        Usuario outro = usuario("Joao Pereira", "professor2@escola.com", Role.PROFESSOR);
        when(usuarioLogado.id()).thenReturn(outro.getId());
        when(repositorio.findById(idGerado)).thenReturn(Optional.of(ocorrenciaPersistida()));

        assertThatThrownBy(() -> servico.atualizar(idGerado, pedidoValido()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> servico.excluir(idGerado))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(historico);
        verify(repositorio, never()).delete(any(Ocorrencia.class));
    }

    @Test
    void coordenacaoAlteraOcorrenciaDeOutroProfessor() {
        when(usuarioLogado.isCoordenacao()).thenReturn(true);
        Usuario coordenacao = usuario("Ana Coordenacao", "coordenacao@escola.com", Role.COORDENACAO);
        when(usuarioLogado.id()).thenReturn(coordenacao.getId());
        when(repositorio.findById(idGerado)).thenReturn(Optional.of(ocorrenciaPersistida()));

        servico.atualizar(idGerado, pedidoValido());

        verify(historico).registrar(idGerado, coordenacao.getId(),
                AcaoHistorico.OCORRENCIA_ATUALIZADA.name(), "atualizada");
    }

    @Test
    void lancadaNaoPodeSerAlteradaNemExcluida() {
        Ocorrencia ocorrencia = ocorrenciaPersistida();
        ocorrencia.lancar(usuario("Ana Coordenacao", "coordenacao@escola.com", Role.COORDENACAO),
                Instant.now(relogio));
        when(repositorio.findById(idGerado)).thenReturn(Optional.of(ocorrencia));

        assertThatThrownBy(() -> servico.atualizar(idGerado, pedidoValido()))
                .isInstanceOf(ConflitoOcorrenciaException.class);
        assertThatThrownBy(() -> servico.excluir(idGerado))
                .isInstanceOf(ConflitoOcorrenciaException.class);
        verifyNoInteractions(historico);
    }

    @Test
    void autorCancelaOcorrenciaNaoLancadaERegistraHistorico() {
        Ocorrencia ocorrencia = ocorrenciaPersistida();
        when(repositorio.findById(idGerado)).thenReturn(Optional.of(ocorrencia));

        servico.excluir(idGerado);

        assertThat(ocorrencia.getCanceladaEm()).isEqualTo(Instant.now(relogio));
        assertThat(ocorrencia.getStatusLancamento()).isEqualTo(StatusLancamento.NAO_LANCADA);
        verify(historico).registrar(idGerado, professor.getId(),
                AcaoHistorico.OCORRENCIA_EXCLUIDA.name(), "cancelada");
        verify(repositorio, never()).delete(any(Ocorrencia.class));
    }

    @Test
    void ocorrenciaCanceladaSomeDaBuscaENaoPodeSerAlterada() {
        Ocorrencia ocorrencia = ocorrenciaPersistida();
        ocorrencia.cancelar(Instant.now(relogio));
        when(repositorio.findById(idGerado)).thenReturn(Optional.of(ocorrencia));

        assertThatThrownBy(() -> servico.buscar(idGerado))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> servico.atualizar(idGerado, pedidoValido()))
                .isInstanceOf(ConflitoOcorrenciaException.class)
                .hasMessageContaining("cancelada");
        assertThatThrownBy(() -> servico.excluir(idGerado))
                .isInstanceOf(ConflitoOcorrenciaException.class)
                .hasMessageContaining("cancelada");
        verifyNoInteractions(historico);
    }

    @Test
    void listarRecusaPeriodoInvertidoEDelegaFiltroValido() {
        assertThatThrownBy(() -> servico.listar(new FiltroOcorrencia(
                null, null, null, null, LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 1))))
                .isInstanceOf(DadosOcorrenciaInvalidosException.class);
        verifyNoInteractions(repositorio);

        Ocorrencia ocorrencia = ocorrenciaPersistida();
        when(repositorio.findAll(qualquerConsulta(), any(Sort.class))).thenReturn(List.of(ocorrencia));
        FiltroOcorrencia filtro = new FiltroOcorrencia(turma.getId(), aluno.getId(),
                TipoOcorrencia.CELULAR, StatusLancamento.NAO_LANCADA,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30));

        List<OcorrenciaResponse> lista = servico.listar(filtro);

        assertThat(lista).hasSize(1);
        assertThat(lista.getFirst().alunoNome()).isEqualTo("Ana Clara Souza");
        assertThat(lista.getFirst().turmaNome()).isEqualTo("6A");
        verify(repositorio).findAll(qualquerConsulta(), any(Sort.class));
    }

    @Test
    void buscarInexistenteLancaNaoEncontrado() {
        when(repositorio.findById(idGerado)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.buscar(idGerado))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void operacoesQueGravamSaoTransacionais() throws Exception {
        assertTransacional("criar", OcorrenciaRequest.class);
        assertTransacional("criarLote", OcorrenciaLoteRequest.class);
        assertTransacional("atualizar", UUID.class, OcorrenciaRequest.class);
        assertTransacional("excluir", UUID.class);
        assertTransacional("lancar", UUID.class);
    }

    private void assertTransacional(String metodo, Class<?>... parametros) throws Exception {
        Transactional transacao = OcorrenciaService.class.getMethod(metodo, parametros)
                .getAnnotation(Transactional.class);
        assertThat(transacao).isNotNull();
        assertThat(transacao.readOnly()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static Specification<Ocorrencia> qualquerConsulta() {
        return any(Specification.class);
    }

    private OcorrenciaRequest pedidoValido() {
        return pedido("6A", "Aluno interrompeu a aula varias vezes.", "Conversa com o aluno",
                "Suspensão", LocalDate.of(2026, 10, 30));
    }

    private OcorrenciaLoteRequest loteValido(List<UUID> alunoIds, String turmaNome) {
        return new OcorrenciaLoteRequest(
                alunoIds,
                turmaNome,
                TipoOcorrencia.INDISCIPLINA,
                "Aluno interrompeu a aula varias vezes.",
                "Conversa com o aluno",
                "Suspensão",
                LocalDate.of(2026, 10, 30));
    }

    private OcorrenciaRequest pedido(String turmaNome, String descricao, String medidas, LocalDate data) {
        return pedido(turmaNome, descricao, medidas, null, data);
    }

    private OcorrenciaRequest pedido(String turmaNome, String descricao, String medidas,
                                     String medidasSugeridas, LocalDate data) {
        return new OcorrenciaRequest(
                new OcorrenciaRequest.AlunoReferencia(aluno.getId(), aluno.getNome()),
                turmaNome,
                TipoOcorrencia.INDISCIPLINA,
                descricao,
                medidas,
                medidasSugeridas,
                data);
    }

    private Ocorrencia ocorrenciaPersistida() {
        Ocorrencia ocorrencia = new Ocorrencia(aluno, professor, TipoOcorrencia.INDISCIPLINA,
                "Aluno interrompeu a aula varias vezes.", "Conversa com o aluno",
                "Suspensão", LocalDate.of(2026, 10, 30));
        ReflectionTestUtils.setField(ocorrencia, "id", idGerado);
        return ocorrencia;
    }

    private static Usuario usuario(String nome, String email, Role role) {
        Usuario usuario = new Usuario(nome, email, "hash", role);
        ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
        return usuario;
    }
}
