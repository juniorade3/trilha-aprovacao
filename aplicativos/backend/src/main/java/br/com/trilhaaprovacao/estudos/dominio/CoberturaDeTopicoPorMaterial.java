package br.com.trilhaaprovacao.estudos.dominio;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record CoberturaDeTopicoPorMaterial(
        UUID identificador,
        UUID identificadorDoMaterial,
        UUID identificadorDoTopico,
        OffsetDateTime criadoEm) {

    public CoberturaDeTopicoPorMaterial {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(identificadorDoMaterial);
        Objects.requireNonNull(identificadorDoTopico);
        Objects.requireNonNull(criadoEm);
    }

    public static CoberturaDeTopicoPorMaterial criar(UUID material, UUID topico) {
        return new CoberturaDeTopicoPorMaterial(
                UUID.randomUUID(), material, topico, OffsetDateTime.now());
    }
}
