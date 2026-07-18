package br.com.trilhaaprovacao.estudos.dominio;

import java.net.URI;

final class ValidacaoDeEstudo {
    private ValidacaoDeEstudo() {
    }

    static String obrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " e obrigatorio.");
        }
        return valor.trim();
    }

    static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    static String endereco(String valor) {
        String endereco = opcional(valor);
        if (endereco == null) {
            return null;
        }
        try {
            URI uri = URI.create(endereco);
            if (!uri.isAbsolute() || (!"http".equalsIgnoreCase(uri.getScheme())
                    && !"https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
            return endereco;
        } catch (IllegalArgumentException excecao) {
            throw new IllegalArgumentException("Endereco deve ser uma URL HTTP ou HTTPS.");
        }
    }
}
