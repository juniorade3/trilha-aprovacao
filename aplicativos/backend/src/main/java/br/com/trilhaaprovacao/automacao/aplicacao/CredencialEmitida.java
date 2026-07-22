package br.com.trilhaaprovacao.automacao.aplicacao;

import br.com.trilhaaprovacao.automacao.dominio.CredencialDeIntegracao;

public record CredencialEmitida(
        String token,
        CredencialDeIntegracao credencial) {
}
