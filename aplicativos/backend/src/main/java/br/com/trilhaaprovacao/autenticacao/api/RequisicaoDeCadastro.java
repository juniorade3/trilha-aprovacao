package br.com.trilhaaprovacao.autenticacao.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record RequisicaoDeCadastro(
        @NotBlank(message = "nome e obrigatorio") @Size(max = 120, message = "nome deve ter no maximo 120 caracteres") String nome,
        @NotBlank(message = "e-mail e obrigatorio") @Email(message = "e-mail invalido") String email,
        @NotBlank(message = "senha e obrigatoria") @Size(min = 8, message = "senha deve ter ao menos 8 caracteres") String senha) {
    public RequisicaoDeCadastro {
        nome = nome == null ? null : nome.trim();
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "RequisicaoDeCadastro[nome=" + nome + ", email=" + email + ", senha=[PROTEGIDA]]";
    }
}
