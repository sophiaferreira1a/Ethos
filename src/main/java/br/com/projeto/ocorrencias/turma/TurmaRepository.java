package br.com.projeto.ocorrencias.turma;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurmaRepository extends JpaRepository<Turma, UUID> {

    List<Turma> findAllByOrderByNomeAsc();

    Optional<Turma> findByNomeIgnoreCase(String nome);
}
