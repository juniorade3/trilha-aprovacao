package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.planejamento.aplicacao.DadosDoBlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.TipoDeAtividade;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record RequisicaoDeBlocoDeEstudo(
        UUID identificadorDaMateria,
        UUID identificadorDoTopico,
        @NotBlank @Size(max = 200) String titulo,
        @NotNull TipoDeAtividade tipoDeAtividade,
        @NotNull LocalDate data,
        @NotNull @Min(1) @Max(1440) Integer duracaoPrevistaEmMinutos,
        @NotNull @Min(1) Integer ordem,
        LocalTime horarioPrevisto,
        @Size(max = 2000) String observacao) {

    public DadosDoBlocoDeEstudo paraDados() {
        return new DadosDoBlocoDeEstudo(identificadorDaMateria,
                identificadorDoTopico, titulo, tipoDeAtividade, data,
                duracaoPrevistaEmMinutos, ordem, horarioPrevisto, observacao);
    }
}
