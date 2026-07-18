package br.com.trilhaaprovacao.conteudos.dominio;

import java.util.Locale;

final class NormalizacaoDeTexto {
    private NormalizacaoDeTexto() {
    }

    static String obrigatorio(String valor, String campo) {
        String normalizado = opcional(valor);
        if (normalizado == null) {
            throw new IllegalArgumentException(campo + " e obrigatorio.");
        }
        return normalizado;
    }

    static String opcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    static String chave(String valor) {
        return obrigatorio(valor, "nome").toLowerCase(Locale.ROOT);
    }
}
