package br.com.projeto.ocorrencias.aluno;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.projeto.ocorrencias.aluno.dto.AlunoRequest;
import br.com.projeto.ocorrencias.aluno.dto.AlunoResponse;
import br.com.projeto.ocorrencias.aluno.dto.MoverAlunosRequest;
import jakarta.validation.Valid;

/**
 * {@code GET /alunos?busca=&turma=}: busca do passo 1 do registro de ocorrencia.
 * {@code busca} e parte do nome; {@code turma} e o nome da turma.
 *
 * <p>CRUD e movimentacao em lote sao exclusivos da coordenacao.</p>
 */
@RestController
@RequestMapping("/alunos")
public class AlunoController {

    private final AlunoService service;

    public AlunoController(AlunoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PROFESSOR', 'COORDENACAO')")
    public List<AlunoResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) String turma) {
        return service.listar(busca, turma).stream()
                .map(AlunoResponse::de)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COORDENACAO')")
    public AlunoResponse criar(@Valid @RequestBody AlunoRequest dados) {
        return AlunoResponse.de(service.criar(dados));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('COORDENACAO')")
    public AlunoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody AlunoRequest dados) {
        return AlunoResponse.de(service.atualizar(id, dados));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('COORDENACAO')")
    public void excluir(@PathVariable UUID id) {
        service.excluir(id);
    }

    @PatchMapping("/turma")
    @PreAuthorize("hasRole('COORDENACAO')")
    public List<AlunoResponse> moverParaTurma(@Valid @RequestBody MoverAlunosRequest dados) {
        return service.moverEmLote(dados).stream()
                .map(AlunoResponse::de)
                .toList();
    }
}
