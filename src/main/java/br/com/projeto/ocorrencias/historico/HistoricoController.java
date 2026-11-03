package br.com.projeto.ocorrencias.historico;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import br.com.projeto.ocorrencias.config.ErroResposta;
import br.com.projeto.ocorrencias.historico.dto.HistoricoResponse;

/**
 * {@code GET /ocorrencias/{id}/historico}: acesso exclusivo da coordenacao
 * (regra tambem aplicada no {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/ocorrencias/{ocorrenciaId}/historico")
public class HistoricoController {

    private final HistoricoConsultaService consulta;

    public HistoricoController(HistoricoConsultaService consulta) {
        this.consulta = consulta;
    }

    @GetMapping
    @PreAuthorize("hasRole('COORDENACAO')")
    public List<HistoricoResponse> listar(@PathVariable UUID ocorrenciaId) {
        return consulta.listarPorOcorrencia(ocorrenciaId);
    }

    /** Id que nao e UUID: 400 em vez do 500 generico. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroResposta idInvalido(MethodArgumentTypeMismatchException excecao) {
        return new ErroResposta(HttpStatus.BAD_REQUEST.value(), "Identificador de ocorrencia invalido");
    }
}
