package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.planejamento.aplicacao.EstadoDoPlanejamentoDeHoje;
import br.com.trilhaaprovacao.planejamento.aplicacao.ResultadoDoPlanejamentoDeHoje;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RespostaDoPlanejamentoDeHoje(
        EstadoDoPlanejamentoDeHoje estado,
        LocalDate data,
        UUID identificadorDoPlano,
        LocalDate dataInicialDoPlano,
        int minutosDisponiveis,
        int minutosPlanejados,
        int quantidadeDeBlocos,
        RespostaDeBlocoDeEstudo proximoBloco,
        List<RespostaDeBlocoDeEstudo> sequencia,
        List<RespostaDeBlocoDeEstudo> atrasados,
        List<RespostaDeBlocoDeEstudo> realizados) {

    public static RespostaDoPlanejamentoDeHoje de(ResultadoDoPlanejamentoDeHoje resultado) {
        return new RespostaDoPlanejamentoDeHoje(resultado.estado(), resultado.data(),
                resultado.identificadorDoPlano(), resultado.dataInicialDoPlano(),
                resultado.minutosDisponiveis(), resultado.minutosPlanejados(),
                resultado.quantidadeDeBlocos(), resultado.proximoBloco() == null
                        ? null : RespostaDeBlocoDeEstudo.de(resultado.proximoBloco()),
                resultado.sequencia().stream().map(RespostaDeBlocoDeEstudo::de).toList(),
                resultado.atrasados().stream().map(RespostaDeBlocoDeEstudo::de).toList(),
                resultado.realizados().stream().map(RespostaDeBlocoDeEstudo::de).toList());
    }
}
