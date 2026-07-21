package br.com.trilhaaprovacao.evidencias.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RequisicaoDeResultadoDeQuestoes(
        @NotNull @Min(1) Integer quantidadeDeQuestoes,
        @NotNull @Min(0) Integer quantidadeDeAcertos) {
}
