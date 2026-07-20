package br.com.trilhaaprovacao.planejamento.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record RequisicaoDeAplicacaoDoReplanejamento(
        @NotNull LocalDate dataDeReferencia,
        @NotNull Set<UUID> identificadoresDasPendenciasIgnoradas,
        @NotNull Set<UUID> identificadoresDasConfirmacoesDoLimite,
        @NotBlank String assinaturaDaPrevia) { }
