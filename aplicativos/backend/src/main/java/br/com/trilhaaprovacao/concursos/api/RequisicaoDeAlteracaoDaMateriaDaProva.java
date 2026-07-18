package br.com.trilhaaprovacao.concursos.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record RequisicaoDeAlteracaoDaMateriaDaProva(
        @Min(value = 1, message = "ordem deve ser positiva")
        int ordem,
        @DecimalMin(value = "0", inclusive = false, message = "peso deve ser positivo")
        BigDecimal peso,
        @Positive(message = "quantidade de questoes deve ser positiva")
        Integer quantidadeDeQuestoes,
        @DecimalMin(value = "0", inclusive = false, message = "pontuacao maxima deve ser positiva")
        BigDecimal pontuacaoMaxima) {
}
