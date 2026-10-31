package br.com.projeto.ocorrencias.indicadores;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Repository;

import br.com.projeto.ocorrencias.indicadores.dto.IndicadoresResponse.PorPeriodo;
import br.com.projeto.ocorrencias.indicadores.dto.IndicadoresResponse.PorTipo;
import br.com.projeto.ocorrencias.indicadores.dto.IndicadoresResponse.PorTurma;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * Consultas agregadas do dashboard. <b>Somente leitura</b>: so {@code SELECT}, em SQL
 * nativo, sobre as tabelas {@code ocorrencias} e {@code turmas}. Nao usa nem altera o
 * repository do modulo de ocorrencias.
 *
 * <p>Os filtros de data sao sempre parametros ligados; as unicas partes
 * concatenadas vem das constantes de {@link Agrupamento}.</p>
 */
@Repository
public class IndicadoresRepository {

    private static final String CONTAGENS = """
            count(*) as total,
            count(*) filter (where o.status_lancamento = 'NAO_LANCADA') as nao_lancadas,
            count(*) filter (where o.status_lancamento = 'LANCADA') as lancadas
            """;

    @PersistenceContext
    private EntityManager em;

    /** Retorna {total, naoLancadas, lancadas}. */
    public long[] totais(LocalDate de, LocalDate ate) {
        String sql = "select " + CONTAGENS + " from ocorrencias o" + onde(de, ate);
        Object[] linha = (Object[]) consulta(sql, de, ate).getSingleResult();
        return new long[] { numero(linha[0]), numero(linha[1]), numero(linha[2]) };
    }

    public List<PorTurma> porTurma(LocalDate de, LocalDate ate) {
        String sql = "select t.nome, " + CONTAGENS
                + " from ocorrencias o join turmas t on t.id = o.turma_id"
                + onde(de, ate)
                + " group by t.nome order by count(*) desc, t.nome asc";
        return lista(sql, de, ate,
                l -> new PorTurma((String) l[0], numero(l[1]), numero(l[2]), numero(l[3])));
    }

    public List<PorTipo> porTipo(LocalDate de, LocalDate ate) {
        String sql = "select o.tipo, " + CONTAGENS
                + " from ocorrencias o"
                + onde(de, ate)
                + " group by o.tipo order by count(*) desc, o.tipo asc";
        return lista(sql, de, ate,
                l -> new PorTipo((String) l[0], numero(l[1]), numero(l[2]), numero(l[3])));
    }

    public List<PorPeriodo> porPeriodo(LocalDate de, LocalDate ate, Agrupamento agrupamento) {
        String rotulo = "to_char(date_trunc('" + agrupamento.unidadeSql() + "', cast(o.data as timestamp)), '"
                + agrupamento.formatoSql() + "')";
        String sql = "select " + rotulo + " as periodo, " + CONTAGENS
                + " from ocorrencias o"
                + onde(de, ate)
                + " group by 1 order by 1 asc";
        return lista(sql, de, ate,
                l -> new PorPeriodo((String) l[0], numero(l[1]), numero(l[2]), numero(l[3])));
    }

    private static String onde(LocalDate de, LocalDate ate) {
        List<String> condicoes = new ArrayList<>();
        if (de != null) {
            condicoes.add("o.data >= :de");
        }
        if (ate != null) {
            condicoes.add("o.data <= :ate");
        }
        return condicoes.isEmpty() ? "" : " where " + String.join(" and ", condicoes);
    }

    private Query consulta(String sql, LocalDate de, LocalDate ate) {
        Query query = em.createNativeQuery(sql);
        if (de != null) {
            query.setParameter("de", de);
        }
        if (ate != null) {
            query.setParameter("ate", ate);
        }
        return query;
    }

    private <T> List<T> lista(String sql, LocalDate de, LocalDate ate, Function<Object[], T> mapeador) {
        List<?> linhas = consulta(sql, de, ate).getResultList();
        List<T> resultado = new ArrayList<>(linhas.size());
        for (Object linha : linhas) {
            resultado.add(mapeador.apply((Object[]) linha));
        }
        return resultado;
    }

    private static long numero(Object valor) {
        return valor == null ? 0L : ((Number) valor).longValue();
    }
}
