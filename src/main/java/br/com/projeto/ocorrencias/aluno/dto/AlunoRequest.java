package br.com.projeto.ocorrencias.aluno.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de criacao e atualizacao de aluno.
 */
public record AlunoRequest(

        @NotBlank(message = "informe o nome do aluno")
        @Size(max = 150, message = "o nome deve ter no maximo 150 caracteres")
        String nome,

        @NotNull(message = "informe a turma do aluno")
        UUID turmaId) {
}
