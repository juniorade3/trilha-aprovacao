package br.com.trilhaaprovacao.conteudoprogramatico.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RequisicaoDeMapeamento(
        @NotNull(message = "topico e obrigatorio")
        UUID identificadorDoTopicoDaMateria) {
}
