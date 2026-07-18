package br.com.trilhaaprovacao.conteudoprogramatico.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record RequisicaoDeAlteracaoDoItemDoEdital(
        @NotBlank(message = "descricao original e obrigatoria")
        String descricaoOriginal,
        UUID identificadorDoItemPai,
        @Min(value = 1, message = "ordem deve ser positiva")
        int ordem) {
}
