package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.compartilhado.api.RespostaDeErro;
import br.com.trilhaaprovacao.planejamento.aplicacao.DisponibilidadeInformada;
import br.com.trilhaaprovacao.planejamento.aplicacao.PrioridadeDeMateriaInformada;
import br.com.trilhaaprovacao.planejamento.aplicacao.ServicoDeGeracaoDeterministica;
import br.com.trilhaaprovacao.planejamento.aplicacao.ServicoDePlanejamento;
import br.com.trilhaaprovacao.planejamento.aplicacao.ServicoDeReplanejamento;
import br.com.trilhaaprovacao.planejamento.aplicacao.ResultadoDaPreviaDoReplanejamento;
import br.com.trilhaaprovacao.planejamento.dominio.ConfiguracaoDaGeracaoDeterministica;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/planos-semanais")
@Tag(name = "Planejamento")
public class ControladorDePlanosSemanais {
    private final ServicoDePlanejamento servico;
    private final ServicoDeGeracaoDeterministica geracao;
    private final IdentidadeDoUsuarioAtual usuarioAtual;
    private final ServicoDeReplanejamento replanejamento;

    public ControladorDePlanosSemanais(ServicoDePlanejamento servico,
            ServicoDeGeracaoDeterministica geracao,
            IdentidadeDoUsuarioAtual usuarioAtual,
            ServicoDeReplanejamento replanejamento) {
        this.servico = servico;
        this.geracao = geracao;
        this.usuarioAtual = usuarioAtual;
        this.replanejamento = replanejamento;
    }

    @PostMapping
    public ResponseEntity<RespostaDePlanoSemanal> criar(
            @Valid @RequestBody RequisicaoDePlanoSemanal requisicao,
            Authentication autenticacao) {
        var resultado = servico.criarPlanoSemanal(
                usuarioAtual.obter(autenticacao), requisicao.dataInicial());
        return ResponseEntity.created(URI.create(
                "/api/v1/planos-semanais/" + resultado.plano().identificador()))
                .body(RespostaDePlanoSemanal.de(resultado));
    }

    @GetMapping
    public RespostaDePlanoSemanal obter(@RequestParam LocalDate dataInicial,
            Authentication autenticacao) {
        return RespostaDePlanoSemanal.de(servico.obterPlanoSemanal(
                usuarioAtual.obter(autenticacao), dataInicial));
    }

    @PutMapping("/{identificador}/disponibilidades")
    public RespostaDePlanoSemanal alterarDisponibilidades(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeAlteracaoDasDisponibilidades requisicao,
            Authentication autenticacao) {
        var informadas = requisicao.disponibilidades().stream()
                .map(item -> new DisponibilidadeInformada(
                        item.data(), item.minutosDisponiveis()))
                .toList();
        return RespostaDePlanoSemanal.de(servico.alterarDisponibilidades(
                usuarioAtual.obter(autenticacao), identificador, informadas));
    }

