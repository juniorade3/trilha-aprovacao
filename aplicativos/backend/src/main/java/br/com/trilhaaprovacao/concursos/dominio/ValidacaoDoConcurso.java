package br.com.trilhaaprovacao.concursos.dominio;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class ValidacaoDoConcurso {
    private ValidacaoDoConcurso() {
    }

    static String textoObrigatorio(String valor, String campo) {
        String texto = textoOpcional(valor);
        if (texto == null) {
            throw new IllegalArgumentException(campo + " e obrigatorio.");
        }
        return texto;
    }

    static String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    static String chave(String valor) {
        return textoObrigatorio(valor, "nome").toLowerCase(Locale.ROOT);
    }

    static void ordemPositiva(int ordem) {
        if (ordem < 1) {
            throw new IllegalArgumentException("Ordem deve ser positiva.");
        }
    }

    static void inteiroPositivo(Integer valor, String campo) {
        if (valor != null && valor < 1) {
            throw new IllegalArgumentException(campo + " deve ser positivo.");
        }
    }

    static void decimalPositivo(BigDecimal valor, String campo) {
        if (valor != null && valor.signum() <= 0) {
            throw new IllegalArgumentException(campo + " deve ser positivo.");
        }
    }

    static void pontuacaoCoerente(BigDecimal minima, BigDecimal maxima) {
        decimalPositivo(minima, "Pontuacao minima");
        decimalPositivo(maxima, "Pontuacao maxima");
        if (minima != null && maxima != null && minima.compareTo(maxima) > 0) {
            throw new IllegalArgumentException("Pontuacao minima nao pode exceder a maxima.");
        }
    }

    static String enderecoValido(String valor) {
        String endereco = textoOpcional(valor);
        if (endereco == null) {
            return null;
        }
        try {
            URI uri = new URI(endereco);
            if (!uri.isAbsolute() || (!"http".equalsIgnoreCase(uri.getScheme())
                    && !"https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("Endereco do documento deve ser uma URL HTTP valida.");
            }
            return endereco;
        } catch (URISyntaxException excecao) {
            throw new IllegalArgumentException("Endereco do documento deve ser uma URL HTTP valida.");
        }
    }
}
