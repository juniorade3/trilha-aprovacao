package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.planejamento.aplicacao.ResultadoDaAplicacaoDoReplanejamento;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDaAplicacaoDoReplanejamento(
        UUID identificadorDoReplanejamento,
        OffsetDateTime aplicadoEm,
        RespostaDePlanoSemanal planoAtualizado,
        int quantidadeDePendenciasTransferidas,
        int quantidadeDeFragmentosCriados) {
    public static RespostaDaAplicacaoDoReplanejamento de(
            ResultadoDaAplicacaoDoReplanejamento resultado) {
        return new RespostaDaAplicacaoDoReplanejamento(
                resultado.identificadorDoReplanejamento(), resultado.aplicadoEm(),
                RespostaDePlanoSemanal.de(resultado.planoAtualizado()),
                resultado.quantidadeDePendenciasTransferidas(),
                resultado.quantidadeDeFragmentosCriados());
    }
}
