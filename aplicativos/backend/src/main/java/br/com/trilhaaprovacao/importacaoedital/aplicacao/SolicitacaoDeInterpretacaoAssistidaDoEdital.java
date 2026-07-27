package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.importacaoedital.dominio.TipoDaFonteDoEdital;

public record SolicitacaoDeInterpretacaoAssistidaDoEdital(
        TipoDaFonteDoEdital tipoDaFonte,
        String nomeDoArquivo,
        byte[] conteudoDoArquivo,
        String texto,
        String descricaoDoCargoAlvo) {

    public SolicitacaoDeInterpretacaoAssistidaDoEdital {
        if (tipoDaFonte == null || descricaoDoCargoAlvo == null
                || descricaoDoCargoAlvo.isBlank()
                || descricaoDoCargoAlvo.length() > 1_000) {
            throw new IllegalArgumentException(
                    "Fonte e cargo alvo sao obrigatorios para interpretacao.");
        }
        nomeDoArquivo = nomeDoArquivo == null || nomeDoArquivo.isBlank()
                ? "edital" : nomeDoArquivo.strip();
        descricaoDoCargoAlvo = descricaoDoCargoAlvo.strip();
        conteudoDoArquivo = conteudoDoArquivo == null
                ? new byte[0] : conteudoDoArquivo.clone();

        boolean textoValido = texto != null && !texto.isBlank();
        boolean pdfValido = conteudoDoArquivo.length > 0;
        if (tipoDaFonte == TipoDaFonteDoEdital.TEXTO && !textoValido) {
            throw new IllegalArgumentException(
                    "Texto UTF-8 e obrigatorio para esta fonte.");
        }
        if (tipoDaFonte != TipoDaFonteDoEdital.TEXTO && !pdfValido) {
            throw new IllegalArgumentException(
                    "Conteudo do PDF e obrigatorio para esta fonte.");
        }
    }

    @Override
    public byte[] conteudoDoArquivo() {
        return conteudoDoArquivo.clone();
    }
}
