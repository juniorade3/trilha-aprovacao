package br.com.trilhaaprovacao.revisoes.dominio;

import java.time.LocalDate;

public record RevisaoEspacadaCalculada(
        int etapa,
        int intervaloEmDias,
        LocalDate dataDevida,
        LocalDate ultimaRevisao,
        Integer ultimaRecordacao) {
}
