package br.com.trilhaaprovacao.automacao.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record RequisicaoDeTrocaDoCodigoDeVinculo(
        @NotBlank String codigo,
        @Positive long identificadorDoBot,
        @Positive long identificadorDoTelegram,
        @Positive long identificadorDoChat) {
}
