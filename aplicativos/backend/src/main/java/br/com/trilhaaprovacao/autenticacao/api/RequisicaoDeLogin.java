package br.com.trilhaaprovacao.autenticacao.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequisicaoDeLogin(
        @NotBlank(message = "e-mail e obrigatorio") @Email(message = "e-mail invalido") String email,
        @NotBlank(message = "senha e obrigatoria") String senha) {
}
