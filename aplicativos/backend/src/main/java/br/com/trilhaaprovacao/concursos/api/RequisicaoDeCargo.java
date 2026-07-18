package br.com.trilhaaprovacao.concursos.api;

import br.com.trilhaaprovacao.concursos.dominio.NivelDeEscolaridade;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequisicaoDeCargo(
        @NotBlank(message = "nome e obrigatorio")
        @Size(max = 160, message = "nome deve ter no maximo 160 caracteres")
        String nome,
        @Size(max = 160, message = "area deve ter no maximo 160 caracteres")
        String area,
        @Size(max = 160, message = "especialidade deve ter no maximo 160 caracteres")
        String especialidade,
        @NotNull(message = "nivel de escolaridade e obrigatorio")
        NivelDeEscolaridade nivelDeEscolaridade,
        @Min(value = 1, message = "ordem deve ser positiva")
        int ordem) {
}
