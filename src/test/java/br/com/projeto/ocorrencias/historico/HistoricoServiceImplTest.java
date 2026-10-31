package br.com.projeto.ocorrencias.historico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Testes unitarios (sem banco) das regras de {@link HistoricoServiceImpl}.
 * A transacao obrigatoria e a gravacao real sao cobertas em {@code HistoricoIntegracaoTest}.
 */
class HistoricoServiceImplTest {

    private HistoricoRepository repository;
    private HistoricoServiceImpl service;

    private final UUID ocorrenciaId = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        repository = mock(HistoricoRepository.class);
        service = new HistoricoServiceImpl(repository);
    }

    @Test
    void gravaEventoComOsDadosInformados() {
        service.registrar(ocorrenciaId, usuarioId, AcaoHistorico.OCORRENCIA_CRIADA.name(), "criada pelo professor");

        ArgumentCaptor<Historico> captor = ArgumentCaptor.forClass(Historico.class);
        verify(repository).saveAndFlush(captor.capture());
        Historico gravado = captor.getValue();
        assertEquals(ocorrenciaId, gravado.getOcorrenciaId());
        assertEquals(usuarioId, gravado.getUsuarioId());
        assertEquals("OCORRENCIA_CRIADA", gravado.getAcao());
        assertEquals("criada pelo professor", gravado.getDetalhes());
    }

    @Test
    void detalhesEmBrancoViramNulo() {
        service.registrar(ocorrenciaId, usuarioId, "OCORRENCIA_LANCADA", "   ");

        ArgumentCaptor<Historico> captor = ArgumentCaptor.forClass(Historico.class);
        verify(repository).saveAndFlush(captor.capture());
        assertNull(captor.getValue().getDetalhes());
    }

    @Test
    void aceitaTodasAsAcoesDoContrato() {
        for (AcaoHistorico acao : AcaoHistorico.values()) {
            service.registrar(ocorrenciaId, usuarioId, acao.name(), null);
        }
        verify(repository, times(AcaoHistorico.values().length)).saveAndFlush(any(Historico.class));
    }

    @Test
    void acaoDesconhecidaERejeitadaSemGravar() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar(ocorrenciaId, usuarioId, "ACAO_QUE_NAO_EXISTE", null));
        verify(repository, never()).saveAndFlush(any(Historico.class));
    }

    @Test
    void acaoNulaOuEmBrancoERejeitada() {
        assertThrows(IllegalArgumentException.class, () -> service.registrar(ocorrenciaId, usuarioId, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.registrar(ocorrenciaId, usuarioId, " ", null));
        verifyNoInteractions(repository);
    }

    @Test
    void idsNulosSaoRejeitados() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar(null, usuarioId, "OCORRENCIA_CRIADA", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar(ocorrenciaId, null, "OCORRENCIA_CRIADA", null));
        verifyNoInteractions(repository);
    }
}
