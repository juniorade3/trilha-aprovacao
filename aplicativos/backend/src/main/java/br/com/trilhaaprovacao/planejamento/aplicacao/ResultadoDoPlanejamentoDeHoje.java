package br.com.trilhaaprovacao.planejamento.aplicacao;

import br.com.trilhaaprovacao.planejamento.dominio.BlocoDeEstudo;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ResultadoDoPlanejamentoDeHoje(
        EstadoDoPlanejamentoDeHoje estado,
        LocalDate data,
        UUID identificadorDoPlano,
        LocalDate dataInicialDoPlano,
        int minutosDisponiveis,
        int minutosPlanejados,
        BlocoDeEstudo proximoBloco,
        List<BlocoDeEstudo> sequencia,
        List<BlocoDeEstudo> atrasados,
        List<BlocoDeEstudo> realizados) {

    public ResultadoDoPlanejamentoDeHoje {
        sequencia = List.copyOf(sequencia);
        atrasados = List.copyOf(atrasados);
        realizados = List.copyOf(realizados);
    }

    public int quantidadeDeBlocos() {
        return (proximoBloco == null ? 0 : 1) + sequencia.size() + realizados.size();
    }
}
