package br.com.projeto.ocorrencias.turma;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.projeto.ocorrencias.aluno.AlunoRepository;
import br.com.projeto.ocorrencias.config.ConflitoCadastroException;
import br.com.projeto.ocorrencias.ocorrencia.OcorrenciaRepository;
import br.com.projeto.ocorrencias.turma.dto.TurmaRequest;
import jakarta.persistence.EntityNotFoundException;

/**
 * Consulta e cadastro de turmas.
 */
@Service
@Transactional(readOnly = true)
public class TurmaService {

    private final TurmaRepository repositorio;
    private final AlunoRepository alunos;
    private final OcorrenciaRepository ocorrencias;

    public TurmaService(TurmaRepository repositorio, AlunoRepository alunos,
                        OcorrenciaRepository ocorrencias) {
        this.repositorio = repositorio;
        this.alunos = alunos;
        this.ocorrencias = ocorrencias;
    }

    public List<Turma> listar() {
        return repositorio.findAllByOrderByNomeAsc();
    }

    public Turma buscarPorId(UUID id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Turma nao encontrada: " + id));
    }

    public Turma buscarPorNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new EntityNotFoundException("Turma nao encontrada");
        }
        return repositorio.findByNomeIgnoreCase(nome.trim())
                .orElseThrow(() -> new EntityNotFoundException("Turma nao encontrada: " + nome.trim()));
    }

    @Transactional
    public Turma criar(TurmaRequest dados) {
        String nome = nome(dados.nome());
        if (repositorio.existsByNomeIgnoreCase(nome)) {
            throw new ConflitoCadastroException("Ja existe uma turma com esse nome");
        }
        return repositorio.save(new Turma(nome));
    }

    @Transactional
    public Turma atualizar(UUID id, TurmaRequest dados) {
        Turma turma = buscarPorId(id);
        String nome = nome(dados.nome());
        if (repositorio.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new ConflitoCadastroException("Ja existe uma turma com esse nome");
        }
        turma.renomear(nome);
        return turma;
    }

    @Transactional
    public void excluir(UUID id) {
        buscarPorId(id);
        if (alunos.existsByTurmaId(id) || ocorrencias.existsByTurmaId(id)) {
            throw new ConflitoCadastroException(
                    "Turma possui alunos ou ocorrencias vinculadas e nao pode ser excluida");
        }
        repositorio.deleteById(id);
    }

    private static String nome(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
