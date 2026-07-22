package br.com.trilhaaprovacao.automacao.aplicacao;

import br.com.trilhaaprovacao.automacao.dominio.VinculoDeCanal;
import java.time.OffsetDateTime;

public record CodigoDeVinculoGerado(
        String codigo,
        OffsetDateTime expiraEm,
        VinculoDeCanal vinculo) {
}
