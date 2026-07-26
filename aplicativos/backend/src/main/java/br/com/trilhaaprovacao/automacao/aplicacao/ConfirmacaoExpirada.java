package br.com.trilhaaprovacao.automacao.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;

public final class ConfirmacaoExpirada extends ConflitoDeDominio {
    public ConfirmacaoExpirada() {
        super("CONFIRMACAO_EXPIRADA",
                "A confirmacao expirou. Prepare uma nova operacao.");
    }
}
