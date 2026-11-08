package br.com.projeto.ocorrencias.config;

/**
 * Operacao de cadastro incompativel com os dados existentes (HTTP 409).
 */
public class ConflitoCadastroException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflitoCadastroException(String mensagem) {
        super(mensagem);
    }
}
