package br.com.trilhaaprovacao.automacao.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RequisicaoDeConfirmacaoDaOperacao(
        @NotBlank @Pattern(regexp = "^[23456789A-HJ-NP-Z]{8}$") String codigo,
        @NotBlank @Pattern(regexp = "^(BOTAO|TEXTO|VOZ)$") String metodo,
        @Positive long identificadorDoBot,
        @Positive long identificadorDoTelegram,
        @Positive long identificadorDoChat,
        @NotBlank @Size(max = 160) String identificadorDaSessao,
        @NotBlank @Size(max = 160) String identificadorDoUpdate) {
}
