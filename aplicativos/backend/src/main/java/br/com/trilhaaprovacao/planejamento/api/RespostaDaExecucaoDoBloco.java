package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.planejamento.aplicacao.ResultadoDaExecucaoDoBloco;

public record RespostaDaExecucaoDoBloco(
        RespostaDeBlocoDeEstudo bloco,
        RespostaDeExecucaoDoBloco execucao) {

    public static RespostaDaExecucaoDoBloco de(ResultadoDaExecucaoDoBloco resultado) {
        return new RespostaDaExecucaoDoBloco(
                RespostaDeBlocoDeEstudo.de(resultado.bloco()),
                RespostaDeExecucaoDoBloco.de(resultado.execucao()));
    }
}
