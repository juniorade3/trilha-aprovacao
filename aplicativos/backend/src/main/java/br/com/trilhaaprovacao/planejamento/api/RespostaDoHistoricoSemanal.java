package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.planejamento.aplicacao.ResultadoDoHistoricoSemanal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RespostaDoHistoricoSemanal(UUID identificadorDoPlano,
        LocalDate dataDeReferencia, String estadoDoPlano, Resumo resumo,
        List<Snapshot> snapshotOriginal, List<Execucao> execucoesReais,
        List<Bloco> cancelamentos, List<Transferencia> transferencias,
        List<Pendencia> pendenciasAtuais, String observacaoDoSnapshot) {
    public static RespostaDoHistoricoSemanal de(ResultadoDoHistoricoSemanal resultado) {
        var r = resultado.resumo();
        return new RespostaDoHistoricoSemanal(resultado.identificadorDoPlano(),
                resultado.dataDeReferencia(), resultado.estadoDoPlano(),
                new Resumo(r.minutosPlanejados(), r.minutosExecutados(),
                        r.minutosConcluidos(), r.minutosInterrompidos(), r.minutosPendentes(),
                        r.blocosConcluidos(), r.blocosParciais(), r.blocosNaoIniciados(),
                        r.blocosReagendados(), r.taxaExecutadaSobrePlanejada()),
                resultado.snapshotOriginal().stream().map(s -> new Snapshot(
                        s.identificadorDoBloco(), s.titulo(), s.data(),
                        s.duracaoPrevistaEmMinutos(), s.ordem(), s.origem(),
                        s.capturadoEm())).toList(),
                resultado.execucoesReais().stream().map(e -> new Execucao(
                        e.identificador(), e.identificadorDoBloco(), e.iniciadaEm(),
                        e.encerradaEm(), e.duracaoExecutadaEmMinutos(),
                        e.resultado() == null ? null : e.resultado().name(),
                        e.identificadorDoRegistroDeEstudo())).toList(),
                resultado.cancelamentos().stream().map(b -> new Bloco(b.identificador(),
                        b.titulo(), b.data(), b.duracaoPrevistaEmMinutos(),
                        b.estado().name())).toList(),
                resultado.transferencias().stream().map(t -> new Transferencia(
                        t.identificadorDoReplanejamento(), t.dataDeReferencia(), t.aplicadoEm(),
                        t.identificadorDoBlocoOriginal(), t.decisao(), t.motivo(),
                        t.minutosPendentes(), t.identificadorDoBlocoCriado(),
                        t.sequencia(), t.data(), t.duracaoEmMinutos())).toList(),
                resultado.pendenciasAtuais().stream().map(p -> new Pendencia(
                        p.identificadorDoBloco(), p.titulo(), p.data(),
                        p.minutosPendentes(), p.estado())).toList(),
                resultado.observacaoDoSnapshot());
    }
    public record Resumo(int minutosPlanejados, int minutosExecutados,
            int minutosConcluidos, int minutosInterrompidos, int minutosPendentes,
            int blocosConcluidos, int blocosParciais, int blocosNaoIniciados,
            int blocosReagendados, BigDecimal taxaExecutadaSobrePlanejada) { }
    public record Snapshot(UUID identificadorDoBloco, String titulo, LocalDate data,
            int duracaoPrevistaEmMinutos, int ordem, String origem,
            OffsetDateTime capturadoEm) { }
    public record Execucao(UUID identificador, UUID identificadorDoBloco,
            OffsetDateTime iniciadaEm, OffsetDateTime encerradaEm,
            Integer duracaoExecutadaEmMinutos, String resultado,
            UUID identificadorDoRegistroDeEstudo) { }
    public record Bloco(UUID identificador, String titulo, LocalDate data,
            int duracaoPrevistaEmMinutos, String estado) { }
    public record Transferencia(UUID identificadorDoReplanejamento,
            LocalDate dataDeReferencia, OffsetDateTime aplicadoEm,
            UUID identificadorDoBlocoOriginal, String decisao, String motivo,
            int minutosPendentes, UUID identificadorDoBlocoCriado, int sequencia,
            LocalDate data, int duracaoEmMinutos) { }
    public record Pendencia(UUID identificadorDoBloco, String titulo, LocalDate data,
            int minutosPendentes, String estado) { }
}
