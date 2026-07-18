package br.com.trilhaaprovacao.concursos.api;

import br.com.trilhaaprovacao.concursos.dominio.SituacaoDoConcurso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record RequisicaoDeConcurso(
        @NotBlank(message = "nome e obrigatorio")
        @Size(max = 160, message = "nome deve ter no maximo 160 caracteres")
        String nome,
        @Size(max = 1000, message = "descricao deve ter no maximo 1000 caracteres")
        String descricao,
        @Size(max = 160, message = "orgao deve ter no maximo 160 caracteres")
        String orgao,
        @Size(max = 160, message = "banca deve ter no maximo 160 caracteres")
        String banca,
        @NotNull(message = "situacao e obrigatoria")
        SituacaoDoConcurso situacao,
        LocalDate dataPrevistaPrincipal) {
}
