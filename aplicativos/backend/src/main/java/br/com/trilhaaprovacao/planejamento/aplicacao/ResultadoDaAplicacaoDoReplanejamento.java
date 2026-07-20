package br.com.trilhaaprovacao.planejamento.aplicacao;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ResultadoDaAplicacaoDoReplanejamento(
        UUID identificadorDoReplanejamento,
        OffsetDateTime aplicadoEm,
        ResultadoDoPlanoSemanal planoAtualizado,
        int quantidadeDePendenciasTransferidas,
        int quantidadeDeFragmentosCriados) { }
