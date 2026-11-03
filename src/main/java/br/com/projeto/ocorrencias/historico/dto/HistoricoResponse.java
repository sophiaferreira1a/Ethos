package br.com.projeto.ocorrencias.historico.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.projeto.ocorrencias.historico.HistoricoRegistro;

/**
 * Item da linha do tempo de uma ocorrencia. O usuario vai aninhado, como no
 * restante da API.
 */
public record HistoricoResponse(
        UUID id,
        String acao,
        String detalhes,
        Instant dataHora,
        UsuarioResumo usuario) {

    public record UsuarioResumo(UUID id, String nome) {
    }

    public static HistoricoResponse de(HistoricoRegistro registro) {
        return new HistoricoResponse(
                registro.id(),
                registro.acao(),
                registro.detalhes(),
                registro.dataHora(),
                new UsuarioResumo(registro.usuarioId(), registro.usuarioNome()));
    }
}
