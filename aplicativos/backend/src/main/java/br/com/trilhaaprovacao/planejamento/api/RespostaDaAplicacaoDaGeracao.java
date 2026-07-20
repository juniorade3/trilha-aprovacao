package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.planejamento.aplicacao.ResultadoDaAplicacaoDaGeracao;

public record RespostaDaAplicacaoDaGeracao(
        RespostaDePlanoSemanal plano,
        Resumo resumo) {

    public static RespostaDaAplicacaoDaGeracao de(ResultadoDaAplicacaoDaGeracao resultado) {
        return new RespostaDaAplicacaoDaGeracao(
                RespostaDePlanoSemanal.de(resultado.plano()),
                new Resumo(resultado.quantidadeDeBlocosCriados(),
                        resultado.quantidadeDeBlocosSubstituidos(),
                        resultado.quantidadeDeBlocosPreservados()));
    }

    public record Resumo(
            int quantidadeDeBlocosCriados,
            int quantidadeDeBlocosSubstituidos,
            int quantidadeDeBlocosPreservados) {
    }
}
