package br.com.projeto.ocorrencias.ocorrencia.dto;

import java.time.LocalDate;
import java.util.UUID;

import br.com.projeto.ocorrencias.ocorrencia.TipoOcorrencia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de criacao e atualizacao. Professor e status nao vem do cliente:
 * o service grava o professor da sessao e nasce com {@code NAO_LANCADA}.
 *
 * <p>{@code turma} e o nome (ex.: {@code 6A}). Se vier preenchida, precisa ser a turma do aluno.</p>
 */
public record OcorrenciaRequest(

        @NotNull(message = "informe o aluno")
        @Valid
        AlunoReferencia aluno,

        String turma,

        @NotNull(message = "informe o tipo")
        TipoOcorrencia tipo,

        @NotBlank(message = "informe a descricao")
        @Size(min = 10, message = "descricao deve ter ao menos 10 caracteres")
        String descricao,

        @NotBlank(message = "informe as medidas tomadas")
        String medidasTomadas,

        @NotNull(message = "informe a data")
        LocalDate data) {

    public record AlunoReferencia(
            @NotNull(message = "informe o aluno") UUID id,
            String nome) {
    }
}
