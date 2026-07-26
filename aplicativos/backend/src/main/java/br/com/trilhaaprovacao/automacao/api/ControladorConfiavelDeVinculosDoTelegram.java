package br.com.trilhaaprovacao.automacao.api;

import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeVinculosDoTelegram;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeAplicacaoDeOperacoesAssistidas;
import br.com.trilhaaprovacao.compartilhado.api.RespostaDeErro;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@Hidden
@RestController
@RequestMapping("/api/v1/integracoes-confiaveis/telegram")
@ConditionalOnProperty(prefix = "trilha.automacao", name = "habilitada",
        havingValue = "true")
public class ControladorConfiavelDeVinculosDoTelegram {
    private final ServicoDeVinculosDoTelegram servico;
    private final ServicoDeAplicacaoDeOperacoesAssistidas aplicacao;
    private final ObjectMapper mapeador;

    public ControladorConfiavelDeVinculosDoTelegram(
            ServicoDeVinculosDoTelegram servico,
            ServicoDeAplicacaoDeOperacoesAssistidas aplicacao,
            ObjectMapper mapeador) {
        this.servico = servico;
        this.aplicacao = aplicacao;
        this.mapeador = mapeador;
    }

    @PostMapping("/vinculos")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vinculo ativado."),
        @ApiResponse(responseCode = "401", description = "Gateway nao autorizado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "404", description = "Codigo nao encontrado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "409", description = "Telegram ja vinculado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "422", description = "Codigo expirado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class)))
    })
    public ResponseEntity<RespostaDaTrocaDoCodigoDeVinculo> trocar(
            @Valid @RequestBody RequisicaoDeTrocaDoCodigoDeVinculo requisicao) {
        var troca = servico.trocarCodigo(requisicao.codigo(),
                requisicao.identificadorDoBot(), requisicao.identificadorDoTelegram(),
                requisicao.identificadorDoChat());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(RespostaDaTrocaDoCodigoDeVinculo.de(troca));
    }

    @PostMapping("/vinculos/{identificador}/provisionamento")
    public ResponseEntity<RespostaDeVinculoDoTelegram> provisionar(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeProvisionamentoDoAgente requisicao) {
        var vinculo = servico.registrarProvisionamento(identificador,
                requisicao.identificadorDoBot(),
                requisicao.identificadorDoTelegram(),
                requisicao.identificadorDoChat(),
                requisicao.identificadorDoAgente(),
                requisicao.identificadorDaSessao());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(RespostaDeVinculoDoTelegram.de(vinculo));
    }

    @PostMapping("/operacoes/{identificador}/confirmacao")
    public ResponseEntity<RespostaDaConfirmacaoAssistida> confirmarEAplicar(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeConfirmacaoDaOperacao requisicao,
            HttpServletRequest pedido) {
        var resultado = aplicacao.confirmarComResultado(identificador,
                requisicao.codigo(), requisicao.metodo(),
                requisicao.identificadorDoBot(),
                requisicao.identificadorDoTelegram(),
                requisicao.identificadorDoChat(),
                requisicao.identificadorDaSessao(),
                requisicao.identificadorDoUpdate(), correlacao(pedido));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(RespostaDaConfirmacaoAssistida.de(resultado, mapeador));
    }

    @PostMapping("/operacoes/confirmacao")
    public ResponseEntity<RespostaDaConfirmacaoAssistida> confirmarEAplicar(
            @Valid @RequestBody RequisicaoDeConfirmacaoDaOperacao requisicao,
            HttpServletRequest pedido) {
        var resultado = aplicacao.confirmarComResultado(requisicao.codigo(),
                requisicao.metodo(), requisicao.identificadorDoBot(),
                requisicao.identificadorDoTelegram(),
                requisicao.identificadorDoChat(),
                requisicao.identificadorDaSessao(),
                requisicao.identificadorDoUpdate(), correlacao(pedido));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(RespostaDaConfirmacaoAssistida.de(resultado, mapeador));
    }

    private UUID correlacao(HttpServletRequest pedido) {
        Object valor = pedido.getAttribute("identificadorDeCorrelacao");
        try {
            return UUID.fromString(String.valueOf(valor));
        } catch (IllegalArgumentException excecao) {
            return UUID.randomUUID();
        }
    }
}
