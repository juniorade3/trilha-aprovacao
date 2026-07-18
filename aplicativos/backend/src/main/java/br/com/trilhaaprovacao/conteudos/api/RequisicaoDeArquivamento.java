package br.com.trilhaaprovacao.conteudos.api;

import jakarta.validation.constraints.NotNull;

public record RequisicaoDeArquivamento(
        @NotNull(message = "arquivada e obrigatorio")
        Boolean arquivada) {
}
