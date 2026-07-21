package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.evidencias.api.RespostaDeEvidencia;
import br.com.trilhaaprovacao.planejamento.aplicacao.ResultadoDaExecucaoDoBloco;

public record RespostaDaExecucaoDoBloco(
        RespostaDeBlocoDeEstudo bloco,
        RespostaDeExecucaoDoBloco execucao,
        RespostaResumidaDoRegistroDeEstudo estudo,
        RespostaDeEvidencia evidencia) {
    public static RespostaDaExecucaoDoBloco de(ResultadoDaExecucaoDoBloco resultado) {
        return new RespostaDaExecucaoDoBloco(
                RespostaDeBlocoDeEstudo.de(resultado.bloco()),
                RespostaDeExecucaoDoBloco.de(resultado.execucao()),
                RespostaResumidaDoRegistroDeEstudo.de(resultado.estudo()),
                RespostaDeEvidencia.de(resultado.evidencia()));
    }
}
