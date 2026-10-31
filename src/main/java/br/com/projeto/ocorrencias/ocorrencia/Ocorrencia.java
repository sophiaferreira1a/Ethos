package br.com.projeto.ocorrencias.ocorrencia;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import br.com.projeto.ocorrencias.aluno.Aluno;
import br.com.projeto.ocorrencias.turma.Turma;
import br.com.projeto.ocorrencias.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Ocorrencia escolar ({@code ocorrencias}).
 *
 * <p>Nasce {@link StatusLancamento#NAO_LANCADA}, sem data nem responsavel de lancamento.
 * Esses dois campos so sao preenchidos juntos em {@link #lancar(Usuario, Instant)},
 * o que espelha o check {@code ck_lancamento} do banco.</p>
 */
@Entity
@Table(name = "ocorrencias")
public class Ocorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turma_id", nullable = false)
    private Turma turma;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professor_id", nullable = false, updatable = false)
    private Usuario professor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoOcorrencia tipo;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false, columnDefinition = "text")
    private String descricao;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "medidas_tomadas", columnDefinition = "text")
    private String medidasTomadas;

    @Column(nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_lancamento", nullable = false, length = 20)
    private StatusLancamento statusLancamento = StatusLancamento.NAO_LANCADA;

    @Column(name = "data_lancamento")
    private Instant dataLancamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_lancamento_id")
    private Usuario responsavelLancamento;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Ocorrencia() {
        // exigido pelo JPA
    }

    public Ocorrencia(Aluno aluno, Usuario professor, TipoOcorrencia tipo,
                      String descricao, String medidasTomadas, LocalDate data) {
        this.aluno = aluno;
        this.turma = aluno.getTurma();
        this.professor = professor;
        this.tipo = tipo;
        this.descricao = descricao;
        this.medidasTomadas = medidasTomadas;
        this.data = data;
        this.statusLancamento = StatusLancamento.NAO_LANCADA;
    }

    @PrePersist
    void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
        if (statusLancamento == null) {
            statusLancamento = StatusLancamento.NAO_LANCADA;
        }
    }

    public void atualizar(Aluno aluno, TipoOcorrencia tipo, String descricao,
                          String medidasTomadas, LocalDate data) {
        exigirAindaNaoLancada();
        this.aluno = aluno;
        this.turma = aluno.getTurma();
        this.tipo = tipo;
        this.descricao = descricao;
        this.medidasTomadas = medidasTomadas;
        this.data = data;
    }

    /**
     * Marca a ocorrencia como lancada. Data e responsavel sao obrigatorios e gravados juntos.
     */
    public void lancar(Usuario responsavel, Instant quando) {
        if (statusLancamento == StatusLancamento.LANCADA) {
            throw new ConflitoOcorrenciaException("Ocorrencia ja lancada");
        }
        if (responsavel == null || quando == null) {
            throw new IllegalStateException("Lancamento exige data e responsavel preenchidos");
        }
        this.statusLancamento = StatusLancamento.LANCADA;
        this.dataLancamento = quando;
        this.responsavelLancamento = responsavel;
    }

    public void exigirAindaNaoLancada() {
        if (statusLancamento == StatusLancamento.LANCADA) {
            throw new ConflitoOcorrenciaException("Ocorrencia lancada nao pode ser alterada nem excluida");
        }
    }

    public UUID getId() {
        return id;
    }

    public Aluno getAluno() {
        return aluno;
    }

    public Turma getTurma() {
        return turma;
    }

    public Usuario getProfessor() {
        return professor;
    }

    public TipoOcorrencia getTipo() {
        return tipo;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getMedidasTomadas() {
        return medidasTomadas;
    }

    public LocalDate getData() {
        return data;
    }

    public StatusLancamento getStatusLancamento() {
        return statusLancamento;
    }

    public Instant getDataLancamento() {
        return dataLancamento;
    }

    public Usuario getResponsavelLancamento() {
        return responsavelLancamento;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Ocorrencia ocorrencia)) {
            return false;
        }
        return id != null && Objects.equals(id, ocorrencia.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
