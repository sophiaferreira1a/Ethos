package br.com.projeto.ocorrencias.historico;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Evento de auditoria de uma ocorrencia ({@code historicos}).
 *
 * <p>{@code ocorrenciaId} e {@code usuarioId} sao UUIDs simples, sem {@code @ManyToOne}:
 * este modulo nao depende das entidades do modulo de ocorrencias. A integridade
 * referencial e garantida pelas chaves estrangeiras do banco.</p>
 *
 * <p>Registro imutavel: historico nunca e alterado depois de gravado.</p>
 */
@Entity
@Immutable
@Table(name = "historicos")
public class Historico {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ocorrencia_id", nullable = false, updatable = false)
    private UUID ocorrenciaId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 40, updatable = false)
    private String acao;

    @Column(columnDefinition = "text", updatable = false)
    private String detalhes;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    protected Historico() {
        // exigido pelo JPA
    }

    public Historico(UUID ocorrenciaId, UUID usuarioId, String acao, String detalhes) {
        this.ocorrenciaId = ocorrenciaId;
        this.usuarioId = usuarioId;
        this.acao = acao;
        this.detalhes = detalhes;
    }

    @PrePersist
    void aoPersistir() {
        if (dataHora == null) {
            dataHora = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOcorrenciaId() {
        return ocorrenciaId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getAcao() {
        return acao;
    }

    public String getDetalhes() {
        return detalhes;
    }

    public Instant getDataHora() {
        return dataHora;
    }
}
