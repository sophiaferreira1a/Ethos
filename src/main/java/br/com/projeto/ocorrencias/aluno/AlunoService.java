package br.com.projeto.ocorrencias.aluno;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

/**
 * Consulta de alunos. A busca por nome e turma alimenta o registro de ocorrencias.
 */
@Service
@Transactional(readOnly = true)
public class AlunoService {

    private final AlunoRepository repositorio;

    public AlunoService(AlunoRepository repositorio) {
        this.repositorio = repositorio;
    }

    public Aluno buscarPorId(UUID id) {
        return repositorio.findComTurmaById(id)
                .orElseThrow(() -> new EntityNotFoundException("Aluno nao encontrado: " + id));
    }

    public List<Aluno> listar(String busca, String turma) {
        return repositorio.filtrar(texto(busca), texto(turma));
    }

    private static String texto(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
