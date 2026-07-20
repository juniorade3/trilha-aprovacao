package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.planejamento.aplicacao.DisponibilidadeInformada;
import br.com.trilhaaprovacao.planejamento.aplicacao.PrioridadeDeMateriaInformada;
import br.com.trilhaaprovacao.planejamento.aplicacao.ServicoDeGeracaoDeterministica;
import br.com.trilhaaprovacao.planejamento.aplicacao.ServicoDePlanejamento;
import br.com.trilhaaprovacao.planejamento.dominio.ConfiguracaoDaGeracaoDeterministica;
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

    public ControladorDePlanosSemanais(ServicoDePlanejamento servico,
            ServicoDeGeracaoDeterministica geracao,
            IdentidadeDoUsuarioAtual usuarioAtual) {
        this.servico = servico;
        this.geracao = geracao;
        this.usuarioAtual = usuarioAtual;
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
    public RespostaDeMateriasParaGeracao listarMateriasParaGeracao(
            @PathVariable UUID identificador, Authentication autenticacao) {
        return RespostaDeMateriasParaGeracao.de(geracao.listarMaterias(
                usuarioAtual.obter(autenticacao), identificador));
    }

    @PutMapping("/{identificador}/prioridades-de-materias")
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
