package br.com.trilhaaprovacao.planejamento.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RequisicaoDePreviaDaGeracao(
        @NotNull @Min(25) @Max(180) Integer duracaoPadraoDoBlocoPrincipalEmMinutos,
        @NotNull @Min(0) @Max(120) Integer duracaoDoBlocoDeRevisaoEmMinutos) {
}
