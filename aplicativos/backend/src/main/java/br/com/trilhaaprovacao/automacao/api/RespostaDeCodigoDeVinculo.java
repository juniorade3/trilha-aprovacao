package br.com.trilhaaprovacao.automacao.api;

import br.com.trilhaaprovacao.automacao.aplicacao.CodigoDeVinculoGerado;
import java.time.OffsetDateTime;

public record RespostaDeCodigoDeVinculo(
        String codigo,
        OffsetDateTime expiraEm,
        RespostaDeVinculoDoTelegram vinculo) {

    public static RespostaDeCodigoDeVinculo de(CodigoDeVinculoGerado codigo) {
        return new RespostaDeCodigoDeVinculo(codigo.codigo(), codigo.expiraEm(),
                RespostaDeVinculoDoTelegram.de(codigo.vinculo()));
    }
}
