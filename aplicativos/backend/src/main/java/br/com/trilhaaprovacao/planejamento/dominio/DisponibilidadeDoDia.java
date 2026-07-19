package br.com.trilhaaprovacao.planejamento.dominio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record DisponibilidadeDoDia(
        UUID identificador,
        UUID identificadorDoPlano,
        LocalDate data,
        int minutosDisponiveis,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public DisponibilidadeDoDia {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(identificadorDoPlano);
        Objects.requireNonNull(data);
        Objects.requireNonNull(criadoEm);
        Objects.requireNonNull(atualizadoEm);
        validarMinutos(minutosDisponiveis);
    }

    public static DisponibilidadeDoDia criar(PlanoSemanal plano, LocalDate data) {
        Objects.requireNonNull(plano);
        if (!plano.contem(data)) {
            throw new IllegalArgumentException("A data deve pertencer a semana do plano.");
        }
        OffsetDateTime agora = OffsetDateTime.now();
        return new DisponibilidadeDoDia(UUID.randomUUID(), plano.identificador(),
                data, 0, agora, agora, 0);
    }

    public DisponibilidadeDoDia alterarMinutos(int minutos) {
        validarMinutos(minutos);
        return new DisponibilidadeDoDia(identificador, identificadorDoPlano, data,
                minutos, criadoEm, OffsetDateTime.now(), versao);
    }

    private static void validarMinutos(int minutos) {
        if (minutos < 0 || minutos > 1440) {
            throw new IllegalArgumentException(
                    "A disponibilidade deve estar entre 0 e 1440 minutos.");
        }
    }
}
