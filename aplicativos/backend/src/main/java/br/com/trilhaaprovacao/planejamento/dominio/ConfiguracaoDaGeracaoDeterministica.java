package br.com.trilhaaprovacao.planejamento.dominio;

public record ConfiguracaoDaGeracaoDeterministica(
        int duracaoPadraoDoBlocoPrincipalEmMinutos,
        int duracaoDoBlocoDeRevisaoEmMinutos) {

    public ConfiguracaoDaGeracaoDeterministica {
        if (duracaoPadraoDoBlocoPrincipalEmMinutos < 25
                || duracaoPadraoDoBlocoPrincipalEmMinutos > 180) {
            throw new IllegalArgumentException(
                    "A duracao principal deve estar entre 25 e 180 minutos.");
        }
        if (duracaoDoBlocoDeRevisaoEmMinutos < 0
                || duracaoDoBlocoDeRevisaoEmMinutos > 120) {
            throw new IllegalArgumentException(
                    "A duracao da revisao deve estar entre 0 e 120 minutos.");
        }
    }
}
