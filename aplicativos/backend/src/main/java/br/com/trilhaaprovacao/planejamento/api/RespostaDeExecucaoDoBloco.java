package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.planejamento.dominio.ExecucaoDoBloco;
import br.com.trilhaaprovacao.planejamento.dominio.ResultadoDaExecucao;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDeExecucaoDoBloco(
        UUID identificador,
        UUID identificadorDoBloco,
        OffsetDateTime iniciadaEm,
        OffsetDateTime encerradaEm,
        Integer duracaoExecutadaEmMinutos,
        ResultadoDaExecucao resultado,
        String observacao,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public static RespostaDeExecucaoDoBloco de(ExecucaoDoBloco execucao) {
        return new RespostaDeExecucaoDoBloco(execucao.identificador(),
                execucao.identificadorDoBloco(), execucao.iniciadaEm(),
                execucao.encerradaEm(), execucao.duracaoExecutadaEmMinutos(),
                execucao.resultado(), execucao.observacao(), execucao.criadoEm(),
                execucao.atualizadoEm(), execucao.versao());
    }
}
