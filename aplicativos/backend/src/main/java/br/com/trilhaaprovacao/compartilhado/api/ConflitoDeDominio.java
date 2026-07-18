package br.com.trilhaaprovacao.compartilhado.api;

public class ConflitoDeDominio extends RuntimeException {
    private final String codigo;

    public ConflitoDeDominio(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String codigo() { return codigo; }
}
