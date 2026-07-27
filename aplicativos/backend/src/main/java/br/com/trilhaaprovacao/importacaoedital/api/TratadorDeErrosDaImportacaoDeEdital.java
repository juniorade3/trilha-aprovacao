package br.com.trilhaaprovacao.importacaoedital.api;

import br.com.trilhaaprovacao.compartilhado.api.RespostaDeErro;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.FalhaNaExtracaoDoEdital;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.FalhaNaInterpretacaoAssistidaDoEdital;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ControladorDeImportacoesDeEdital.class)
class TratadorDeErrosDaImportacaoDeEdital {

    @ExceptionHandler(FalhaNaExtracaoDoEdital.class)
    ResponseEntity<RespostaDeErro> tratarFalhaNaExtracao(
            FalhaNaExtracaoDoEdital excecao,
            HttpServletRequest requisicao) {
        HttpStatus estado = "ARQUIVO_MUITO_GRANDE".equals(excecao.codigo())
                ? HttpStatus.PAYLOAD_TOO_LARGE
                : HttpStatus.UNPROCESSABLE_ENTITY;
        return resposta(estado, excecao.codigo(), excecao.getMessage(),
                requisicao);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<RespostaDeErro> tratarUploadMuitoGrande(
            HttpServletRequest requisicao) {
        return resposta(HttpStatus.PAYLOAD_TOO_LARGE,
                "ARQUIVO_MUITO_GRANDE",
                "Arquivo do edital excede o limite permitido.", requisicao);
    }

    @ExceptionHandler(FalhaNaInterpretacaoAssistidaDoEdital.class)
    ResponseEntity<RespostaDeErro> tratarFalhaNaInterpretacaoAssistida(
            FalhaNaInterpretacaoAssistidaDoEdital excecao,
            HttpServletRequest requisicao) {
        HttpStatus estado = switch (excecao.codigo()) {
            case RECURSO_OCUPADO -> HttpStatus.CONFLICT;
            case TEMPO_LIMITE_DA_IA -> HttpStatus.GATEWAY_TIMEOUT;
            case FONTE_EXPIRADA -> HttpStatus.GONE;
            case RESPOSTA_RECUSADA_PELA_IA, RESPOSTA_INVALIDA_DA_IA ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case LIMITE_DE_PAGINAS_RENDERIZADAS_EXCEDIDO ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case IA_DESABILITADA, IA_INDISPONIVEL ->
                    HttpStatus.SERVICE_UNAVAILABLE;
        };
        return resposta(estado, excecao.codigo().name(),
                excecao.getMessage(), requisicao);
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<RespostaDeErro> tratarMultipartInvalido(
            HttpServletRequest requisicao) {
        return resposta(HttpStatus.BAD_REQUEST, "UPLOAD_INVALIDO",
                "Upload do edital possui formato invalido.", requisicao);
    }

    private ResponseEntity<RespostaDeErro> resposta(HttpStatus estado,
            String codigo, String mensagem,
            HttpServletRequest requisicao) {
        String correlacao = (String) requisicao.getAttribute(
                "identificadorDeCorrelacao");
        return ResponseEntity.status(estado).body(new RespostaDeErro(
                codigo, mensagem, correlacao, List.of()));
    }
}
