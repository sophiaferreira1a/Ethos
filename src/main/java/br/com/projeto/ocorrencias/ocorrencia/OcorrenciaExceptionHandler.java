package br.com.projeto.ocorrencias.ocorrencia;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import br.com.projeto.ocorrencias.config.ErroResposta;

/**
 * Traduz as excecoes deste modulo para o mesmo JSON de erro do restante da API.
 */
@RestControllerAdvice
public class OcorrenciaExceptionHandler {

    @ExceptionHandler(DadosOcorrenciaInvalidosException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroResposta dadosInvalidos(DadosOcorrenciaInvalidosException excecao) {
        return new ErroResposta(HttpStatus.BAD_REQUEST.value(), excecao.getMessage());
    }

    @ExceptionHandler(ConflitoOcorrenciaException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErroResposta conflito(ConflitoOcorrenciaException excecao) {
        return new ErroResposta(HttpStatus.CONFLICT.value(), excecao.getMessage());
    }
}
