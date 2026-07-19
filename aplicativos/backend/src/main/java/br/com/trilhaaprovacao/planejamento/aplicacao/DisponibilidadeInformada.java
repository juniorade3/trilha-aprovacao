package br.com.trilhaaprovacao.planejamento.aplicacao;

import java.time.LocalDate;

public record DisponibilidadeInformada(LocalDate data, int minutosDisponiveis) {
}
