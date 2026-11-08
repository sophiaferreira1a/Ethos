package br.com.projeto.ocorrencias.aluno;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.projeto.ocorrencias.turma.Turma;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Aluno ({@code alunos}). Sempre pertence a uma turma.
 */
@Entity
@Table(name = "alunos")
public class Aluno {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nome;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turma_id", nullable = false)
    private Turma turma;

    @Column(name = "excluido_em")
    private Instant excluidoEm;

    protected Aluno() {
        // exigido pelo JPA
    }

    public Aluno(String nome, Turma turma) {
        this.nome = nome;
        this.turma = turma;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public Turma getTurma() {
        return turma;
    }

    public Instant getExcluidoEm() {
        return excluidoEm;
    }

    public void atualizar(String nome, Turma turma) {
        this.nome = nome;
        this.turma = turma;
    }

    public void moverPara(Turma turma) {
        this.turma = turma;
    }

    /**
     * Exclusao logica: preserva a linha porque ocorrencias e historicos apontam para ela.
     */
    public void excluir(Instant quando) {
        if (quando == null) {
            throw new IllegalStateException("Exclusao exige data");
        }
        if (excluidoEm == null) {
            excluidoEm = quando;
        }
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Aluno aluno)) {
            return false;
        }
        return id != null && Objects.equals(id, aluno.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
