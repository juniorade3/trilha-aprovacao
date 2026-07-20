package br.com.trilhaaprovacao.planejamento.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record RequisicaoDeReagendamentoDoBloco(
        @NotNull LocalDate data, LocalTime horarioPrevisto, @Min(1) int ordem) {
}
