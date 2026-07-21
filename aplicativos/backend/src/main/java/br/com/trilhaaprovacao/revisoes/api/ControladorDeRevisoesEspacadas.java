package br.com.trilhaaprovacao.revisoes.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.compartilhado.api.RespostaDeErro;
import br.com.trilhaaprovacao.revisoes.aplicacao.ConsultaDeRevisoesEspacadas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/revisoes-espacadas")
@Tag(name = "Revisões espaçadas")
public class ControladorDeRevisoesEspacadas {
    private final ConsultaDeRevisoesEspacadas consulta;
    private final IdentidadeDoUsuarioAtual usuarioAtual;

    public ControladorDeRevisoesEspacadas(
            ConsultaDeRevisoesEspacadas consulta,
            IdentidadeDoUsuarioAtual usuarioAtual) {
        this.consulta = consulta;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping
    @Operation(
            summary = "Consulta a agenda deterministica de revisoes espacadas",
            description = "Projeta as revisoes dos topicos exigidos usando somente evidencias "
                    + "ativas ate a data de referencia, sem persistir alteracoes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agenda de revisoes calculada.",
                    content = @Content(schema = @Schema(
                            implementation = RespostaDeRevisoesEspacadas.class))),
            @ApiResponse(responseCode = "400", description = "Datas invalidas ou ausentes.",
                    content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
            @ApiResponse(responseCode = "401", description = "Sessao ausente ou expirada.",
                    content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
            @ApiResponse(responseCode = "403", description = "Acesso recusado.",
                    content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
            @ApiResponse(responseCode = "404",
                    description = "Usuario da sessao nao encontrado.",
                    content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
            @ApiResponse(responseCode = "409", description = "Conflito de estado.",
                    content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
            @ApiResponse(responseCode = "422",
                    description = "Contexto oficial incompleto ou periodo invalido.",
                    content = @Content(schema = @Schema(implementation = RespostaDeErro.class)))
    })
    public RespostaDeRevisoesEspacadas consultar(
            @RequestParam @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDeReferencia,
            @RequestParam @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            Authentication autenticacao) {
        UUID usuario = usuarioAtual.obter(autenticacao);
        return RespostaDeRevisoesEspacadas.de(
                consulta.consultar(usuario, dataDeReferencia, ate));
    }
}
