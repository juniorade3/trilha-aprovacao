package br.com.trilhaaprovacao.importacaoedital.aplicacao;

/** Porta opcional. Nenhuma implementacao local implica OCR indisponivel. */
public interface ServicoDeOcrDoEdital {
    String extrairTexto(byte[] pdf);
}
