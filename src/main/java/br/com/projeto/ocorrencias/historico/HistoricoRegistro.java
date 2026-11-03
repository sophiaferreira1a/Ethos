package br.com.projeto.ocorrencias.historico;

import java.time.Instant;
import java.util.UUID;

/**
 * Linha de consulta do historico ja com o nome de quem executou a acao.
 * Projecao interna do {@link HistoricoRepository}; a API devolve {@code HistoricoResponse}.
 */
public record HistoricoRegistro(
        UUID id,
        String acao,
        String detalhes,
        Instant dataHora,
        UUID usuarioId,
        String usuarioNome) {
}
