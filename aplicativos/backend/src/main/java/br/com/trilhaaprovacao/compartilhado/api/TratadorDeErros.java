package br.com.trilhaaprovacao.compartilhado.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class TratadorDeErros {
    private static final Logger LOG = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler(ConflitoDeDominio.class)
    ResponseEntity<RespostaDeErro> tratarConflito(ConflitoDeDominio excecao, HttpServletRequest requisicao) {
        return resposta(HttpStatus.CONFLICT, excecao.codigo(), excecao.getMessage(), List.of(), requisicao);
    }

    @ExceptionHandler(RegraDeDominio.class)
    ResponseEntity<RespostaDeErro> tratarRegraDeDominio(RegraDeDominio excecao, HttpServletRequest requisicao) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, excecao.codigo(), excecao.getMessage(), List.of(), requisicao);
    }

    @ExceptionHandler(RecursoNaoEncontrado.class)
    ResponseEntity<RespostaDeErro> tratarRecursoNaoEncontrado(RecursoNaoEncontrado excecao, HttpServletRequest requisicao) {
        return resposta(HttpStatus.NOT_FOUND, excecao.codigo(), excecao.getMessage(), List.of(), requisicao);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<RespostaDeErro> tratarIntegridade(HttpServletRequest requisicao) {
        return resposta(HttpStatus.CONFLICT, "CONFLITO_DE_DADOS",
                "A operacao conflita com dados existentes.", List.of(), requisicao);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<RespostaDeErro> tratarConcorrencia(HttpServletRequest requisicao) {
        return resposta(HttpStatus.CONFLICT, "DADO_ALTERADO_CONCORRENTEMENTE",
                "O registro foi alterado por outra operacao. Atualize os dados e tente novamente.", List.of(), requisicao);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<RespostaDeErro> tratarValidacao(MethodArgumentNotValidException excecao, HttpServletRequest requisicao) {
        List<String> detalhes = excecao.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage()).toList();
        return resposta(HttpStatus.BAD_REQUEST, "ENTRADA_INVALIDA", "Existem campos invalidos.", detalhes, requisicao);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<RespostaDeErro> tratarValidacaoDeParametros(
            ConstraintViolationException excecao, HttpServletRequest requisicao) {
        List<String> detalhes = excecao.getConstraintViolations().stream()
                .map(violacao -> violacao.getPropertyPath() + ": " + violacao.getMessage())
                .toList();
        return resposta(HttpStatus.BAD_REQUEST, "ENTRADA_INVALIDA",
                "Existem parametros invalidos.", detalhes, requisicao);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<RespostaDeErro> tratarEntradaIlegivel(HttpServletRequest requisicao) {
        return resposta(HttpStatus.BAD_REQUEST, "ENTRADA_INVALIDA",
                "A requisicao possui formato invalido.", List.of(), requisicao);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<RespostaDeErro> tratarAcessoNegado(HttpServletRequest requisicao) {
        return resposta(HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Voce nao tem permissao para esta operacao.", List.of(), requisicao);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<RespostaDeErro> tratarFalhaDeAutenticacao(HttpServletRequest requisicao) {
        return resposta(HttpStatus.UNAUTHORIZED, "CREDENCIAIS_INVALIDAS", "E-mail ou senha invalidos.", List.of(), requisicao);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<RespostaDeErro> tratarErroInesperado(Exception excecao, HttpServletRequest requisicao) {
        String correlacao = (String) requisicao.getAttribute("identificadorDeCorrelacao");
        LOG.error("Erro inesperado. identificadorDeCorrelacao={}", correlacao, excecao);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO",
                "Nao foi possivel concluir a operacao.", List.of(), requisicao);
    }

    private ResponseEntity<RespostaDeErro> resposta(HttpStatus status, String codigo, String mensagem, List<String> detalhes, HttpServletRequest requisicao) {
        String correlacao = (String) requisicao.getAttribute("identificadorDeCorrelacao");
        return ResponseEntity.status(status).body(new RespostaDeErro(codigo, mensagem, correlacao, detalhes));
    }
}
