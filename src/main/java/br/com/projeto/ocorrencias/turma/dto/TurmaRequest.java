package br.com.projeto.ocorrencias.turma.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de criacao e atualizacao de turma.
 */
public record TurmaRequest(

        @NotBlank(message = "informe o nome da turma")
        @Size(max = 20, message = "o nome deve ter no maximo 20 caracteres")
        String nome) {
}
