package br.com.trilhaaprovacao.importacaoedital.dominio;

public record ProblemaDaImportacao(
        SeveridadeDoProblemaDaImportacao severidade,
        String codigo,
        String mensagem,
        String caminho) {

    public ProblemaDaImportacao {
        if (severidade == null || codigo == null || codigo.isBlank()
                || mensagem == null || mensagem.isBlank()) {
            throw new IllegalArgumentException("Problema da importacao invalido.");
        }
        codigo = codigo.strip();
        mensagem = mensagem.strip();
        caminho = caminho == null || caminho.isBlank() ? null : caminho.strip();
    }
}
