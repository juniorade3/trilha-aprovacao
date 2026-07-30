package br.com.trilhaaprovacao.revisoes.api;

import br.com.trilhaaprovacao.revisoes.aplicacao.ResultadoDaAgendaDeRevisoes;
import br.com.trilhaaprovacao.revisoes.dominio.ConfiguracaoDaFilaDeRevisoes;
import br.com.trilhaaprovacao.revisoes.dominio.SituacaoDaRevisaoEspacada;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Agenda deterministica de revisoes dos topicos oficiais.")
public record RespostaDeRevisoesEspacadas(
        LocalDate dataDeReferencia,
        LocalDate ate,
        CapacidadeDaFila capacidadeDaFila,
        List<Revisao> revisoes) {

    public static RespostaDeRevisoesEspacadas de(ResultadoDaAgendaDeRevisoes resultado) {
        return new RespostaDeRevisoesEspacadas(resultado.dataDeReferencia(), resultado.ate(),
                new CapacidadeDaFila(ConfiguracaoDaFilaDeRevisoes.LIMITE_DE_PRIORIDADES_DA_FILA,
                        ConfiguracaoDaFilaDeRevisoes
                                .DURACAO_ESTIMADA_POR_REVISAO_EM_MINUTOS),
                resultado.revisoes().stream().map(RespostaDeRevisoesEspacadas::revisao)
                        .toList());
    }

    @Schema(name = "CapacidadeDaFilaDeRevisoes")
    public record CapacidadeDaFila(
            int limiteDePrioridades,
            int duracaoEstimadaPorRevisaoEmMinutos) {
    }

    private static Revisao revisao(ResultadoDaAgendaDeRevisoes.Revisao revisao) {
        return new Revisao(revisao.identificadorDoTopico(), revisao.nomeDoTopico(),
                revisao.identificadorDaMateria(), revisao.nomeDaMateria(), revisao.etapa(),
                revisao.intervaloEmDias(), revisao.dataDevida(), revisao.diasEmAtraso(),
                revisao.ultimaRevisao(), revisao.ultimaRecordacao(), revisao.situacao(),
                bloco(revisao.blocoAberto()));
    }

    private static BlocoAberto bloco(
            ResultadoDaAgendaDeRevisoes.BlocoAberto bloco) {
        if (bloco == null) {
            return null;
        }
        return new BlocoAberto(bloco.identificador(), bloco.identificadorDoPlano(),
                bloco.dataInicialDoPlano(), bloco.data(), bloco.estado());
    }

    @Schema(name = "RevisaoEspacada")
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

    @Schema(name = "BlocoAbertoDaRevisao")
    public record BlocoAberto(
            UUID identificador,
            UUID identificadorDoPlano,
            LocalDate dataInicialDoPlano,
            LocalDate data,
            String estado) {
    }
}
