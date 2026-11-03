package br.com.projeto.ocorrencias.indicadores;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import br.com.projeto.ocorrencias.config.ErroResposta;
import br.com.projeto.ocorrencias.indicadores.dto.IndicadoresResponse;

/**
 * {@code GET /indicadores}: acesso exclusivo da coordenacao (regra tambem aplicada
 * no {@code SecurityConfig}).
 *
 * <p>Parametros opcionais: {@code de} e {@code ate} (data da ocorrencia, {@code yyyy-MM-dd},
 * inclusivos) e {@code agrupamento} ({@code DIA}, {@code SEMANA} ou {@code MES}; padrao
 * {@code MES}).</p>
 */
@RestController
@RequestMapping("/indicadores")
public class IndicadoresController {

    private final IndicadoresService service;

    public IndicadoresController(IndicadoresService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('COORDENACAO')")
    public IndicadoresResponse indicadores(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(defaultValue = "MES") Agrupamento agrupamento) {
        return service.consultar(de, ate, agrupamento);
    }

    @ExceptionHandler(FiltroInvalidoException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroResposta filtroInvalido(FiltroInvalidoException excecao) {
        return new ErroResposta(HttpStatus.BAD_REQUEST.value(), excecao.getMessage());
    }

    /** Data fora de yyyy-MM-dd ou agrupamento desconhecido: 400 em vez do 500 generico. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroResposta parametroInvalido(MethodArgumentTypeMismatchException excecao) {
        String dica = "agrupamento".equals(excecao.getName())
                ? "use DIA, SEMANA ou MES"
                : "use o formato yyyy-MM-dd";
        return new ErroResposta(HttpStatus.BAD_REQUEST.value(),
                "Parametro '" + excecao.getName() + "' invalido: " + dica);
    }
}
