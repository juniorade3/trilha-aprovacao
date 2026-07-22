package br.com.trilhaaprovacao.planejamento.dominio;

public record ConfiguracaoDaGeracaoDeterministica(
        int duracaoPadraoDoBlocoPrincipalEmMinutos) {

    public ConfiguracaoDaGeracaoDeterministica {
        if (duracaoPadraoDoBlocoPrincipalEmMinutos < 25
                || duracaoPadraoDoBlocoPrincipalEmMinutos > 180) {
            throw new IllegalArgumentException(
                    "A duracao principal deve estar entre 25 e 180 minutos.");
        }
    }
}
