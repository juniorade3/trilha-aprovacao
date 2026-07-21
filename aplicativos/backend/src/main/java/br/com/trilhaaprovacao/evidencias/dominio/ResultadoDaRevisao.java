package br.com.trilhaaprovacao.evidencias.dominio;

public enum ResultadoDaRevisao {
    PRECISA_REFORCO,
    PARCIAL,
    CONSOLIDADA;

    public static ResultadoDaRevisao classificar(int nivelDeRecordacao) {
        if (nivelDeRecordacao < 1 || nivelDeRecordacao > 5) {
            throw new IllegalArgumentException("Nivel de recordacao deve estar entre 1 e 5.");
        }
        if (nivelDeRecordacao <= 2) {
            return PRECISA_REFORCO;
        }
        return nivelDeRecordacao == 3 ? PARCIAL : CONSOLIDADA;
    }
}
