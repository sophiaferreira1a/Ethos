package br.com.projeto.ocorrencias.ocorrencia.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.projeto.ocorrencias.ocorrencia.Ocorrencia;
import br.com.projeto.ocorrencias.ocorrencia.StatusLancamento;
import br.com.projeto.ocorrencias.ocorrencia.TipoOcorrencia;
import br.com.projeto.ocorrencias.usuario.Usuario;

public record OcorrenciaResponse(
        UUID id,
        UUID alunoId,
        String alunoNome,
        UUID turmaId,
        String turmaNome,
        UUID professorId,
        String professorNome,
        TipoOcorrencia tipo,
        String descricao,
        String medidasTomadas,
        LocalDate data,
        StatusLancamento statusLancamento,
        Instant dataLancamento,
        UUID responsavelLancamentoId,
        String responsavelLancamentoNome,
        Instant criadoEm) {

    public static OcorrenciaResponse de(Ocorrencia ocorrencia) {
        Usuario responsavel = ocorrencia.getResponsavelLancamento();
        return new OcorrenciaResponse(
                ocorrencia.getId(),
                ocorrencia.getAluno().getId(),
                ocorrencia.getAluno().getNome(),
                ocorrencia.getTurma().getId(),
                ocorrencia.getTurma().getNome(),
                ocorrencia.getProfessor().getId(),
                ocorrencia.getProfessor().getNome(),
                ocorrencia.getTipo(),
                ocorrencia.getDescricao(),
                ocorrencia.getMedidasTomadas(),
                ocorrencia.getData(),
                ocorrencia.getStatusLancamento(),
                ocorrencia.getDataLancamento(),
                responsavel == null ? null : responsavel.getId(),
                responsavel == null ? null : responsavel.getNome(),
                ocorrencia.getCriadoEm());
    }
}
