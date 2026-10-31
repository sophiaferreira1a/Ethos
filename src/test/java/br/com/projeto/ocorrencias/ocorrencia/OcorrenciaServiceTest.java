package br.com.projeto.ocorrencias.ocorrencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import br.com.projeto.ocorrencias.aluno.Aluno;
import br.com.projeto.ocorrencias.aluno.AlunoRepository;
import br.com.projeto.ocorrencias.historico.AcaoHistorico;
import br.com.projeto.ocorrencias.historico.HistoricoService;
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
                "  Conversa com o aluno  ", LocalDate.of(2026, 10, 30)));

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
        verify(historico).registrar(idGerado, professor.getId(),
                AcaoHistorico.OCORRENCIA_CRIADA.name(), "criada pelo professor");
        assertThat(resposta.id()).isEqualTo(idGerado);
        assertThat(resposta.statusLancamento()).isEqualTo(StatusLancamento.NAO_LANCADA);
        assertThat(resposta.dataLancamento()).isNull();
        assertThat(resposta.responsavelLancamentoId()).isNull();
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
        when(alunos.findComTurmaById(aluno.getId())).thenReturn(Optional.of(aluno));
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
        when(alunos.findComTurmaById(aluno.getId())).thenReturn(Optional.of(aluno));
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
    void autorExcluiOcorrenciaNaoLancadaERegistraHistorico() {
        when(repositorio.findById(idGerado)).thenReturn(Optional.of(ocorrenciaPersistida()));

        servico.excluir(idGerado);

        verify(historico).registrar(idGerado, professor.getId(),
                AcaoHistorico.OCORRENCIA_EXCLUIDA.name(), "excluida");
        verify(repositorio).delete(any(Ocorrencia.class));
        verify(repositorio).flush();
    }

    @Test
    void exclusaoBloqueadaPeloHistoricoViraConflito() {
        when(repositorio.findById(idGerado)).thenReturn(Optional.of(ocorrenciaPersistida()));
        doThrow(new DataIntegrityViolationException("fk historicos")).when(repositorio).flush();

        assertThatThrownBy(() -> servico.excluir(idGerado))
                .isInstanceOf(ConflitoOcorrenciaException.class)
                .hasMessageContaining("historico");
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
                LocalDate.of(2026, 10, 30));
    }

    private OcorrenciaRequest pedido(String turmaNome, String descricao, String medidas, LocalDate data) {
        return new OcorrenciaRequest(
                new OcorrenciaRequest.AlunoReferencia(aluno.getId(), aluno.getNome()),
                turmaNome,
                TipoOcorrencia.INDISCIPLINA,
                descricao,
                medidas,
                data);
    }

    private Ocorrencia ocorrenciaPersistida() {
        Ocorrencia ocorrencia = new Ocorrencia(aluno, professor, TipoOcorrencia.INDISCIPLINA,
                "Aluno interrompeu a aula varias vezes.", "Conversa com o aluno", LocalDate.of(2026, 10, 30));
        ReflectionTestUtils.setField(ocorrencia, "id", idGerado);
        return ocorrencia;
    }

    private static Usuario usuario(String nome, String email, Role role) {
        Usuario usuario = new Usuario(nome, email, "hash", role);
        ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
        return usuario;
    }
}
