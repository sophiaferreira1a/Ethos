package br.com.projeto.ocorrencias.aluno;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.projeto.ocorrencias.aluno.dto.AlunoRequest;
import br.com.projeto.ocorrencias.aluno.dto.MoverAlunosRequest;
import br.com.projeto.ocorrencias.turma.Turma;
import br.com.projeto.ocorrencias.turma.TurmaRepository;
import jakarta.persistence.EntityNotFoundException;

/**
 * Consulta e cadastro de alunos. A busca por nome e turma alimenta o registro de ocorrencias.
 */
@Service
@Transactional(readOnly = true)
public class AlunoService {

    private static final ZoneId FUSO_BRASIL = ZoneId.of("America/Sao_Paulo");

    private final AlunoRepository repositorio;
    private final TurmaRepository turmas;
    private final Clock relogio;

    @Autowired
    public AlunoService(AlunoRepository repositorio, TurmaRepository turmas) {
        this(repositorio, turmas, Clock.system(FUSO_BRASIL));
    }

    AlunoService(AlunoRepository repositorio, TurmaRepository turmas, Clock relogio) {
        this.repositorio = repositorio;
        this.turmas = turmas;
        this.relogio = relogio;
    }

    public Aluno buscarPorId(UUID id) {
        return repositorio.findComTurmaById(id)
                .orElseThrow(() -> new EntityNotFoundException("Aluno nao encontrado: " + id));
    }

    public List<Aluno> listar(String busca, String turma) {
        return repositorio.filtrar(texto(busca), texto(turma));
    }

    @Transactional
    public Aluno criar(AlunoRequest dados) {
        Turma turma = buscarTurma(dados.turmaId());
        return repositorio.save(new Aluno(nome(dados.nome()), turma));
    }

    @Transactional
    public Aluno atualizar(UUID id, AlunoRequest dados) {
        Aluno aluno = buscarPorId(id);
        aluno.atualizar(nome(dados.nome()), buscarTurma(dados.turmaId()));
        return aluno;
    }

    /**
     * Exclusao logica: o aluno sai das listas, mas ocorrencias e historicos continuam intactos.
     */
    @Transactional
    public void excluir(UUID id) {
        buscarPorId(id).excluir(Instant.now(relogio));
    }

    /**
     * Move varios alunos para a mesma turma em uma unica transacao.
     */
    @Transactional
    public List<Aluno> moverEmLote(MoverAlunosRequest dados) {
        Turma destino = buscarTurma(dados.turmaId());
        List<UUID> ids = new LinkedHashSet<>(dados.alunoIds()).stream().toList();
        List<Aluno> encontrados = repositorio.findAllComTurmaByIdIn(ids);
        if (encontrados.size() != ids.size()) {
            throw new EntityNotFoundException("Aluno nao encontrado ou excluido");
        }
        encontrados.forEach(aluno -> aluno.moverPara(destino));
        return encontrados;
    }

    private Turma buscarTurma(UUID id) {
        return turmas.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Turma nao encontrada: " + id));
    }

    private static String nome(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static String texto(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
