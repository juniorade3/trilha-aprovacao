package br.com.trilhaaprovacao.importacaoedital.aplicacao;

public record ResultadoDaInterpretacaoAssistidaDoEdital(
        ArvoreInterpretadaDoEdital arvore,
        UsoDaInterpretacaoAssistida uso) {

    public ResultadoDaInterpretacaoAssistidaDoEdital {
        if (arvore == null) {
            throw new IllegalArgumentException(
                    "Arvore interpretada e obrigatoria.");
        }
        uso = uso == null ? new UsoDaInterpretacaoAssistida(0, 0, 0) : uso;
    }

    public record UsoDaInterpretacaoAssistida(
            long tokensDeEntrada,
            long tokensDeSaida,
            long totalDeTokens) {

        public UsoDaInterpretacaoAssistida {
            if (tokensDeEntrada < 0 || tokensDeSaida < 0
                    || totalDeTokens < 0) {
                throw new IllegalArgumentException(
                        "Uso de tokens nao pode ser negativo.");
            }
        }
    }
}
