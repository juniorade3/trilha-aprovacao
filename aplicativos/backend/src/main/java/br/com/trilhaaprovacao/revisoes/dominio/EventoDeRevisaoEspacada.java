package br.com.trilhaaprovacao.revisoes.dominio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record EventoDeRevisaoEspacada(
        UUID identificadorDaEvidencia,
        OffsetDateTime instante,
        LocalDate data,
        boolean revisao,
        Integer nivelDeRecordacao) {

    public EventoDeRevisaoEspacada {
        Objects.requireNonNull(identificadorDaEvidencia,
                "Identificador da evidencia e obrigatorio.");
        Objects.requireNonNull(instante, "Instante da evidencia e obrigatorio.");
        Objects.requireNonNull(data, "Data da evidencia e obrigatoria.");
        if (nivelDeRecordacao != null
                && (nivelDeRecordacao < 1 || nivelDeRecordacao > 5)) {
            throw new IllegalArgumentException(
                    "Nivel de recordacao deve estar entre 1 e 5.");
        }
    }
}
