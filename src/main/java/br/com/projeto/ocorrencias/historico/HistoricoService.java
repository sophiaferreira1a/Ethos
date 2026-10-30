package br.com.projeto.ocorrencias.historico;

/**
 * Contrato unico de registro de historico, usado pelo modulo de ocorrencias (Mateus)
 * e implementado pelo modulo de historico (Bernardo).
 *
 * <p>Quem chama ({@code OcorrenciaService}) deve estar na mesma transacao: se o registro
 * falhar, a ocorrencia tambem e desfeita.</p>
 */
public interface HistoricoService {

    /**
     * Registra um evento de historico.
     *
     * @param ocorrenciaId ocorrencia afetada
     * @param usuarioId    usuario que executou a acao (ver {@code UsuarioLogado})
     * @param acao         uma constante de {@link AcaoHistorico}
     * @param detalhes     texto livre para auditoria (pode ser {@code null})
     */
    void registrar(java.util.UUID ocorrenciaId, java.util.UUID usuarioId, String acao, String detalhes);
}
