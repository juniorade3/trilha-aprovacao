package br.com.trilhaaprovacao.planejamento.dominio;

import java.util.Objects;

public record JustificativaDaGeracao(String codigo, String mensagem) {
    public JustificativaDaGeracao {
        Objects.requireNonNull(codigo);
        Objects.requireNonNull(mensagem);
    }
}
