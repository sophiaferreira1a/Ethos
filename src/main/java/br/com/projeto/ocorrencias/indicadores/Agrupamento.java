package br.com.projeto.ocorrencias.indicadores;

/**
 * Granularidade do agrupamento por periodo. Os valores SQL sao constantes fixas
 * (whitelist): nada vindo da requisicao e concatenado na consulta.
 */
public enum Agrupamento {
    /** Um grupo por dia ({@code 2026-10-07}). */
    DIA("day", "YYYY-MM-DD"),
    /** Um grupo por semana ISO, rotulado pela segunda-feira ({@code 2026-10-05}). */
    SEMANA("week", "YYYY-MM-DD"),
    /** Um grupo por mes ({@code 2026-10}). Padrao. */
    MES("month", "YYYY-MM");

    private final String unidadeSql;
    private final String formatoSql;

    Agrupamento(String unidadeSql, String formatoSql) {
        this.unidadeSql = unidadeSql;
        this.formatoSql = formatoSql;
    }

    String unidadeSql() {
        return unidadeSql;
    }

    String formatoSql() {
        return formatoSql;
    }
}
