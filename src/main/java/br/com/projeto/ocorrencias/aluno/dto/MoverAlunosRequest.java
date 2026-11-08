package br.com.projeto.ocorrencias.aluno.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Move varios alunos para a mesma turma em uma unica transacao.
 */
public record MoverAlunosRequest(

        @NotEmpty(message = "selecione ao menos um aluno")
        @Size(max = 200, message = "mova no maximo 200 alunos por vez")
        List<@NotNull(message = "aluno invalido") UUID> alunoIds,

        @NotNull(message = "informe a turma de destino")
        UUID turmaId) {
}
