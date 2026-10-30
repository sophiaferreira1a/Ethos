package br.com.projeto.ocorrencias.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import br.com.projeto.ocorrencias.usuario.EmailJaCadastradoException;
import jakarta.persistence.EntityNotFoundException;

/**
 * Traduz excecoes de negocio em respostas JSON com status coerente.
 */
@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErroResposta falhaAutenticacao(AuthenticationException excecao) {
        // Nao revelar se o e-mail existe: mensagem unica para credencial invalida.
        return new ErroResposta(HttpStatus.UNAUTHORIZED.value(), "E-mail ou senha invalidos");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErroResposta acessoNegado(AccessDeniedException excecao) {
        return new ErroResposta(HttpStatus.FORBIDDEN.value(), "Acesso negado para o seu perfil");
    }

    @ExceptionHandler(EmailJaCadastradoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErroResposta emailDuplicado(EmailJaCadastradoException excecao) {
        return new ErroResposta(HttpStatus.CONFLICT.value(), excecao.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErroResposta naoEncontrado(EntityNotFoundException excecao) {
        return new ErroResposta(HttpStatus.NOT_FOUND.value(), excecao.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErroResposta rotaInexistente(NoResourceFoundException excecao) {
        return new ErroResposta(HttpStatus.NOT_FOUND.value(), "Recurso nao encontrado");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroValidacao dadosInvalidos(MethodArgumentNotValidException excecao) {
        Map<String, String> campos = new LinkedHashMap<>();
        excecao.getBindingResult().getFieldErrors()
                .forEach(erro -> campos.putIfAbsent(erro.getField(), erro.getDefaultMessage()));
        return new ErroValidacao(HttpStatus.BAD_REQUEST.value(), "Dados invalidos", campos);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroResposta corpoIlegivel(HttpMessageNotReadableException excecao) {
        return new ErroResposta(HttpStatus.BAD_REQUEST.value(), "Corpo da requisicao invalido");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErroResposta erroInesperado(Exception excecao) {
        log.error("Erro nao tratado", excecao);
        return new ErroResposta(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Erro interno");
    }

    public record ErroValidacao(int status, String erro, Map<String, String> campos) {
    }
}
