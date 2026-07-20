package br.com.trilhaaprovacao.planejamento.aplicacao;

public record ResultadoDaAplicacaoDaGeracao(
        ResultadoDoPlanoSemanal plano,
        int quantidadeDeBlocosCriados,
        int quantidadeDeBlocosSubstituidos,
        int quantidadeDeBlocosPreservados) {
}
