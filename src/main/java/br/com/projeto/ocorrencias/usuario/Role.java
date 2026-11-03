package br.com.projeto.ocorrencias.usuario;

/**
 * Perfis de acesso. O valor gravado em {@code usuarios.role} e o nome da constante
 * (PROFESSOR / COORDENACAO); o Spring Security usa o prefixo ROLE_.
 */
public enum Role {
    PROFESSOR,
    COORDENACAO;

    public String authority() {
        return "ROLE_" + name();
    }
}
