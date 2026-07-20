package br.com.trilhaaprovacao.planejamento.dominio;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record PrioridadeDeMateriaNoPlano(
        UUID identificador,
        UUID identificadorDoPlano,
        UUID identificadorDaMateria,
        PrioridadeDaMateriaNoPlano prioridade,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public PrioridadeDeMateriaNoPlano {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(identificadorDoPlano);
        Objects.requireNonNull(identificadorDaMateria);
        Objects.requireNonNull(prioridade);
        Objects.requireNonNull(criadoEm);
        Objects.requireNonNull(atualizadoEm);
    }

    public static PrioridadeDeMateriaNoPlano criar(
            UUID plano, UUID materia, PrioridadeDaMateriaNoPlano prioridade) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new PrioridadeDeMateriaNoPlano(UUID.randomUUID(), plano, materia,
                prioridade, agora, agora, 0);
    }

    public PrioridadeDeMateriaNoPlano alterar(PrioridadeDaMateriaNoPlano novaPrioridade) {
        return new PrioridadeDeMateriaNoPlano(identificador, identificadorDoPlano,
                identificadorDaMateria, Objects.requireNonNull(novaPrioridade),
                criadoEm, OffsetDateTime.now(), versao);
    }
}
