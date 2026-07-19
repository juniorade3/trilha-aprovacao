package br.com.trilhaaprovacao.planejamento.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RequisicaoDeAlteracaoDasDisponibilidades(
        @NotNull @Size(min = 7, max = 7)
        List<@Valid RequisicaoDeDisponibilidade> disponibilidades) {
}
