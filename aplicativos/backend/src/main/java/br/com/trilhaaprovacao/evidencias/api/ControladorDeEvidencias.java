package br.com.trilhaaprovacao.evidencias.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeMaterias;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeTopicos;
import br.com.trilhaaprovacao.evidencias.aplicacao.ConsultaDeDiagnosticoDeTopicos;
import br.com.trilhaaprovacao.evidencias.aplicacao.DiagnosticoDeTopico;
import br.com.trilhaaprovacao.evidencias.aplicacao.ServicoDeEvidenciasDeAprendizagem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
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
@RequestMapping("/api/v1/evidencias")
@Tag(name = "Evidências")
public class ControladorDeEvidencias {
    private final ServicoDeEvidenciasDeAprendizagem evidencias;
    private final ConsultaDeDiagnosticoDeTopicos diagnostico;
    private final ServicoDeMaterias materias;
    private final ServicoDeTopicos topicos;
    private final IdentidadeDoUsuarioAtual usuarioAtual;

    public ControladorDeEvidencias(ServicoDeEvidenciasDeAprendizagem evidencias,
            ConsultaDeDiagnosticoDeTopicos diagnostico, ServicoDeMaterias materias,
            ServicoDeTopicos topicos,
            IdentidadeDoUsuarioAtual usuarioAtual) {
        this.evidencias = evidencias;
        this.diagnostico = diagnostico;
        this.materias = materias;
        this.topicos = topicos;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping("/padroes-de-erro")
    @Operation(summary = "Sugere padrões de erro já usados pelo usuário no tópico")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sugestões do tópico."),
            @ApiResponse(responseCode = "400", description = "Parâmetros inválidos."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada."),
            @ApiResponse(responseCode = "403", description = "Acesso recusado."),
            @ApiResponse(responseCode = "404", description = "Tópico não encontrado."),
            @ApiResponse(responseCode = "409", description = "Conflito de estado."),
            @ApiResponse(responseCode = "422", description = "Regra de negócio inválida.")
    })
    public List<String> sugerirPadroes(
            @RequestParam UUID identificadorDoTopico,
            @RequestParam(defaultValue = "") String pesquisa,
            Authentication autenticacao) {
        UUID usuario = usuarioAtual.obter(autenticacao);
        topicos.obter(usuario, identificadorDoTopico);
        return evidencias.sugerirPadroes(usuario, identificadorDoTopico, pesquisa);
    }

    @GetMapping("/diagnostico-de-topicos")
    @Operation(summary = "Diagnostica evidências por tópico em janela civil inclusiva de 30 dias")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Diagnóstico objetivo por tópico."),
            @ApiResponse(responseCode = "400", description = "Data ou filtro inválido."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada."),
            @ApiResponse(responseCode = "403", description = "Acesso recusado."),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado."),
            @ApiResponse(responseCode = "409", description = "Conflito de estado."),
            @ApiResponse(responseCode = "422", description = "Regra de negócio inválida.")
    })
    public List<DiagnosticoDeTopico> diagnosticar(
            @RequestParam @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDeReferencia,
            @RequestParam(required = false) UUID identificadorDaMateria,
            @RequestParam(defaultValue = "false") boolean somenteExigidosNoConcursoAtivo,
            Authentication autenticacao) {
        UUID usuario = usuarioAtual.obter(autenticacao);
        if (identificadorDaMateria != null) {
            materias.obter(usuario, identificadorDaMateria);
        }
        return diagnostico.consultar(usuario, dataDeReferencia,
                identificadorDaMateria, somenteExigidosNoConcursoAtivo);
    }
}
