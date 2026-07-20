package br.com.trilhaaprovacao.planejamento.dominio;

public enum PrioridadeDaMateriaNoPlano {
    ALTA(3),
    NORMAL(2),
    BAIXA(1),
    NAO_INCLUIR(0);

    private final int peso;

    PrioridadeDaMateriaNoPlano(int peso) {
        this.peso = peso;
    }

    public int peso() {
        return peso;
    }
}
