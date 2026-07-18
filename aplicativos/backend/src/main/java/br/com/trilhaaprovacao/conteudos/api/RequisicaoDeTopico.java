package br.com.trilhaaprovacao.conteudos.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RequisicaoDeTopico(
        @NotBlank(message = "nome e obrigatorio")
        @Size(max = 160, message = "nome deve ter no maximo 160 caracteres")
        String nome,

        @Size(max = 1000, message = "descricao deve ter no maximo 1000 caracteres")
        String descricao,

        UUID identificadorDoTopicoPai,

        @Min(value = 1, message = "ordem deve ser positiva")
        int ordem) {
}
