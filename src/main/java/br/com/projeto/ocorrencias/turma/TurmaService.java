package br.com.projeto.ocorrencias.turma;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

/**
 * Consulta de turmas. O cadastro em si vem do banco (seed/migration); este modulo nao cria turma pela API.
 */
@Service
@Transactional(readOnly = true)
public class TurmaService {

    private final TurmaRepository repositorio;

    public TurmaService(TurmaRepository repositorio) {
        this.repositorio = repositorio;
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
}
