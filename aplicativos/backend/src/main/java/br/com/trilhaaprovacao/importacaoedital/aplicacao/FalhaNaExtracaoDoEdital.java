package br.com.trilhaaprovacao.importacaoedital.aplicacao;

public class FalhaNaExtracaoDoEdital extends RuntimeException {
    private final String codigo;

    public FalhaNaExtracaoDoEdital(String codigo, String mensagem) {
        super(mensagem);
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Codigo da falha obrigatorio.");
        }
        this.codigo = codigo.strip();
    }

    public FalhaNaExtracaoDoEdital(String codigo, String mensagem,
            Throwable causa) {
        super(mensagem, causa);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
