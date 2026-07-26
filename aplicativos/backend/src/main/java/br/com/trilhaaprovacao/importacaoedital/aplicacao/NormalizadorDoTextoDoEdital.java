package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import java.text.Normalizer;
import java.util.Locale;

public class NormalizadorDoTextoDoEdital {
    public String normalizarNome(String valor) {
        if (valor == null) return null;
        String semAcentos = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String normalizado = semAcentos.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{Alnum}]+", " ").strip()
                .replaceAll("\\s+", " ");
        return normalizado.isEmpty() ? null : normalizado;
    }

    public String criarChave(String prefixo, int ordem, String nome) {
        String base = normalizarNome(nome);
        String sufixo = base == null ? "sem-nome"
                : base.replace(' ', '-');
        if (sufixo.length() > 100) sufixo = sufixo.substring(0, 100);
        return "%s-%03d-%s".formatted(prefixo, ordem, sufixo);
    }
}
