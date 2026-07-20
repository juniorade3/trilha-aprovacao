package br.com.trilhaaprovacao.planejamento.aplicacao;

import br.com.trilhaaprovacao.estudos.dominio.RegistroDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.BlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.ExecucaoDoBloco;
import java.util.Objects;

public record ResultadoDaExecucaoDoBloco(
        BlocoDeEstudo bloco,
        ExecucaoDoBloco execucao,
        RegistroDeEstudo estudo) {
    public ResultadoDaExecucaoDoBloco {
        Objects.requireNonNull(bloco);
        Objects.requireNonNull(execucao);
    }
    public ResultadoDaExecucaoDoBloco(BlocoDeEstudo bloco, ExecucaoDoBloco execucao) {
        this(bloco, execucao, null);
    }
}
