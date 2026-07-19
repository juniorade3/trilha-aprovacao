package br.com.trilhaaprovacao.planejamento.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RequisicaoDeDisponibilidade(
        @NotNull LocalDate data,
        @NotNull @Min(0) @Max(1440) Integer minutosDisponiveis) {
}
