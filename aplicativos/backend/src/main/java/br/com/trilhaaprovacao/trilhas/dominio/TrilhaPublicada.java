package br.com.trilhaaprovacao.trilhas.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TrilhaPublicada(
        UUID identificador,
        String codigo,
        String nome,
        String versaoPublicada,
        String descricao,
        OffsetDateTime publicadaEm,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {
}
