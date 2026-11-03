package br.com.projeto.ocorrencias.usuario;

/**
 * Lancada quando se tenta cadastrar um e-mail que ja existe (HTTP 409).
 */
public class EmailJaCadastradoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EmailJaCadastradoException(String email) {
        super("E-mail ja cadastrado: " + email);
    }
}
