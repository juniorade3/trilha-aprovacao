package br.com.trilhaaprovacao.automacao.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeVinculosDoTelegram;
import br.com.trilhaaprovacao.compartilhado.api.RespostaDeErro;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integracoes/telegram")
@Tag(name = "Automação assistida")
@ConditionalOnProperty(prefix = "trilha.automacao", name = "habilitada",
        havingValue = "true")
public class ControladorDeIntegracoesDoTelegram {
    private final ServicoDeVinculosDoTelegram servico;
    private final IdentidadeDoUsuarioAtual usuarioAtual;

    public ControladorDeIntegracoesDoTelegram(ServicoDeVinculosDoTelegram servico,
            IdentidadeDoUsuarioAtual usuarioAtual) {
        this.servico = servico;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping("/codigos-de-vinculo")
    @Operation(summary = "Gera um codigo de vinculo do Telegram",
            description = "Gera um codigo aleatorio, de uso unico e validade curta. "
                    + "Somente o hash e armazenado.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Codigo gerado.",
                content = @Content(schema = @Schema(
                        implementation = RespostaDeCodigoDeVinculo.class))),
        @ApiResponse(responseCode = "409", description = "Telegram ja vinculado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "422", description = "Integracao nao configurada.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class)))
    })
    public ResponseEntity<RespostaDeCodigoDeVinculo> gerarCodigo(
            Authentication autenticacao) {
        var resposta = RespostaDeCodigoDeVinculo.de(
                servico.gerarCodigo(usuarioAtual.obter(autenticacao)));
        return ResponseEntity.created(URI.create(
                "/api/v1/integracoes/telegram/vinculo")).body(resposta);
    }

    @GetMapping("/vinculo")
    @Operation(summary = "Consulta o vinculo atual do Telegram",
            description = "Retorna apenas o vinculo pendente ou ativo da conta da sessao.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vinculo encontrado.",
                content = @Content(schema = @Schema(
                        implementation = RespostaDeVinculoDoTelegram.class))),
        @ApiResponse(responseCode = "404", description = "Vinculo nao encontrado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class)))
    })
    public RespostaDeVinculoDoTelegram obter(Authentication autenticacao) {
        return RespostaDeVinculoDoTelegram.de(
                servico.obter(usuarioAtual.obter(autenticacao)));
    }

    @PostMapping("/vinculo/rotacoes")
    @Operation(summary = "Prepara a rotacao da credencial do Telegram",
            description = "Revoga o acesso atual e gera um novo codigo temporario. "
                    + "A nova credencial so e emitida depois que o codigo e enviado "
                    + "pela mesma conversa direta do Telegram.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Rotacao preparada.",
                content = @Content(schema = @Schema(
                        implementation = RespostaDeCodigoDeVinculo.class))),
        @ApiResponse(responseCode = "404", description = "Vinculo ativo nao encontrado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "422", description = "Integracao nao configurada.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class)))
    })
    public ResponseEntity<RespostaDeCodigoDeVinculo> rotacionar(
            Authentication autenticacao) {
        var resposta = RespostaDeCodigoDeVinculo.de(
                servico.rotacionar(usuarioAtual.obter(autenticacao)));
        return ResponseEntity.created(URI.create(
                "/api/v1/integracoes/telegram/vinculo")).body(resposta);
    }

    @DeleteMapping("/vinculo")
    @Operation(summary = "Revoga o vinculo do Telegram",
            description = "Revoga o vinculo e todas as credenciais ativas sem excluir historico.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Vinculo revogado."),
        @ApiResponse(responseCode = "404", description = "Vinculo nao encontrado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class)))
    })
    public ResponseEntity<Void> revogar(Authentication autenticacao) {
        servico.revogar(usuarioAtual.obter(autenticacao));
        return ResponseEntity.noContent().build();
    }

}
