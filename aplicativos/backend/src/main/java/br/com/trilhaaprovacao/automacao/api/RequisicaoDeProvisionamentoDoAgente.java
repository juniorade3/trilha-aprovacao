package br.com.trilhaaprovacao.automacao.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RequisicaoDeProvisionamentoDoAgente(
        @Positive long identificadorDoBot,
        @Positive long identificadorDoTelegram,
        @Positive long identificadorDoChat,
        @NotBlank @Size(max = 160) String identificadorDoAgente,
        @NotBlank @Size(max = 160) String identificadorDaSessao) {
}
