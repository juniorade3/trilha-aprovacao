package br.com.trilhaaprovacao.conteudos.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RequisicaoDeMateria(
        @NotBlank(message = "nome e obrigatorio")
        @Size(max = 120, message = "nome deve ter no maximo 120 caracteres")
        String nome,

        @Size(max = 1000, message = "descricao deve ter no maximo 1000 caracteres")
        String descricao,

        @Pattern(regexp = "^\\s*$|^#[0-9A-Fa-f]{6}$", message = "cor deve usar o formato #RRGGBB")
        String cor) {
}
