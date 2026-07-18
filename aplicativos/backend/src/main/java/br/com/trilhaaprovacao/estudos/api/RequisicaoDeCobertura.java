package br.com.trilhaaprovacao.estudos.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RequisicaoDeCobertura(
        @NotNull(message = "topico e obrigatorio") UUID identificadorDoTopico) {
}
