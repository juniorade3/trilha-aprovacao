package br.com.trilhaaprovacao.concursos.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record RequisicaoDeEdital(
        @NotBlank(message = "titulo e obrigatorio")
        @Size(max = 200, message = "titulo deve ter no maximo 200 caracteres")
        String titulo,
        @Size(max = 80, message = "numero deve ter no maximo 80 caracteres")
        String numero,
        @Positive(message = "ano deve ser positivo")
        Integer ano,
        @Size(max = 1000, message = "descricao deve ter no maximo 1000 caracteres")
        String descricao,
        LocalDate dataDePublicacao,
        @Size(max = 2048, message = "endereco deve ter no maximo 2048 caracteres")
        String enderecoDoDocumento) {
}
