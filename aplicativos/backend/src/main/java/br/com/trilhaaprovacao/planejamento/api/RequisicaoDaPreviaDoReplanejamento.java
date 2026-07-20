package br.com.trilhaaprovacao.planejamento.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record RequisicaoDaPreviaDoReplanejamento(
        @NotNull LocalDate dataDeReferencia,
        @NotNull Set<UUID> identificadoresDasPendenciasIgnoradas) { }
