package br.com.projeto.ocorrencias.usuario.dto;

/**
 * Resposta de login/usuario autenticado. Nunca inclui senha nem hash.
 *
 * @param redirectTo rota que o frontend deve abrir depois do login, conforme o perfil
 */
public record LoginResponse(String nome, String email, String role, String redirectTo) {
}
