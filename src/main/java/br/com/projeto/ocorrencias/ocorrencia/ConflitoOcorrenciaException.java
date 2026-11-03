package br.com.projeto.ocorrencias.ocorrencia;

/**
 * Operacao incompativel com o estado da ocorrencia (HTTP 409).
 */
public class ConflitoOcorrenciaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflitoOcorrenciaException(String mensagem) {
        super(mensagem);
    }
}
