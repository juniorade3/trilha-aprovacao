package br.com.trilhaaprovacao.importacaoedital.dominio;

import java.math.BigDecimal;

public record ValorExtraido<T>(
        T valor,
        BigDecimal confianca,
        ProvenienciaDoDado fonte,
        boolean inferido) {

    public ValorExtraido {
        if (confianca == null || confianca.compareTo(BigDecimal.ZERO) < 0
                || confianca.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Confianca deve ficar entre zero e um.");
        }
        if (valor instanceof String texto && texto.isBlank()) {
            valor = null;
        }
    }

    public static <T> ValorExtraido<T> explicito(T valor,
            ProvenienciaDoDado fonte) {
        return new ValorExtraido<>(valor, new BigDecimal("0.9900"), fonte, false);
    }
}
