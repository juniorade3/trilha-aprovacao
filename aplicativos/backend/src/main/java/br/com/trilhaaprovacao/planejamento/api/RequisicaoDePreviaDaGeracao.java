package br.com.trilhaaprovacao.planejamento.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RequisicaoDePreviaDaGeracao(
        @NotNull LocalDate dataDeReferencia,
        @NotNull @Min(25) @Max(180) Integer duracaoDoBlocoPrincipalEmMinutos) {
}
