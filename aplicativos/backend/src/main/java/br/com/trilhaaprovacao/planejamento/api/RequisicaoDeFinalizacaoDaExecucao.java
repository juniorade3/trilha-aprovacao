package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.evidencias.api.RequisicaoDeEvidencia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RequisicaoDeFinalizacaoDaExecucao(
        @NotNull @Min(1) @Max(1440) Integer duracaoExecutadaEmMinutos,
        @Size(max = 2000) String observacao,
        UUID identificadorDoTopico,
        @Valid RequisicaoDeEvidencia evidencia) {
}
