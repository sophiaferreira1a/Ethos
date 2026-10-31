package br.com.projeto.ocorrencias.ocorrencia;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.projeto.ocorrencias.aluno.Aluno;
import br.com.projeto.ocorrencias.aluno.AlunoRepository;
import br.com.projeto.ocorrencias.historico.AcaoHistorico;
import br.com.projeto.ocorrencias.historico.HistoricoService;
import br.com.projeto.ocorrencias.ocorrencia.dto.OcorrenciaRequest;
import br.com.projeto.ocorrencias.ocorrencia.dto.OcorrenciaResponse;
import br.com.projeto.ocorrencias.usuario.Role;
import br.com.projeto.ocorrencias.usuario.Usuario;
import br.com.projeto.ocorrencias.usuario.UsuarioLogado;
import jakarta.persistence.EntityNotFoundException;

/**
 * Regras de ocorrencia. Criar, lancar, atualizar e excluir registram historico
 * na mesma transacao: se {@link HistoricoService#registrar} falhar, a operacao e desfeita.
 */
@Service
@Transactional(readOnly = true)
public class OcorrenciaService {

    private static final ZoneId FUSO_BRASIL = ZoneId.of("America/Sao_Paulo");
    private static final Sort ORDEM = Sort.by(Sort.Order.desc("data"), Sort.Order.desc("criadoEm"));
    private static final int DESCRICAO_MINIMA = 10;

    private final OcorrenciaRepository repositorio;
    private final AlunoRepository alunos;
    private final UsuarioLogado usuarioLogado;
    private final HistoricoService historico;
    private final Clock relogio;

    @Autowired
    public OcorrenciaService(OcorrenciaRepository repositorio, AlunoRepository alunos,
                             UsuarioLogado usuarioLogado, HistoricoService historico) {
        this(repositorio, alunos, usuarioLogado, historico, Clock.system(FUSO_BRASIL));
    }

    OcorrenciaService(OcorrenciaRepository repositorio, AlunoRepository alunos,
                      UsuarioLogado usuarioLogado, HistoricoService historico, Clock relogio) {
        this.repositorio = repositorio;
        this.alunos = alunos;
        this.usuarioLogado = usuarioLogado;
        this.historico = historico;
        this.relogio = relogio;
    }

    @Transactional
    public OcorrenciaResponse criar(OcorrenciaRequest dados) {
        if (usuarioLogado.role() != Role.PROFESSOR) {
            throw new AccessDeniedException("Apenas o professor registra ocorrencia");
        }
        Conteudo conteudo = conteudo(dados);
        conferirData(conteudo.data());
        Aluno aluno = buscarAluno(dados.aluno().id());
        conferirTurma(dados.turma(), aluno);

        Usuario professor = usuarioLogado.usuario();
        Ocorrencia ocorrencia = new Ocorrencia(aluno, professor, conteudo.tipo(),
                conteudo.descricao(), conteudo.medidas(), conteudo.data());
        Ocorrencia salva = repositorio.saveAndFlush(ocorrencia);
        historico.registrar(salva.getId(), professor.getId(),
                AcaoHistorico.OCORRENCIA_CRIADA.name(), "criada pelo professor");
        return OcorrenciaResponse.de(salva);
    }

    public OcorrenciaResponse buscar(UUID id) {
        return OcorrenciaResponse.de(carregar(id));
    }

    public List<OcorrenciaResponse> listar(FiltroOcorrencia filtro) {
        FiltroOcorrencia criterios = filtro == null
                ? new FiltroOcorrencia(null, null, null, null, null, null)
                : filtro;
        validarPeriodo(criterios);
        Specification<Ocorrencia> consulta = OcorrenciaConsulta.com(criterios);
        return repositorio.findAll(consulta, ORDEM).stream()
                .map(OcorrenciaResponse::de)
                .toList();
    }

    @Transactional
    public OcorrenciaResponse atualizar(UUID id, OcorrenciaRequest dados) {
        Ocorrencia ocorrencia = carregar(id);
        exigirPodeAlterar(ocorrencia);
        Conteudo conteudo = conteudo(dados);
        Aluno aluno = buscarAluno(dados.aluno().id());
        conferirTurma(dados.turma(), aluno);
        conferirData(conteudo.data());
        ocorrencia.atualizar(aluno, conteudo.tipo(), conteudo.descricao(), conteudo.medidas(), conteudo.data());
        historico.registrar(ocorrencia.getId(), usuarioLogado.id(),
                AcaoHistorico.OCORRENCIA_ATUALIZADA.name(), "atualizada");
        return OcorrenciaResponse.de(ocorrencia);
    }

