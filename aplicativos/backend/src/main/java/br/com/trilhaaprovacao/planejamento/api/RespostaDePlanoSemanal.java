package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.planejamento.aplicacao.ResultadoDoPlanoSemanal;
import br.com.trilhaaprovacao.planejamento.dominio.EstadoDoPlanoSemanal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RespostaDePlanoSemanal(
        UUID identificador,
        LocalDate dataInicial,
        LocalDate dataFinal,
        EstadoDoPlanoSemanal estado,
        List<RespostaDeDisponibilidade> disponibilidades,
        List<RespostaDeBlocoDeEstudo> blocos,
        int totalDeMinutosDisponiveis,
        int totalDeMinutosPlanejados,
        int quantidadeDeBlocos,
        boolean possuiExcesso,
        List<RespostaDeResumoDoDiaPlanejado> resumosDosDias,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public static RespostaDePlanoSemanal de(ResultadoDoPlanoSemanal resultado) {
        var plano = resultado.plano();
        return new RespostaDePlanoSemanal(plano.identificador(), plano.dataInicial(),
                plano.dataFinal(), plano.estado(), resultado.disponibilidades().stream()
                        .map(RespostaDeDisponibilidade::de).toList(),
                resultado.blocos().stream().map(RespostaDeBlocoDeEstudo::de).toList(),
                resultado.totalDeMinutosDisponiveis(), resultado.totalDeMinutosPlanejados(),
                resultado.quantidadeDeBlocos(), resultado.possuiExcesso(),
                resultado.resumosDosDias().stream()
                        .map(RespostaDeResumoDoDiaPlanejado::de).toList(),
                plano.criadoEm(), plano.atualizadoEm(), plano.versao());
    }
}
