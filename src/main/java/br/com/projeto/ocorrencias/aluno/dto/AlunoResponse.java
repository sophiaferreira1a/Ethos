package br.com.projeto.ocorrencias.aluno.dto;

import java.util.UUID;

import br.com.projeto.ocorrencias.aluno.Aluno;

/**
 * Aluno na busca do professor. {@code turma} e o nome (ex.: {@code 6A}), o mesmo valor
 * que o {@code POST /ocorrencias} recebe no campo {@code turma}.
 */
public record AlunoResponse(UUID id, String nome, UUID turmaId, String turma) {

    public static AlunoResponse de(Aluno aluno) {
        return new AlunoResponse(aluno.getId(), aluno.getNome(),
                aluno.getTurma().getId(), aluno.getTurma().getNome());
    }
}
