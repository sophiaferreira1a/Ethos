package br.com.projeto.ocorrencias.indicadores.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Indicadores do dashboard da coordenacao.
 *
 * <p>Somente contagens agregadas por turma, tipo e periodo. Proposital: nada por
 * aluno e nenhum ranking, para nao rotular estudantes (secao 9 da documentacao).</p>
 */
public record IndicadoresResponse(
        long total,
        long naoLancadas,
        long lancadas,
        FiltroAplicado filtro,
        List<PorTurma> porTurma,
        List<PorTipo> porTipo,
        List<PorPeriodo> porPeriodo) {

    /** Filtro efetivamente usado, para o front exibir sem adivinhar o padrao. */
    public record FiltroAplicado(LocalDate de, LocalDate ate, String agrupamento) {
    }

    public record PorTurma(String turma, long total, long naoLancadas, long lancadas) {
    }

    public record PorTipo(String tipo, long total, long naoLancadas, long lancadas) {
    }

    /** {@code periodo}: {@code YYYY-MM} (mes) ou {@code YYYY-MM-DD} (dia ou inicio da semana). */
    public record PorPeriodo(String periodo, long total, long naoLancadas, long lancadas) {
    }
}
