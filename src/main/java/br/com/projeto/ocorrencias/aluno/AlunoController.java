package br.com.projeto.ocorrencias.aluno;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.projeto.ocorrencias.aluno.dto.AlunoResponse;

/**
 * {@code GET /alunos?busca=&turma=}: busca do passo 1 do registro de ocorrencia.
 * {@code busca} e parte do nome; {@code turma} e o nome da turma.
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
}
