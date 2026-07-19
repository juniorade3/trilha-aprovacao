package br.com.trilhaaprovacao.planejamento.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RequisicaoDePlanoSemanal(@NotNull LocalDate dataInicial) {
}
