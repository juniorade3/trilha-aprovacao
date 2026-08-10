package br.com.trilhaaprovacao.trilhas.api;

import br.com.trilhaaprovacao.trilhas.dominio.SituacaoDoAcompanhamentoDaTarefa;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequisicaoDeAcompanhamentoDaTarefa(
        @NotNull SituacaoDoAcompanhamentoDaTarefa situacao,
        @Size(max = 2000) String observacao) {
}
