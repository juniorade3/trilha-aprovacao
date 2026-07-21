package br.com.trilhaaprovacao.evidencias.dominio;

import java.text.Normalizer;
import java.util.Locale;

public final class NormalizacaoDePadraoDeErro {
    private NormalizacaoDePadraoDeErro() {
    }

    public static String normalizar(String descricao) {
        if (descricao == null) {
            throw new IllegalArgumentException("Descricao do padrao de erro e obrigatoria.");
        }
        String limpa = descricao.trim().replaceAll("\\s+", " ");
        if (limpa.isBlank() || limpa.length() > 200) {
            throw new IllegalArgumentException(
                    "Descricao do padrao de erro deve ter entre 1 e 200 caracteres.");
        }
        return Normalizer.normalize(limpa, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    public static String limpar(String descricao) {
        normalizar(descricao);
        return descricao.trim().replaceAll("\\s+", " ");
    }
}
