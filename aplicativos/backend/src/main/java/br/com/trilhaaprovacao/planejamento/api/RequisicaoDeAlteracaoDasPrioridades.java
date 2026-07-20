package br.com.trilhaaprovacao.planejamento.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RequisicaoDeAlteracaoDasPrioridades(
        @NotNull List<@Valid RequisicaoDePrioridadeDeMateria> prioridades) {
}
