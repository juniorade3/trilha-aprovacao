package br.com.trilhaaprovacao.automacao.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeOperacoesAssistidas;
import br.com.trilhaaprovacao.compartilhado.api.RespostaDeErro;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@Validated
@RestController
@RequestMapping("/api/v1/operacoes-assistidas")
@Tag(name = "Automação assistida")
@ConditionalOnProperty(prefix = "trilha.automacao", name = "habilitada",
        havingValue = "true")
public class ControladorDeOperacoesAssistidas {
    private final ServicoDeOperacoesAssistidas servico;
    private final IdentidadeDoUsuarioAtual usuarioAtual;
    private final ObjectMapper mapeador;

    public ControladorDeOperacoesAssistidas(ServicoDeOperacoesAssistidas servico,
            IdentidadeDoUsuarioAtual usuarioAtual, ObjectMapper mapeador) {
        this.servico = servico;
        this.usuarioAtual = usuarioAtual;
        this.mapeador = mapeador;
    }

    @GetMapping
    @Operation(summary = "Lista as operacoes assistidas",
            description = "Lista somente operacoes da conta da sessao, da mais recente "
                    + "para a mais antiga.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operacoes listadas.",
                content = @Content(schema = @Schema(
                        implementation = RespostaPaginadaDeOperacoesAssistidas.class))),
        @ApiResponse(responseCode = "400", description = "Paginacao invalida.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class)))
    })
    public RespostaPaginadaDeOperacoesAssistidas listar(
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            Authentication autenticacao) {
        var resultado = servico.listar(
                usuarioAtual.obter(autenticacao), pagina, tamanho);
        return new RespostaPaginadaDeOperacoesAssistidas(
                resultado.getContent().stream()
                .map(RespostaResumidaDeOperacaoAssistida::de).toList(),
                resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
    }

    @GetMapping("/{identificador}")
    @Operation(summary = "Consulta uma operacao assistida",
            description = "Retorna proposta, assinatura, versoes e resultado sem expor "
                    + "segredos de confirmacao.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operacao encontrada.",
                content = @Content(schema = @Schema(
                        implementation = RespostaDeOperacaoAssistida.class))),
        @ApiResponse(responseCode = "400", description = "Identificador invalido.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "404", description = "Operacao nao encontrada.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class)))
    })
    public RespostaDeOperacaoAssistida obter(@PathVariable UUID identificador,
            Authentication autenticacao) {
        return RespostaDeOperacaoAssistida.de(servico.obter(
                usuarioAtual.obter(autenticacao), identificador), mapeador);
    }
}
