package br.com.trilhaaprovacao.priorizacao.dominio;

public enum GrupoDePriorizacao {
    LACUNA(0),
    FRAQUEZA(1),
    CONSOLIDADO(2);

    private final int ordem;

    GrupoDePriorizacao(int ordem) {
        this.ordem = ordem;
    }

    public int ordem() {
        return ordem;
    }
}
