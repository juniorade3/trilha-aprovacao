package br.com.trilhaaprovacao.automacao.api;

import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeVinculosDoTelegram;
import br.com.trilhaaprovacao.automacao.infraestrutura.AutenticadorDoGatewayDaAutomacao;
import br.com.trilhaaprovacao.compartilhado.api.RespostaDeErro;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/v1/integracoes-confiaveis/telegram")
@ConditionalOnProperty(prefix = "trilha.automacao", name = "habilitada",
        havingValue = "true")
public class ControladorConfiavelDeVinculosDoTelegram {
    private final ServicoDeVinculosDoTelegram servico;
    private final AutenticadorDoGatewayDaAutomacao autenticador;

    public ControladorConfiavelDeVinculosDoTelegram(
            ServicoDeVinculosDoTelegram servico,
            AutenticadorDoGatewayDaAutomacao autenticador) {
        this.servico = servico;
        this.autenticador = autenticador;
    }

    @PostMapping("/vinculos")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vinculo ativado."),
        @ApiResponse(responseCode = "403", description = "Gateway nao autorizado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "404", description = "Codigo nao encontrado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "409", description = "Telegram ja vinculado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "422", description = "Codigo expirado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class)))
    })
    public ResponseEntity<RespostaDaTrocaDoCodigoDeVinculo> trocar(
            @RequestHeader(value = "X-Chave-Do-Gateway", required = false) String chave,
            @Valid @RequestBody RequisicaoDeTrocaDoCodigoDeVinculo requisicao) {
        autenticador.autenticar(chave);
        var troca = servico.trocarCodigo(requisicao.codigo(),
                requisicao.identificadorDoBot(), requisicao.identificadorDoTelegram(),
                requisicao.identificadorDoChat());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(RespostaDaTrocaDoCodigoDeVinculo.de(troca));
    }
}
