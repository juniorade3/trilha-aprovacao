package br.com.trilhaaprovacao.planejamento.aplicacao;

import br.com.trilhaaprovacao.planejamento.dominio.DisponibilidadeDoDia;
import br.com.trilhaaprovacao.planejamento.dominio.BlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.PlanoSemanal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ResultadoDoPlanoSemanal(
        PlanoSemanal plano,
        List<DisponibilidadeDoDia> disponibilidades,
        List<BlocoDeEstudo> blocos,
        int totalDeMinutosDisponiveis,
        int totalDeMinutosPlanejados,
        int quantidadeDeBlocos,
        boolean possuiExcesso,
        List<ResumoDoDiaPlanejado> resumosDosDias) {

    public ResultadoDoPlanoSemanal(PlanoSemanal plano,
            List<DisponibilidadeDoDia> disponibilidades,
            List<BlocoDeEstudo> blocos) {
        this(plano, List.copyOf(disponibilidades), List.copyOf(blocos),
                totalDisponivel(disponibilidades), totalPlanejado(blocos), blocos.size(),
                resumos(disponibilidades, blocos).stream()
                        .anyMatch(ResumoDoDiaPlanejado::possuiExcesso),
                resumos(disponibilidades, blocos));
    }

    private static int totalDisponivel(List<DisponibilidadeDoDia> disponibilidades) {
        return disponibilidades.stream()
                .mapToInt(DisponibilidadeDoDia::minutosDisponiveis).sum();
    }

    private static int totalPlanejado(List<BlocoDeEstudo> blocos) {
        return blocos.stream().mapToInt(BlocoDeEstudo::duracaoPrevistaEmMinutos).sum();
    }

    private static List<ResumoDoDiaPlanejado> resumos(
            List<DisponibilidadeDoDia> disponibilidades, List<BlocoDeEstudo> blocos) {
        Map<java.time.LocalDate, Integer> planejadoPorData = blocos.stream()
                .collect(Collectors.groupingBy(BlocoDeEstudo::data,
                        Collectors.summingInt(BlocoDeEstudo::duracaoPrevistaEmMinutos)));
        return disponibilidades.stream().sorted(Comparator.comparing(DisponibilidadeDoDia::data))
                .map(dia -> {
                    int planejado = planejadoPorData.getOrDefault(dia.data(), 0);
                    int saldo = dia.minutosDisponiveis() - planejado;
                    return new ResumoDoDiaPlanejado(dia.data(), dia.minutosDisponiveis(),
                            planejado, saldo, saldo < 0);
                }).toList();
    }
}
