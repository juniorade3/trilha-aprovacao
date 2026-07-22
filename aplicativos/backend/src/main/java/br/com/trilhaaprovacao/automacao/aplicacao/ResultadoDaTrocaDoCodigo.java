package br.com.trilhaaprovacao.automacao.aplicacao;

import br.com.trilhaaprovacao.automacao.dominio.VinculoDeCanal;

public record ResultadoDaTrocaDoCodigo(
        VinculoDeCanal vinculo,
        CredencialEmitida credencial) {
}
