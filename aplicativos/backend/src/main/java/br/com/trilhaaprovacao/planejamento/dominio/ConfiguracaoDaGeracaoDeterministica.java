package br.com.trilhaaprovacao.planejamento.dominio;

public record ConfiguracaoDaGeracaoDeterministica(
        int duracaoPadraoDoBlocoPrincipalEmMinutos,
        int quantidadeDeMateriasPorDia) {
    public static final int QUANTIDADE_PADRAO_DE_MATERIAS_POR_DIA = 3;
    public static final int QUANTIDADE_MAXIMA_DE_MATERIAS_POR_DIA = 20;

    public ConfiguracaoDaGeracaoDeterministica(
            int duracaoPadraoDoBlocoPrincipalEmMinutos) {
        this(duracaoPadraoDoBlocoPrincipalEmMinutos,
                QUANTIDADE_PADRAO_DE_MATERIAS_POR_DIA);
    }

    public ConfiguracaoDaGeracaoDeterministica {
        if (duracaoPadraoDoBlocoPrincipalEmMinutos < 25
                || duracaoPadraoDoBlocoPrincipalEmMinutos > 180) {
            throw new IllegalArgumentException(
                    "A duracao principal deve estar entre 25 e 180 minutos.");
        }
        if (quantidadeDeMateriasPorDia < 1
                || quantidadeDeMateriasPorDia > QUANTIDADE_MAXIMA_DE_MATERIAS_POR_DIA) {
            throw new IllegalArgumentException(
                    "A quantidade de materias por dia deve estar entre 1 e 20.");
        }
    }
}