    @Transactional
    public void excluir(UUID id) {
        Ocorrencia ocorrencia = carregar(id);
        exigirPodeAlterar(ocorrencia);
        historico.registrar(ocorrencia.getId(), usuarioLogado.id(),
                AcaoHistorico.OCORRENCIA_EXCLUIDA.name(), "excluida");
        repositorio.delete(ocorrencia);
        try {
            repositorio.flush();
        } catch (DataIntegrityViolationException excecao) {
            throw new ConflitoOcorrenciaException(
                    "Nao foi possivel excluir a ocorrencia porque ela possui historico");
        }
    }

    @Transactional
    public OcorrenciaResponse lancar(UUID id) {
        if (!usuarioLogado.isCoordenacao()) {
            throw new AccessDeniedException("Apenas a coordenacao pode lancar a ocorrencia");
        }
        Ocorrencia ocorrencia = carregar(id);
        Usuario responsavel = usuarioLogado.usuario();
        ocorrencia.lancar(responsavel, Instant.now(relogio));
        historico.registrar(ocorrencia.getId(), responsavel.getId(),
                AcaoHistorico.OCORRENCIA_LANCADA.name(), "lancada pela coordenacao");
        return OcorrenciaResponse.de(ocorrencia);
    }

    private Ocorrencia carregar(UUID id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Ocorrencia nao encontrada: " + id));
    }

    private Aluno buscarAluno(UUID id) {
        return alunos.findComTurmaById(id)
                .orElseThrow(() -> new EntityNotFoundException("Aluno nao encontrado: " + id));
    }

    private void exigirPodeAlterar(Ocorrencia ocorrencia) {
        ocorrencia.exigirAindaNaoLancada();
        if (usuarioLogado.isCoordenacao()) {
            return;
        }
        UUID autor = ocorrencia.getProfessor().getId();
        if (autor == null || !autor.equals(usuarioLogado.id())) {
            throw new AccessDeniedException(
                    "Somente o professor autor ou a coordenacao podem alterar esta ocorrencia");
        }
    }

    private Conteudo conteudo(OcorrenciaRequest dados) {
        if (dados == null || dados.aluno() == null || dados.aluno().id() == null) {
            throw new DadosOcorrenciaInvalidosException("Informe o aluno");
        }
        if (dados.tipo() == null) {
            throw new DadosOcorrenciaInvalidosException("Informe o tipo da ocorrencia");
        }
        String descricao = dados.descricao() == null ? "" : dados.descricao().trim();
        if (descricao.length() < DESCRICAO_MINIMA) {
            throw new DadosOcorrenciaInvalidosException("Descricao deve ter ao menos 10 caracteres");
        }
        if (dados.medidasTomadas() == null || dados.medidasTomadas().isBlank()) {
            throw new DadosOcorrenciaInvalidosException("Informe as medidas tomadas");
        }
        if (dados.data() == null) {
            throw new DadosOcorrenciaInvalidosException("Informe a data da ocorrencia");
        }
        return new Conteudo(dados.tipo(), descricao, dados.medidasTomadas().trim(), dados.data());
    }

    private void conferirTurma(String turmaInformada, Aluno aluno) {
        if (turmaInformada == null || turmaInformada.isBlank()) {
            return;
        }
        String esperada = aluno.getTurma().getNome();
        if (!turmaInformada.trim().equalsIgnoreCase(esperada)) {
            throw new DadosOcorrenciaInvalidosException("Turma informada nao corresponde a turma do aluno");
        }
    }

    private void conferirData(LocalDate data) {
        if (data.isAfter(LocalDate.now(relogio))) {
            throw new DadosOcorrenciaInvalidosException("Data da ocorrencia nao pode ser futura");
        }
    }

    private void validarPeriodo(FiltroOcorrencia filtro) {
        if (filtro.dataInicio() != null && filtro.dataFim() != null
                && filtro.dataInicio().isAfter(filtro.dataFim())) {
            throw new DadosOcorrenciaInvalidosException(
                    "Periodo invalido: data inicial posterior a data final");
        }
    }

    private record Conteudo(TipoOcorrencia tipo, String descricao, String medidas, LocalDate data) {
    }
}
