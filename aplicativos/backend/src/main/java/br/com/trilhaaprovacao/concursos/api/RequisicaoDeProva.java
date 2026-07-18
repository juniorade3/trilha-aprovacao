package br.com.trilhaaprovacao.concursos.api;

import br.com.trilhaaprovacao.concursos.dominio.CaraterDaProva;
import br.com.trilhaaprovacao.concursos.dominio.TipoDeProva;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RequisicaoDeProva(
        @NotBlank(message = "nome e obrigatorio")
        @Size(max = 160, message = "nome deve ter no maximo 160 caracteres")
        String nome,
        @NotNull(message = "tipo e obrigatorio")
        TipoDeProva tipo,
        @NotNull(message = "carater e obrigatorio")
        CaraterDaProva carater,
        @Min(value = 1, message = "ordem deve ser positiva")
        int ordem,
        OffsetDateTime dataHoraPrevista,
        @Positive(message = "duracao deve ser positiva")
        Integer duracaoEmMinutos,
        @Positive(message = "quantidade de questoes deve ser positiva")
        Integer quantidadeDeQuestoes,
        @DecimalMin(value = "0", inclusive = false, message = "pontuacao maxima deve ser positiva")
        BigDecimal pontuacaoMaxima,
        @DecimalMin(value = "0", inclusive = false, message = "pontuacao minima deve ser positiva")
        BigDecimal pontuacaoMinima) {
}
