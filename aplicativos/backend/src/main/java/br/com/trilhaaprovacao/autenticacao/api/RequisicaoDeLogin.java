package br.com.trilhaaprovacao.autenticacao.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

public record RequisicaoDeLogin(
        @NotBlank(message = "e-mail e obrigatorio") @Email(message = "e-mail invalido") String email,
        @NotBlank(message = "senha e obrigatoria") String senha) {
    public RequisicaoDeLogin {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "RequisicaoDeLogin[email=" + email + ", senha=[PROTEGIDA]]";
    }
}
