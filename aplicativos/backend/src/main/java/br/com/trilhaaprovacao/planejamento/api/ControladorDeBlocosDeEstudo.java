package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.planejamento.aplicacao.ServicoDePlanejamento;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Planejamento")
public class ControladorDeBlocosDeEstudo {
    private final ServicoDePlanejamento servico;
    private final IdentidadeDoUsuarioAtual usuarioAtual;

    public ControladorDeBlocosDeEstudo(ServicoDePlanejamento servico,
            IdentidadeDoUsuarioAtual usuarioAtual) {
        this.servico = servico;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping("/planos-semanais/{plano}/blocos")
    public ResponseEntity<RespostaDeBlocoDeEstudo> adicionar(
            @PathVariable UUID plano,
            @Valid @RequestBody RequisicaoDeBlocoDeEstudo requisicao,
            Authentication autenticacao) {
        var bloco = servico.adicionarBloco(usuarioAtual.obter(autenticacao),
                plano, requisicao.paraDados());
        return ResponseEntity.created(
                        URI.create("/api/v1/blocos-de-estudo/" + bloco.identificador()))
                .body(RespostaDeBlocoDeEstudo.de(bloco));
    }

    @PutMapping("/blocos-de-estudo/{identificador}")
    public RespostaDeBlocoDeEstudo alterar(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeBlocoDeEstudo requisicao,
            Authentication autenticacao) {
        return RespostaDeBlocoDeEstudo.de(servico.alterarBloco(
                usuarioAtual.obter(autenticacao), identificador,
                requisicao.paraDados()));
    }

    @PostMapping("/blocos-de-estudo/{identificador}/reagendamento")
    public RespostaDeBlocoDeEstudo reagendar(@PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeReagendamentoDoBloco requisicao,
            Authentication autenticacao) {
        return RespostaDeBlocoDeEstudo.de(servico.reagendarBloco(
                usuarioAtual.obter(autenticacao), identificador, requisicao.data(),
                requisicao.horarioPrevisto(), requisicao.ordem()));
    }

    @PostMapping("/blocos-de-estudo/{identificador}/cancelamento")
    public RespostaDeBlocoDeEstudo cancelar(@PathVariable UUID identificador,
            Authentication autenticacao) {
        return RespostaDeBlocoDeEstudo.de(servico.cancelarBloco(
                usuarioAtual.obter(autenticacao), identificador));
    }

    @DeleteMapping("/blocos-de-estudo/{identificador}")
    public ResponseEntity<Void> excluir(@PathVariable UUID identificador,
            Authentication autenticacao) {
        servico.excluirBloco(usuarioAtual.obter(autenticacao), identificador);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/planos-semanais/{plano}/ordem-dos-blocos")
    public RespostaDePlanoSemanal reordenar(
            @PathVariable UUID plano,
            @Valid @RequestBody RequisicaoDeOrdenacaoDosBlocos requisicao,
            Authentication autenticacao) {
        return RespostaDePlanoSemanal.de(servico.reordenarBlocos(
                usuarioAtual.obter(autenticacao), plano, requisicao.data(),
                requisicao.identificadoresOrdenados()));
    }

    @PostMapping("/blocos-de-estudo/{identificador}/inicio")
    public RespostaDaExecucaoDoBloco iniciar(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeInicioDaExecucao requisicao,
            Authentication autenticacao) {
        return RespostaDaExecucaoDoBloco.de(servico.iniciarBloco(
                usuarioAtual.obter(autenticacao), identificador,
                requisicao.dataDeReferencia()));
    }


    @GetMapping("/blocos-de-estudo/{identificador}/execucao")
    public RespostaDaExecucaoDoBloco obterExecucao(
            @PathVariable UUID identificador, Authentication autenticacao) {
        return RespostaDaExecucaoDoBloco.de(servico.obterExecucaoDoBloco(
                usuarioAtual.obter(autenticacao), identificador));
    }

    @GetMapping("/blocos-de-estudo/{identificador}/topicos-para-registro")
    public List<RespostaDeTopicoParaRegistro> listarTopicosParaRegistro(
            @PathVariable UUID identificador, Authentication autenticacao) {
        return servico.listarTopicosParaRegistro(
                usuarioAtual.obter(autenticacao), identificador).stream()
                .map(RespostaDeTopicoParaRegistro::de).toList();
    }


    @PostMapping("/blocos-de-estudo/{identificador}/conclusao")
    public RespostaDaExecucaoDoBloco concluir(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeFinalizacaoDaExecucao requisicao,
            Authentication autenticacao) {
        return RespostaDaExecucaoDoBloco.de(servico.concluirBloco(
                usuarioAtual.obter(autenticacao), identificador,
                requisicao.duracaoExecutadaEmMinutos(), requisicao.observacao(),
                requisicao.identificadorDoTopico()));
    }

    @PostMapping("/blocos-de-estudo/{identificador}/interrupcao")
    public RespostaDaExecucaoDoBloco interromper(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeFinalizacaoDaExecucao requisicao,
            Authentication autenticacao) {
        return RespostaDaExecucaoDoBloco.de(servico.interromperBloco(
                usuarioAtual.obter(autenticacao), identificador,
                requisicao.duracaoExecutadaEmMinutos(), requisicao.observacao(),
                requisicao.identificadorDoTopico()));
    }

    @PutMapping("/execucoes-de-bloco/{identificador}/correcao")
    public RespostaDaExecucaoDoBloco corrigirExecucao(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeCorrecaoDaExecucao requisicao,
            Authentication autenticacao) {
        return RespostaDaExecucaoDoBloco.de(servico.corrigirExecucao(
                usuarioAtual.obter(autenticacao), identificador, requisicao.resultado(),
                requisicao.duracaoExecutadaEmMinutos(), requisicao.observacao()));
    }

    @PostMapping("/execucoes-de-bloco/{identificador}/registro-de-estudo")
    public RespostaDaExecucaoDoBloco registrarNoHistorico(
            @PathVariable UUID identificador,
            @RequestBody(required = false) RequisicaoDeRegistroNoHistorico requisicao,
            Authentication autenticacao) {
        UUID topico = requisicao == null ? null : requisicao.identificadorDoTopico();
        return RespostaDaExecucaoDoBloco.de(servico.registrarExecucaoNoHistorico(
                usuarioAtual.obter(autenticacao), identificador, topico));
    }

}