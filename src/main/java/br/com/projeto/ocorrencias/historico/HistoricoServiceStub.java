package br.com.projeto.ocorrencias.historico;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementacao temporaria: apenas registra a chamada no log.
 *
 * <p><b>Bernardo:</b> ao criar a implementacao real ({@code HistoricoServiceImpl}),
 * APAGUE esta classe. Duas implementacoes de {@link HistoricoService} no contexto
 * quebram a subida da aplicacao.</p>
 *
 * <p>O Mateus ja pode chamar {@link HistoricoService} desde o primeiro dia.</p>
 */
@Service
public class HistoricoServiceStub implements HistoricoService {

    private static final Logger log = LoggerFactory.getLogger(HistoricoServiceStub.class);

    @Override
    public void registrar(UUID ocorrenciaId, UUID usuarioId, String acao, String detalhes) {
        log.info("[historico-stub] ocorrencia={} usuario={} acao={} detalhes={}",
                ocorrenciaId, usuarioId, acao, detalhes);
    }
}
