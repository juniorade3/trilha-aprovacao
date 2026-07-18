package br.com.trilhaaprovacao.compartilhado.api;

public class RegraDeDominio extends RuntimeException {
    private final String codigo;

    public RegraDeDominio(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
