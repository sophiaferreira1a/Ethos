package br.com.projeto.ocorrencias.ocorrencia;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Criterios da listagem de ocorrencias. Qualquer campo nulo fica de fora do filtro.
 * Periodo usa a data do fato ({@code ocorrencias.data}), inclusive nas duas pontas.
 */
public record FiltroOcorrencia(
        UUID turmaId,
        UUID alunoId,
        TipoOcorrencia tipo,
        StatusLancamento status,
        LocalDate dataInicio,
        LocalDate dataFim) {
}
