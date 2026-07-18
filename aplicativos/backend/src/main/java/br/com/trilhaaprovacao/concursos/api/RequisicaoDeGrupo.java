package br.com.trilhaaprovacao.concursos.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record RequisicaoDeGrupo(
        @NotBlank(message = "nome e obrigatorio")
        @Size(max = 160, message = "nome deve ter no maximo 160 caracteres")
        String nome,
        @Min(value = 1, message = "ordem deve ser positiva")
        int ordem,
        @Positive(message = "quantidade de questoes deve ser positiva")
        Integer quantidadeDeQuestoes,
        @DecimalMin(value = "0", inclusive = false, message = "pontuacao maxima deve ser positiva")
        BigDecimal pontuacaoMaxima,
        @DecimalMin(value = "0", inclusive = false, message = "pontuacao minima deve ser positiva")
        BigDecimal pontuacaoMinima) {
}