    @GetMapping("/{identificador}/materias-para-geracao")
    @Operation(
            summary = "Lista as materias elegiveis para a geracao",
            description = "Retorna as materias pessoais ativas do cargo selecionado no "
                    + "concurso ativo, em ordem estavel, com as prioridades atuais do plano.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Materias elegiveis listadas.",
                content = @Content(schema = @Schema(
                        implementation = RespostaDeMateriasParaGeracao.class))),
        @ApiResponse(responseCode = "404", description = "Plano semanal nao encontrado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "422",
                description = "Concurso ativo, cargo selecionado ou materias elegiveis ausentes.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class)))
    })
    public RespostaDeMateriasParaGeracao listarMateriasParaGeracao(
            @PathVariable UUID identificador, Authentication autenticacao) {
        return RespostaDeMateriasParaGeracao.de(geracao.listarMaterias(
                usuarioAtual.obter(autenticacao), identificador));
    }

    @PutMapping("/{identificador}/prioridades-de-materias")
    @Operation(
            summary = "Substitui as prioridades das materias",
            description = "Substitui as prioridades do plano em rascunho. A requisicao deve "
                    + "conter cada materia elegivel uma unica vez.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Prioridades substituidas.",
                content = @Content(schema = @Schema(
                        implementation = RespostaDeMateriasParaGeracao.class))),
        @ApiResponse(responseCode = "400", description = "Requisicao invalida.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "404", description = "Plano semanal nao encontrado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "409", description = "Plano nao esta em rascunho.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "422",
                description = "Elegibilidade ou conjunto de prioridades invalido.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class)))
    })
    public RespostaDeMateriasParaGeracao alterarPrioridades(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeAlteracaoDasPrioridades requisicao,
            Authentication autenticacao) {
        var prioridades = requisicao.prioridades().stream()
                .map(item -> new PrioridadeDeMateriaInformada(
                        item.identificadorDaMateria(), item.prioridade()))
                .toList();
        return RespostaDeMateriasParaGeracao.de(geracao.substituirPrioridades(
                usuarioAtual.obter(autenticacao), identificador, prioridades));
    }

    @PostMapping("/{identificador}/geracao-deterministica/previa")
    @Operation(
            summary = "Gera a previa deterministica da semana",
            description = "Calcula a previa com a configuracao informada sem persistir novos "
                    + "blocos no plano.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Previa calculada.",
                content = @Content(schema = @Schema(
                        implementation = RespostaDaPreviaDaGeracao.class))),
        @ApiResponse(responseCode = "400", description = "Requisicao invalida.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "404", description = "Plano semanal nao encontrado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "409", description = "Plano nao esta em rascunho.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "422",
                description = "Elegibilidade ou configuracao da geracao invalida.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class)))
    })
    public RespostaDaPreviaDaGeracao gerarPrevia(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDePreviaDaGeracao requisicao,
            Authentication autenticacao) {
        var configuracao = new ConfiguracaoDaGeracaoDeterministica(
                requisicao.duracaoPadraoDoBlocoPrincipalEmMinutos(),
                requisicao.duracaoDoBlocoDeRevisaoEmMinutos());
        return RespostaDaPreviaDaGeracao.de(geracao.gerarPrevia(
                usuarioAtual.obter(autenticacao), identificador, configuracao));
    }

    @PostMapping("/{identificador}/geracao-deterministica")
    @Operation(
            summary = "Aplica a geracao deterministica ao plano",
            description = "Persiste os blocos sugeridos e, quando confirmado, substitui somente "
                    + "blocos gerados sem ajuste manual.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Geracao aplicada.",
                content = @Content(schema = @Schema(
                        implementation = RespostaDaAplicacaoDaGeracao.class))),
        @ApiResponse(responseCode = "400", description = "Requisicao invalida.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "404", description = "Plano semanal nao encontrado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "409",
                description = "Plano nao esta em rascunho ou exige confirmacao para regenerar.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "422",
                description = "Elegibilidade ou configuracao da geracao invalida.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class)))
    })
    public RespostaDaAplicacaoDaGeracao aplicarGeracao(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeAplicacaoDaGeracao requisicao,
            Authentication autenticacao) {
        var configuracao = new ConfiguracaoDaGeracaoDeterministica(
                requisicao.duracaoPadraoDoBlocoPrincipalEmMinutos(),
                requisicao.duracaoDoBlocoDeRevisaoEmMinutos());
        return RespostaDaAplicacaoDaGeracao.de(geracao.aplicar(
                usuarioAtual.obter(autenticacao), identificador, configuracao,
                requisicao.substituirBlocosGerados()));
    }

    @PostMapping("/{identificador}/replanejamento/previa")
    @Operation(summary = "Calcula a previa do replanejamento",
            description = "Calcula deterministicamente as transferencias ate domingo sem escrita.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Previa calculada."),
        @ApiResponse(responseCode = "400", description = "Requisicao invalida.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "401", description = "Sessao ausente ou expirada.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "403", description = "Acesso negado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "404", description = "Plano nao encontrado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "409", description = "Plano nao esta ativo.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "422", description = "Data ou regra invalida.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class)))
    })
    public ResultadoDaPreviaDoReplanejamento gerarPreviaDoReplanejamento(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDaPreviaDoReplanejamento requisicao,
            Authentication autenticacao) {
        return replanejamento.gerarPrevia(usuarioAtual.obter(autenticacao), identificador,
                requisicao.dataDeReferencia(),
                requisicao.identificadoresDasPendenciasIgnoradas());
    }

    @PostMapping("/{identificador}/replanejamento")
    @Operation(summary = "Aplica o replanejamento",
            description = "Bloqueia, recalcula, confere a assinatura e aplica atomicamente.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Replanejamento aplicado."),
        @ApiResponse(responseCode = "400", description = "Requisicao invalida.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "401", description = "Sessao ausente ou expirada.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "403", description = "Acesso negado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "404", description = "Plano nao encontrado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "409",
                description = "Plano inativo ou previa de replanejamento desatualizada.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "422",
                description = "Confirmacao ausente ou nenhuma transferencia aplicavel.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class)))
    })
    public RespostaDaAplicacaoDoReplanejamento aplicarReplanejamento(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeAplicacaoDoReplanejamento requisicao,
            Authentication autenticacao) {
        return RespostaDaAplicacaoDoReplanejamento.de(replanejamento.aplicar(
                usuarioAtual.obter(autenticacao), identificador,
                requisicao.dataDeReferencia(),
                requisicao.identificadoresDasPendenciasIgnoradas(),
                requisicao.identificadoresDasConfirmacoesDoLimite(),
                requisicao.assinaturaDaPrevia()));
    }

    @GetMapping("/{identificador}/historico-semanal")
    @Operation(summary = "Consulta o historico objetivo da semana",
            description = "Retorna snapshot, execucoes, cancelamentos, transferencias e resumo.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Historico consultado."),
        @ApiResponse(responseCode = "400", description = "Parametro invalido.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "401", description = "Sessao ausente ou expirada.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "403", description = "Acesso negado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "404", description = "Plano nao encontrado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "409", description = "Conflito de estado.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class))),
        @ApiResponse(responseCode = "422", description = "Data fora da semana.",
                content = @Content(schema = @Schema(implementation = RespostaDeErro.class)))
    })
    public RespostaDoHistoricoSemanal obterHistoricoSemanal(
            @PathVariable UUID identificador,
            @RequestParam LocalDate dataDeReferencia,
            Authentication autenticacao) {
        return RespostaDoHistoricoSemanal.de(replanejamento.obterHistorico(
                usuarioAtual.obter(autenticacao), identificador, dataDeReferencia));
    }

    @PostMapping("/{identificador}/encerramento")
    public RespostaDePlanoSemanal encerrar(@PathVariable UUID identificador,
            Authentication autenticacao) {
        return RespostaDePlanoSemanal.de(servico.encerrarPlano(
                usuarioAtual.obter(autenticacao), identificador));
    }

    @PostMapping("/{identificador}/cancelamento")
    public RespostaDePlanoSemanal cancelar(@PathVariable UUID identificador,
            Authentication autenticacao) {
        return RespostaDePlanoSemanal.de(servico.cancelarPlano(
                usuarioAtual.obter(autenticacao), identificador));
    }

    @PostMapping("/{identificador}/ativacao")
    public RespostaDePlanoSemanal ativar(@PathVariable UUID identificador,
            Authentication autenticacao) {
        return RespostaDePlanoSemanal.de(servico.ativarPlanoSemanal(
                usuarioAtual.obter(autenticacao), identificador));
    }
}
