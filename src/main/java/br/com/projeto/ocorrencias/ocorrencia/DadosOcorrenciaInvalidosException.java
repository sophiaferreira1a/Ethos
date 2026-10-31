package br.com.projeto.ocorrencias.ocorrencia;

/**
 * Dados da ocorrencia que nao passam na regra de negocio (HTTP 400).
 */
public class DadosOcorrenciaInvalidosException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DadosOcorrenciaInvalidosException(String mensagem) {
        super(mensagem);
    }
}
