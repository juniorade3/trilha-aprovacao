package br.com.trilhaaprovacao.automacao.api;

import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeAplicacaoDeOperacoesAssistidas.ResultadoDaConfirmacao;
import tools.jackson.databind.ObjectMapper;

public record RespostaDaConfirmacaoAssistida(
        RespostaDeOperacaoAssistida operacao,
        boolean exigeNovaConfirmacao,
        String proximoCodigo,
        String proximaFrase) {

    public static RespostaDaConfirmacaoAssistida de(
            ResultadoDaConfirmacao resultado, ObjectMapper mapeador) {
        String codigo = resultado.proximoCodigo();
        return new RespostaDaConfirmacaoAssistida(
                RespostaDeOperacaoAssistida.de(resultado.operacao(), mapeador),
                resultado.exigeNovaConfirmacao(), codigo,
                codigo == null ? null : "/confirmar " + codigo);
    }
}
