package br.com.trilhaaprovacao.estudos.api;

import br.com.trilhaaprovacao.estudos.dominio.TipoDeMaterial;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RequisicaoDeMaterial(
        @NotBlank(message = "titulo e obrigatorio") String titulo,
        @NotNull(message = "tipo e obrigatorio") TipoDeMaterial tipo,
        String descricao,
        String fonte,
        String endereco,
        @Positive(message = "duracao estimada deve ser positiva")
        Integer duracaoEstimadaEmMinutos) {
}
