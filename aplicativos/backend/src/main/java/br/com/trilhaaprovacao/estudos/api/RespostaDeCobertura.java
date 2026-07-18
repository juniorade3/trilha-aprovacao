package br.com.trilhaaprovacao.estudos.api;

import br.com.trilhaaprovacao.estudos.dominio.CoberturaDeTopicoPorMaterial;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDeCobertura(
        UUID identificador,
        UUID identificadorDoMaterial,
        UUID identificadorDoTopico,
        String nomeDoTopico,
        OffsetDateTime criadoEm) {

    static RespostaDeCobertura de(
            CoberturaDeTopicoPorMaterial cobertura, String nomeDoTopico) {
        return new RespostaDeCobertura(cobertura.identificador(),
                cobertura.identificadorDoMaterial(), cobertura.identificadorDoTopico(),
                nomeDoTopico, cobertura.criadoEm());
    }
}
