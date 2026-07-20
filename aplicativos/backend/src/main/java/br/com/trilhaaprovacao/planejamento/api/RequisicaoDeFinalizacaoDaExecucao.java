package br.com.trilhaaprovacao.planejamento.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequisicaoDeFinalizacaoDaExecucao(
        @NotNull @Min(1) @Max(1440) Integer duracaoExecutadaEmMinutos,
        @Size(max = 2000) String observacao) {
}
