package br.com.projeto.ocorrencias.ocorrencia.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.projeto.ocorrencias.ocorrencia.TipoOcorrencia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cria a mesma ocorrencia para varios alunos de uma unica turma.
 * Cada aluno gera uma ocorrencia independente, tudo na mesma transacao.
 */
public record OcorrenciaLoteRequest(

        @NotEmpty(message = "selecione ao menos um aluno")
        @Size(max = 50, message = "selecione no maximo 50 alunos por vez")
        List<@NotNull(message = "aluno invalido") UUID> alunoIds,

        String turma,

        @NotNull(message = "informe o tipo")
        TipoOcorrencia tipo,

        @NotBlank(message = "informe a descricao")
        @Size(min = 10, message = "descricao deve ter ao menos 10 caracteres")
        String descricao,

        @NotBlank(message = "informe as medidas tomadas")
        String medidasTomadas,

        @Size(max = 2000, message = "sugestoes devem ter no maximo 2000 caracteres")
        String medidasSugeridas,

        @NotNull(message = "informe a data")
        LocalDate data) {
}
