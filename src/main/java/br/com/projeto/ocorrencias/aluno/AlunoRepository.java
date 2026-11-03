package br.com.projeto.ocorrencias.aluno;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlunoRepository extends JpaRepository<Aluno, UUID> {

    @EntityGraph(attributePaths = "turma")
    @Query("select a from Aluno a where a.id = :id")
    Optional<Aluno> findComTurmaById(@Param("id") UUID id);

    @Query("""
            select a from Aluno a join fetch a.turma t
            where (:busca is null or :busca = '' or lower(a.nome) like lower(concat('%', :busca, '%')))
              and (:turma is null or :turma = '' or lower(t.nome) = lower(:turma))
            order by a.nome asc
            """)
    List<Aluno> filtrar(@Param("busca") String busca, @Param("turma") String turma);
}
