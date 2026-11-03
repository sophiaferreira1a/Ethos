package br.com.projeto.ocorrencias.turma;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.projeto.ocorrencias.turma.dto.TurmaResponse;

/**
 * {@code GET /turmas}: lista para os filtros do professor e da coordenacao, em ordem de nome.
 */
@RestController
@RequestMapping("/turmas")
public class TurmaController {

    private final TurmaService service;

    public TurmaController(TurmaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PROFESSOR', 'COORDENACAO')")
    public List<TurmaResponse> listar() {
        return service.listar().stream()
                .map(TurmaResponse::de)
                .toList();
    }
}
