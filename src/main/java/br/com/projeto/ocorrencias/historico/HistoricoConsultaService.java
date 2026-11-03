package br.com.projeto.ocorrencias.historico;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.projeto.ocorrencias.historico.dto.HistoricoResponse;
import jakarta.persistence.EntityNotFoundException;

/**
 * Consulta do historico (leitura). Separado de {@link HistoricoService}, que e o contrato
 * de escrita compartilhado com o modulo de ocorrencias.
 */
@Service
public class HistoricoConsultaService {

    private final HistoricoRepository repository;

    public HistoricoConsultaService(HistoricoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<HistoricoResponse> listarPorOcorrencia(UUID ocorrenciaId) {
        if (!repository.ocorrenciaExiste(ocorrenciaId)) {
            throw new EntityNotFoundException("Ocorrencia nao encontrada");
        }
        return repository.buscarPorOcorrencia(ocorrenciaId).stream()
                .map(HistoricoResponse::de)
                .toList();
    }
}
