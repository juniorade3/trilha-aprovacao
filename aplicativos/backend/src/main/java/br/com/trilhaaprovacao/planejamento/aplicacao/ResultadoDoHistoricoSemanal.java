package br.com.trilhaaprovacao.planejamento.aplicacao;

import br.com.trilhaaprovacao.planejamento.dominio.BlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.ExecucaoDoBloco;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDeReplanejamentos.Snapshot;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDeReplanejamentos.Transferencia;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ResultadoDoHistoricoSemanal(
        UUID identificadorDoPlano,
        LocalDate dataDeReferencia,
        String estadoDoPlano,
        Resumo resumo,
        List<Snapshot> snapshotOriginal,
        List<ExecucaoDoBloco> execucoesReais,
        List<BlocoDeEstudo> cancelamentos,
        List<Transferencia> transferencias,
        List<PendenciaAtual> pendenciasAtuais,
        String observacaoDoSnapshot) {
    public record Resumo(int minutosPlanejados, int minutosExecutados,
            int minutosConcluidos, int minutosInterrompidos, int minutosPendentes,
            int blocosConcluidos, int blocosParciais, int blocosNaoIniciados,
            int blocosReagendados, BigDecimal taxaExecutadaSobrePlanejada) { }
    public record PendenciaAtual(UUID identificadorDoBloco, String titulo,
            LocalDate data, int minutosPendentes, String estado) { }
}
