package br.com.projeto.ocorrencias.historico;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementacao real do contrato de registro de historico.
 *
 * <p>{@link Propagation#MANDATORY}: o registro so acontece dentro da transacao da acao
 * (aberta pelo service de ocorrencias). Se alguem chamar sem transacao, falha alto
 * em vez de gravar o historico "solto" e perder a atomicidade descrita na secao 6
 * da documentacao.</p>
 */
@Service
public class HistoricoServiceImpl implements HistoricoService {

    private final HistoricoRepository repository;

    public HistoricoServiceImpl(HistoricoRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(UUID ocorrenciaId, UUID usuarioId, String acao, String detalhes) {
        if (ocorrenciaId == null) {
            throw new IllegalArgumentException("ocorrenciaId e obrigatorio");
        }
        if (usuarioId == null) {
            throw new IllegalArgumentException("usuarioId e obrigatorio");
        }
        String acaoValida = validarAcao(acao);
        String detalhesLimpos = (detalhes == null || detalhes.isBlank()) ? null : detalhes.strip();

        // saveAndFlush: se a FK falhar (ocorrencia/usuario inexistente), o erro estoura aqui,
        // dentro da transacao do chamador, e desfaz a acao inteira.
        repository.saveAndFlush(new Historico(ocorrenciaId, usuarioId, acaoValida, detalhesLimpos));
    }

    private static String validarAcao(String acao) {
        if (acao == null || acao.isBlank()) {
            throw new IllegalArgumentException("acao e obrigatoria");
        }
        try {
            return AcaoHistorico.valueOf(acao.strip()).name();
        } catch (IllegalArgumentException erro) {
            throw new IllegalArgumentException("acao desconhecida: " + acao, erro);
        }
    }
}
