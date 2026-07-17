package br.com.trilhaaprovacao.compartilhado.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TratadorDeErros {
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

    private ResponseEntity<RespostaDeErro> resposta(HttpStatus status, String codigo, String mensagem, List<String> detalhes, HttpServletRequest requisicao) {
        String correlacao = (String) requisicao.getAttribute("identificadorDeCorrelacao");
        return ResponseEntity.status(status).body(new RespostaDeErro(codigo, mensagem, correlacao, detalhes));
    }
}
