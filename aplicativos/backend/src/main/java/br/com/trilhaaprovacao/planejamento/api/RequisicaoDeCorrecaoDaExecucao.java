package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.evidencias.api.RequisicaoDeEvidencia;
import jakarta.validation.Valid;
import br.com.trilhaaprovacao.planejamento.dominio.ResultadoDaExecucao;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RequisicaoDeCorrecaoDaExecucao(
        @NotNull ResultadoDaExecucao resultado,
        @Min(1) @Max(1440) int duracaoExecutadaEmMinutos,
        @Size(max = 2000) String observacao,
        UUID identificadorDoTopico,
        @Valid RequisicaoDeEvidencia evidencia) {
}
