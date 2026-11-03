package br.com.projeto.ocorrencias.indicadores;

/**
 * Filtro de indicadores incoerente (ex.: data inicial depois da final). Vira 400.
 */
public class FiltroInvalidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FiltroInvalidoException(String mensagem) {
        super(mensagem);
    }
}
