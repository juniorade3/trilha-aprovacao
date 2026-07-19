package br.com.trilhaaprovacao.planejamento.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RequisicaoDeOrdenacaoDosBlocos(
        @NotNull LocalDate data,
        @NotNull @Size(min = 1) List<@NotNull UUID> identificadoresOrdenados) {
}
