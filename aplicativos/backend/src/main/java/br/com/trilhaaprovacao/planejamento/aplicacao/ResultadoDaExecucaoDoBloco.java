package br.com.trilhaaprovacao.planejamento.aplicacao;

import br.com.trilhaaprovacao.estudos.dominio.RegistroDeEstudo;
import br.com.trilhaaprovacao.evidencias.dominio.EvidenciaDeAprendizagem;
import br.com.trilhaaprovacao.planejamento.dominio.BlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.ExecucaoDoBloco;
import java.util.Objects;

public record ResultadoDaExecucaoDoBloco(
        BlocoDeEstudo bloco,
        ExecucaoDoBloco execucao,
        RegistroDeEstudo estudo,
        EvidenciaDeAprendizagem evidencia) {
    public ResultadoDaExecucaoDoBloco {
        Objects.requireNonNull(bloco);
        Objects.requireNonNull(execucao);
    }
    public ResultadoDaExecucaoDoBloco(BlocoDeEstudo bloco, ExecucaoDoBloco execucao) {
        this(bloco, execucao, null, null);
    }
    public ResultadoDaExecucaoDoBloco(BlocoDeEstudo bloco, ExecucaoDoBloco execucao,
            RegistroDeEstudo estudo) {
        this(bloco, execucao, estudo, null);
    }
}
