package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.planejamento.dominio.PrioridadeDaMateriaNoPlano;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RequisicaoDePrioridadeDeMateria(
        @NotNull UUID identificadorDaMateria,
        @NotNull PrioridadeDaMateriaNoPlano prioridade) {
}
