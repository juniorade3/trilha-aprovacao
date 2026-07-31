package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import java.text.Normalizer;
import java.util.Locale;

public class NormalizadorDoTextoDoEdital {
    private static final int LIMITE_DA_CHAVE = 160;
    private static final int RESERVA_MINIMA_DO_SUFIXO = 16;

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
        String separadorDaOrdem = "-%03d-".formatted(ordem);
        int limiteDoPrefixo = LIMITE_DA_CHAVE
                - separadorDaOrdem.length() - RESERVA_MINIMA_DO_SUFIXO;
        String prefixoLimitado = prefixo.length() <= limiteDoPrefixo
                ? prefixo : prefixo.substring(0, limiteDoPrefixo);
        int limiteDoSufixo = Math.min(100, LIMITE_DA_CHAVE
                - prefixoLimitado.length() - separadorDaOrdem.length());
        if (sufixo.length() > limiteDoSufixo) {
            sufixo = sufixo.substring(0, limiteDoSufixo);
        }
        return prefixoLimitado + separadorDaOrdem + sufixo;
    }
}
