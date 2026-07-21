package br.com.trilhaaprovacao.evidencias.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequisicaoDePadraoDeErro(
        @NotBlank @Size(max = 200) String descricao,
        @Min(1) int quantidadeDeOcorrencias) {
}
