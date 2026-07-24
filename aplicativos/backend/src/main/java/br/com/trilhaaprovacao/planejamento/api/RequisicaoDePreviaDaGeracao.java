package br.com.trilhaaprovacao.planejamento.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RequisicaoDePreviaDaGeracao(
        @NotNull LocalDate dataDeReferencia,
        @NotNull @Min(25) @Max(180) Integer duracaoDoBlocoPrincipalEmMinutos,
        @Min(1) @Max(20)
        @Schema(description = "Quantidade desejada de materias distintas por dia.",
                defaultValue = "3", example = "4")
        Integer quantidadeDeMateriasPorDia) {
}
