package br.com.trilhaaprovacao.priorizacao.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.compartilhado.api.RespostaDeErro;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeMaterias;
import br.com.trilhaaprovacao.priorizacao.aplicacao.ConsultaDePriorizacaoDeTopicos;
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
@RequestMapping("/api/v1/priorizacao-de-topicos")
@Tag(name = "Priorização de tópicos")
public class ControladorDePriorizacaoDeTopicos {
    private final ConsultaDePriorizacaoDeTopicos consulta;
    private final ServicoDeMaterias materias;
    private final IdentidadeDoUsuarioAtual usuarioAtual;

    public ControladorDePriorizacaoDeTopicos(
            ConsultaDePriorizacaoDeTopicos consulta,
            ServicoDeMaterias materias,
            IdentidadeDoUsuarioAtual usuarioAtual) {
        this.consulta = consulta;
        this.materias = materias;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping
    @Operation(
            summary = "Prioriza deterministicamente os topicos do contexto oficial",
            description = "Classifica lacunas, fraquezas e topicos consolidados sem persistir "
                    + "alteracoes, usando fatos ativos ate a data de referencia.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Ranking consultivo dos topicos exigidos.",
                    content = @Content(schema = @Schema(
                            implementation = RespostaDePriorizacaoDeTopicos.class))),
            @ApiResponse(responseCode = "400", description = "Data ou filtro invalido.",
                    content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
            @ApiResponse(responseCode = "401", description = "Sessao ausente ou expirada.",
                    content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
            @ApiResponse(responseCode = "403", description = "Acesso recusado.",
                    content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
            @ApiResponse(responseCode = "404",
                    description = "Materia nao encontrada para o usuario da sessao.",
                    content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
            @ApiResponse(responseCode = "409", description = "Conflito de estado.",
                    content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
            @ApiResponse(responseCode = "422", description = "Contexto oficial incompleto.",
                    content = @Content(schema = @Schema(implementation = RespostaDeErro.class)))
    })
    public RespostaDePriorizacaoDeTopicos consultar(
            @RequestParam @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDeReferencia,
            @RequestParam(required = false) UUID identificadorDaMateria,
            Authentication autenticacao) {
        UUID usuario = usuarioAtual.obter(autenticacao);
        if (identificadorDaMateria != null) {
            materias.obter(usuario, identificadorDaMateria);
        }
        return RespostaDePriorizacaoDeTopicos.de(
                consulta.consultar(usuario, dataDeReferencia, identificadorDaMateria));
    }
}
