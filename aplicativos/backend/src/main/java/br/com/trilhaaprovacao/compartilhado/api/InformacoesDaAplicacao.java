package br.com.trilhaaprovacao.compartilhado.api;

public record InformacoesDaAplicacao(String nome, String versao) {

    public static InformacoesDaAplicacao daFundacao() {
        return new InformacoesDaAplicacao("Trilha da Aprovacao", "v1");
    }
}
