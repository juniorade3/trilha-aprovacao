package br.com.trilhaaprovacao.concursos.api;

import jakarta.validation.constraints.NotNull;

public record RequisicaoDeArquivamento(
        @NotNull(message = "arquivado e obrigatorio")
        Boolean arquivado) {
}
