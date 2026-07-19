package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.planejamento.dominio.DisponibilidadeDoDia;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDeDisponibilidade(
        UUID identificador,
        LocalDate data,
        int minutosDisponiveis,
        OffsetDateTime atualizadoEm,
        long versao) {

    public static RespostaDeDisponibilidade de(DisponibilidadeDoDia disponibilidade) {
        return new RespostaDeDisponibilidade(disponibilidade.identificador(),
                disponibilidade.data(), disponibilidade.minutosDisponiveis(),
                disponibilidade.atualizadoEm(), disponibilidade.versao());
    }
}
