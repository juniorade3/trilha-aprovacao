package br.com.trilhaaprovacao.evidencias.dominio;

public record DadosDoPadraoDeErro(String descricao, int quantidadeDeOcorrencias) {
    public DadosDoPadraoDeErro {
        descricao = NormalizacaoDePadraoDeErro.limpar(descricao);
        if (quantidadeDeOcorrencias < 1) {
            throw new IllegalArgumentException("Quantidade de ocorrencias deve ser positiva.");
        }
    }

    public String descricaoNormalizada() {
        return NormalizacaoDePadraoDeErro.normalizar(descricao);
    }
}
