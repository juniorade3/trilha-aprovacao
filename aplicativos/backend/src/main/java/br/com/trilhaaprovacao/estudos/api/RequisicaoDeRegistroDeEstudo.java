package br.com.trilhaaprovacao.estudos.api;

import br.com.trilhaaprovacao.estudos.dominio.TipoDeEstudo;
import br.com.trilhaaprovacao.evidencias.api.RequisicaoDeEvidencia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RequisicaoDeRegistroDeEstudo(
        @NotNull(message = "topico e obrigatorio") UUID identificadorDoTopico,
        UUID identificadorDoMaterial,
        @NotNull(message = "data e hora sao obrigatorias") OffsetDateTime dataHora,
        @Min(value = 1, message = "duracao minima e 1 minuto")
        @Max(value = 1440, message = "duracao maxima e 1440 minutos")
        int duracaoEmMinutos,
        String observacao,
        TipoDeEstudo tipoDeEstudo,
        @Valid RequisicaoDeEvidencia evidencia) {
}
