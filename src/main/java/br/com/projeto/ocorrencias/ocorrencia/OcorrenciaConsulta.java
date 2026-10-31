package br.com.projeto.ocorrencias.ocorrencia;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

final class OcorrenciaConsulta {

    private OcorrenciaConsulta() {
    }

    static Specification<Ocorrencia> com(FiltroOcorrencia filtro) {
        return (raiz, consulta, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (filtro.turmaId() != null) {
                predicados.add(cb.equal(raiz.get("turma").get("id"), filtro.turmaId()));
            }
            if (filtro.alunoId() != null) {
                predicados.add(cb.equal(raiz.get("aluno").get("id"), filtro.alunoId()));
            }
            if (filtro.tipo() != null) {
                predicados.add(cb.equal(raiz.get("tipo"), filtro.tipo()));
            }
            if (filtro.status() != null) {
                predicados.add(cb.equal(raiz.get("statusLancamento"), filtro.status()));
            }
            if (filtro.dataInicio() != null) {
                predicados.add(cb.greaterThanOrEqualTo(raiz.get("data"), filtro.dataInicio()));
            }
            if (filtro.dataFim() != null) {
                predicados.add(cb.lessThanOrEqualTo(raiz.get("data"), filtro.dataFim()));
            }
            if (predicados.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }
}
