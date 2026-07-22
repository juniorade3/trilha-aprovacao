package br.com.trilhaaprovacao.automacao.api;

import br.com.trilhaaprovacao.automacao.dominio.CanalDeIntegracao;
import br.com.trilhaaprovacao.automacao.dominio.EstadoDoVinculoDeCanal;
import br.com.trilhaaprovacao.automacao.dominio.VinculoDeCanal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDeVinculoDoTelegram(
        UUID identificador,
        CanalDeIntegracao canal,
        EstadoDoVinculoDeCanal estado,
        long identificadorDoBot,
        Long identificadorExterno,
        Long identificadorDoChat,
        boolean provisionado,
        String identificadorDoAgente,
        OffsetDateTime vinculadoEm,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        OffsetDateTime revogadoEm) {

    public static RespostaDeVinculoDoTelegram de(VinculoDeCanal vinculo) {
        return new RespostaDeVinculoDoTelegram(vinculo.identificador(), vinculo.canal(),
                vinculo.estado(), vinculo.identificadorDoBot(),
                vinculo.identificadorExterno(), vinculo.identificadorDoChat(),
                vinculo.provisionadoEm() != null, vinculo.identificadorDoAgente(),
                vinculo.codigoConsumidoEm(), vinculo.criadoEm(),
                vinculo.atualizadoEm(), vinculo.revogadoEm());
    }
}
