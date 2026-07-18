package br.com.trilhaaprovacao.compartilhado.api;

public class RecursoNaoEncontrado extends RuntimeException {
    private final String codigo;

    public RecursoNaoEncontrado(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
