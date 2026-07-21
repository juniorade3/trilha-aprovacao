package br.com.trilhaaprovacao.revisoes.aplicacao;

import br.com.trilhaaprovacao.revisoes.dominio.SituacaoDaRevisaoEspacada;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ResultadoDaAgendaDeRevisoes(
        LocalDate dataDeReferencia,
        LocalDate ate,
        List<Revisao> revisoes) {

    public record Revisao(
            UUID identificadorDoTopico,
            String nomeDoTopico,
            UUID identificadorDaMateria,
            String nomeDaMateria,
            int etapa,
            int intervaloEmDias,
            LocalDate dataDevida,
            long diasEmAtraso,
            LocalDate ultimaRevisao,
            Integer ultimaRecordacao,
            SituacaoDaRevisaoEspacada situacao,
            BlocoAberto blocoAberto) {
    }

    public record BlocoAberto(
            UUID identificador,
            UUID identificadorDoPlano,
            LocalDate dataInicialDoPlano,
            LocalDate data,
            String estado) {
    }
}
