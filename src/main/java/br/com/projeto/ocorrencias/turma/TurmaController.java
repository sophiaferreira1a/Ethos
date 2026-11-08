package br.com.projeto.ocorrencias.turma;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.projeto.ocorrencias.turma.dto.TurmaRequest;
import br.com.projeto.ocorrencias.turma.dto.TurmaResponse;
import jakarta.validation.Valid;

/**
 * {@code GET /turmas}: lista para os filtros do professor e da coordenacao, em ordem de nome.
 * O CRUD e exclusivo da coordenacao.
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COORDENACAO')")
    public TurmaResponse criar(@Valid @RequestBody TurmaRequest dados) {
        return TurmaResponse.de(service.criar(dados));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('COORDENACAO')")
    public TurmaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody TurmaRequest dados) {
        return TurmaResponse.de(service.atualizar(id, dados));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('COORDENACAO')")
    public void excluir(@PathVariable UUID id) {
        service.excluir(id);
    }
}
