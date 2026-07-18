package br.com.trilhaaprovacao.compartilhado.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TratadorDeErros {
    private static final Logger LOG = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler(ConflitoDeDominio.class)
    ResponseEntity<RespostaDeErro> tratarConflito(ConflitoDeDominio excecao, HttpServletRequest requisicao) {
        return resposta(HttpStatus.CONFLICT, excecao.codigo(), excecao.getMessage(), List.of(), requisicao);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<RespostaDeErro> tratarValidacao(MethodArgumentNotValidException excecao, HttpServletRequest requisicao) {
        List<String> detalhes = excecao.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage()).toList();
        return resposta(HttpStatus.BAD_REQUEST, "ENTRADA_INVALIDA", "Existem campos invalidos.", detalhes, requisicao);
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
