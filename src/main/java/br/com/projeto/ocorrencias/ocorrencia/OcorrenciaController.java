package br.com.projeto.ocorrencias.ocorrencia;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import br.com.projeto.ocorrencias.config.ErroResposta;
import br.com.projeto.ocorrencias.ocorrencia.dto.OcorrenciaRequest;
import br.com.projeto.ocorrencias.ocorrencia.dto.OcorrenciaResponse;
import jakarta.validation.Valid;

/**
 * HTTP de ocorrencias. Quem pode chamar cada metodo tambem esta no {@code SecurityConfig}.
 *
 * <p>{@code DELETE} cancela: a ocorrencia sai da listagem e o historico permanece.</p>
 */
@RestController
@RequestMapping("/ocorrencias")
public class OcorrenciaController {

    private final OcorrenciaService service;

    public OcorrenciaController(OcorrenciaService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PROFESSOR')")
    public OcorrenciaResponse criar(@Valid @RequestBody OcorrenciaRequest dados) {
        return service.criar(dados);
    }

    @GetMapping
    @PreAuthorize("hasRole('COORDENACAO')")
    public List<OcorrenciaResponse> listar(
            @RequestParam(required = false) UUID turmaId,
            @RequestParam(required = false) UUID alunoId,
            @RequestParam(required = false) TipoOcorrencia tipo,
            @RequestParam(required = false) StatusLancamento status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return service.listar(new FiltroOcorrencia(turmaId, alunoId, tipo, status, dataInicio, dataFim));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('COORDENACAO')")
    public OcorrenciaResponse buscar(@PathVariable UUID id) {
        return service.buscar(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROFESSOR', 'COORDENACAO')")
    public OcorrenciaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody OcorrenciaRequest dados) {
        return service.atualizar(id, dados);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('PROFESSOR', 'COORDENACAO')")
    public void excluir(@PathVariable UUID id) {
        service.excluir(id);
    }

    @PatchMapping("/{id}/lancada")
    @PreAuthorize("hasRole('COORDENACAO')")
    public OcorrenciaResponse lancar(@PathVariable UUID id) {
        return service.lancar(id);
    }

    /** UUID, enum ou data fora do formato: 400 em vez do 500 generico. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroResposta parametroInvalido(MethodArgumentTypeMismatchException excecao) {
        String nome = excecao.getName();
        String dica = switch (nome) {
            case "tipo" -> "tipo desconhecido";
            case "status" -> "use NAO_LANCADA ou LANCADA";
            case "dataInicio", "dataFim" -> "use o formato yyyy-MM-dd";
            default -> "identificador invalido";
        };
        return new ErroResposta(HttpStatus.BAD_REQUEST.value(),
                "Parametro '" + nome + "' invalido: " + dica);
    }
}
