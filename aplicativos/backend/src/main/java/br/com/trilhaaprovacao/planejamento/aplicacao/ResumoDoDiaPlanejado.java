package br.com.trilhaaprovacao.planejamento.aplicacao;

import java.time.LocalDate;

public record ResumoDoDiaPlanejado(
        LocalDate data,
        int minutosDisponiveis,
        int minutosPlanejados,
        int saldoEmMinutos,
        boolean possuiExcesso) {
}
