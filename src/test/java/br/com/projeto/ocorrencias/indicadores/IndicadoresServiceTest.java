package br.com.projeto.ocorrencias.indicadores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.projeto.ocorrencias.indicadores.dto.IndicadoresResponse;
import br.com.projeto.ocorrencias.indicadores.dto.IndicadoresResponse.PorPeriodo;
import br.com.projeto.ocorrencias.indicadores.dto.IndicadoresResponse.PorTipo;
import br.com.projeto.ocorrencias.indicadores.dto.IndicadoresResponse.PorTurma;

class IndicadoresServiceTest {

    private IndicadoresRepository repository;
    private IndicadoresService service;

    @BeforeEach
    void preparar() {
        repository = mock(IndicadoresRepository.class);
        service = new IndicadoresService(repository);
    }

    @Test
    void montaRespostaComTotaisEAgrupamentos() {
        LocalDate de = LocalDate.of(2026, 10, 1);
        LocalDate ate = LocalDate.of(2026, 10, 31);
        when(repository.totais(de, ate)).thenReturn(new long[] { 4, 3, 1 });
        when(repository.porTurma(de, ate)).thenReturn(List.of(new PorTurma("2A", 4, 3, 1)));
        when(repository.porTipo(de, ate)).thenReturn(List.of(new PorTipo("COMPORTAMENTAL", 4, 3, 1)));
        when(repository.porPeriodo(de, ate, Agrupamento.SEMANA))
                .thenReturn(List.of(new PorPeriodo("2026-10-05", 4, 3, 1)));

        IndicadoresResponse resposta = service.consultar(de, ate, Agrupamento.SEMANA);

        assertEquals(4, resposta.total());
        assertEquals(3, resposta.naoLancadas());
        assertEquals(1, resposta.lancadas());
        assertEquals("2A", resposta.porTurma().get(0).turma());
        assertEquals("COMPORTAMENTAL", resposta.porTipo().get(0).tipo());
        assertEquals("2026-10-05", resposta.porPeriodo().get(0).periodo());
        assertEquals(de, resposta.filtro().de());
        assertEquals(ate, resposta.filtro().ate());
        assertEquals("SEMANA", resposta.filtro().agrupamento());
    }

    @Test
    void agrupamentoNuloUsaMes() {
        when(repository.totais(null, null)).thenReturn(new long[] { 0, 0, 0 });
        when(repository.porTurma(null, null)).thenReturn(List.of());
        when(repository.porTipo(null, null)).thenReturn(List.of());
        when(repository.porPeriodo(null, null, Agrupamento.MES)).thenReturn(List.of());

        IndicadoresResponse resposta = service.consultar(null, null, null);

        assertEquals("MES", resposta.filtro().agrupamento());
        assertNull(resposta.filtro().de());
        verify(repository).porPeriodo(null, null, Agrupamento.MES);
    }

    @Test
    void dataInicialPosteriorAFinalELancaFiltroInvalido() {
        assertThrows(FiltroInvalidoException.class,
                () -> service.consultar(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1), Agrupamento.MES));
        verifyNoInteractions(repository);
    }

    @Test
    void dataInicialIgualAFinalEValida() {
        LocalDate dia = LocalDate.of(2026, 10, 7);
        when(repository.totais(dia, dia)).thenReturn(new long[] { 1, 1, 0 });
        when(repository.porTurma(dia, dia)).thenReturn(List.of());
        when(repository.porTipo(dia, dia)).thenReturn(List.of());
        when(repository.porPeriodo(dia, dia, Agrupamento.DIA)).thenReturn(List.of());

        assertEquals(1, service.consultar(dia, dia, Agrupamento.DIA).total());
    }
}
