package br.com.projeto.ocorrencias.turma.dto;

import java.util.UUID;

import br.com.projeto.ocorrencias.turma.Turma;

public record TurmaResponse(UUID id, String nome) {

    public static TurmaResponse de(Turma turma) {
        return new TurmaResponse(turma.getId(), turma.getNome());
    }
}
