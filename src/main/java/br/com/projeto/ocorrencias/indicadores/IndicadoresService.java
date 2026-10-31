package br.com.projeto.ocorrencias.indicadores;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.projeto.ocorrencias.indicadores.dto.IndicadoresResponse;
import br.com.projeto.ocorrencias.indicadores.dto.IndicadoresResponse.FiltroAplicado;

@Service
public class IndicadoresService {

    private final IndicadoresRepository repository;

    public IndicadoresService(IndicadoresRepository repository) {
        this.repository = repository;
    }

    /**
     * @param de          data inicial (inclusive) da ocorrencia; {@code null} = sem limite
     * @param ate         data final (inclusive); {@code null} = sem limite
     * @param agrupamento granularidade de {@code porPeriodo}; {@code null} = mes
     */
    @Transactional(readOnly = true)
    public IndicadoresResponse consultar(LocalDate de, LocalDate ate, Agrupamento agrupamento) {
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new FiltroInvalidoException("A data inicial nao pode ser posterior a data final");
        }
        Agrupamento granularidade = agrupamento == null ? Agrupamento.MES : agrupamento;

        long[] totais = repository.totais(de, ate);
        return new IndicadoresResponse(
                totais[0],
                totais[1],
                totais[2],
                new FiltroAplicado(de, ate, granularidade.name()),
                repository.porTurma(de, ate),
                repository.porTipo(de, ate),
                repository.porPeriodo(de, ate, granularidade));
    }
}
