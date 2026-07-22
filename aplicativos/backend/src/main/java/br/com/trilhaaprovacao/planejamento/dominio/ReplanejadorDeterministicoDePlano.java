package br.com.trilhaaprovacao.planejamento.dominio;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ReplanejadorDeterministicoDePlano {
    public Previa replanejar(LocalDate dataDeReferencia,
            List<DisponibilidadeDoDia> disponibilidades,
            List<BlocoDeEstudo> blocos,
            Map<UUID, ExecucaoDoBloco> execucoes,
            Map<UUID, PrioridadeDaMateriaNoPlano> prioridades,
            Set<UUID> blocosJaTransferidos,
            Set<UUID> pendenciasIgnoradas) {
        if (dataDeReferencia == null) throw new IllegalArgumentException("Data obrigatoria.");
        LocalDate domingo = dataDeReferencia.plusDays(7 - dataDeReferencia.getDayOfWeek().getValue());
        Map<LocalDate, DistribuidorDeterministicoDeCapacidade.Dia> dias = new LinkedHashMap<>();
        disponibilidades.stream().filter(d -> !d.data().isBefore(dataDeReferencia)
                        && !d.data().isAfter(domingo))
                .sorted(Comparator.comparing(DisponibilidadeDoDia::data))
                .forEach(d -> dias.put(d.data(), criarDia(d, blocos, execucoes)));

        List<Pendencia> pendencias = blocos.stream()
                .filter(b -> !blocosJaTransferidos.contains(b.identificador()))
                .map(b -> pendencia(b, execucoes.get(b.identificador()), dataDeReferencia,
                        b.identificadorDaMateria() == null
                                ? PrioridadeDaMateriaNoPlano.NORMAL
                                : prioridades.getOrDefault(b.identificadorDaMateria(),
                                        PrioridadeDaMateriaNoPlano.NORMAL)))
                .filter(java.util.Objects::nonNull)
                .sorted(ordemDasPendencias()).toList();

        List<Proposta> propostas = new ArrayList<>();
        for (Pendencia pendencia : pendencias) {
            if (pendenciasIgnoradas.contains(pendencia.bloco().identificador())) {
                propostas.add(new Proposta(pendencia, Decisao.IGNORAR, List.of(),
                        pendencia.minutosPendentes(), false,
                        "Pendencia removida desta previa pelo usuario."));
                continue;
            }
            if (pendencia.bloco().quantidadeDeReagendamentos() > 3) {
                propostas.add(new Proposta(pendencia, Decisao.DECIDIR_MANUALMENTE,
                        List.of(), pendencia.minutosPendentes(), false,
                        "Mais de tres reagendamentos: reduza, cancele manualmente ou mantenha pendente."));
                continue;
            }
            Proposta inteira = tentarInteira(pendencia, dias);
            if (inteira != null) {
                propostas.add(inteira);
                continue;
            }
            Proposta dividida = tentarDividir(pendencia, dias);
            propostas.add(dividida == null
                    ? new Proposta(pendencia, Decisao.SEM_CAPACIDADE, List.of(),
                            pendencia.minutosPendentes(), false,
                            "Nao ha capacidade para transferir todos os minutos nesta semana.")
                    : dividida);
        }
        return new Previa(dataDeReferencia, domingo, List.copyOf(dias.values()),
                List.copyOf(pendencias), List.copyOf(propostas));
    }

    private DistribuidorDeterministicoDeCapacidade.Dia criarDia(
            DisponibilidadeDoDia disponibilidade, List<BlocoDeEstudo> blocos,
            Map<UUID, ExecucaoDoBloco> execucoes) {
        int ocupados = 0;
        Set<UUID> materias = new HashSet<>();
        for (BlocoDeEstudo bloco : blocos) {
            if (!bloco.data().equals(disponibilidade.data())
                    || bloco.estado() == EstadoDoBlocoDeEstudo.CANCELADO) continue;
            ExecucaoDoBloco execucao = execucoes.get(bloco.identificador());
            if ((bloco.estado() == EstadoDoBlocoDeEstudo.CONCLUIDO
                    || bloco.estado() == EstadoDoBlocoDeEstudo.PARCIALMENTE_CONCLUIDO)
                    && execucao != null && execucao.duracaoExecutadaEmMinutos() != null) {
                ocupados += execucao.duracaoExecutadaEmMinutos();
            } else {
                ocupados += bloco.duracaoPrevistaEmMinutos();
            }
            if (bloco.identificadorDaMateria() != null
                    && bloco.tipoDeAtividade() != TipoDeAtividade.REVISAO) {
                materias.add(bloco.identificadorDaMateria());
            }
        }
        return new DistribuidorDeterministicoDeCapacidade.Dia(disponibilidade.data(),
                disponibilidade.minutosDisponiveis(), ocupados, materias);
    }

    private Pendencia pendencia(BlocoDeEstudo bloco, ExecucaoDoBloco execucao,
            LocalDate referencia, PrioridadeDaMateriaNoPlano prioridade) {
        if (bloco.data().isAfter(referencia)
                || bloco.estado() == EstadoDoBlocoDeEstudo.CONCLUIDO
                || bloco.estado() == EstadoDoBlocoDeEstudo.CANCELADO
                || bloco.estado() == EstadoDoBlocoDeEstudo.EM_ANDAMENTO) return null;
        int executados = execucao == null || execucao.duracaoExecutadaEmMinutos() == null
                ? 0 : execucao.duracaoExecutadaEmMinutos();
        int pendentes = Math.max(0, bloco.duracaoPrevistaEmMinutos() - executados);
        if (pendentes == 0) return null;
        Motivo motivo;
        if (bloco.estado() == EstadoDoBlocoDeEstudo.PARCIALMENTE_CONCLUIDO) {
            motivo = Motivo.EXECUCAO_PARCIAL;
        } else if (bloco.estado() == EstadoDoBlocoDeEstudo.PLANEJADO
                && bloco.quantidadeDeReagendamentos() > 0 && bloco.data().isBefore(referencia)) {
            motivo = Motivo.REAGENDAMENTO_VENCIDO;
        } else if (bloco.estado() == EstadoDoBlocoDeEstudo.PLANEJADO
                && bloco.data().isBefore(referencia)) {
            motivo = Motivo.NAO_INICIADO;
        } else return null;
        long atraso = Math.max(0, ChronoUnit.DAYS.between(bloco.data(), referencia));
        return new Pendencia(bloco, prioridade, motivo, executados, pendentes, atraso);
    }

    private Comparator<Pendencia> ordemDasPendencias() {
        return Comparator.comparingInt((Pendencia p) -> p.bloco().quantidadeDeReagendamentos())
                .thenComparing((Pendencia p) -> p.prioridade().peso(), Comparator.reverseOrder())
                .thenComparing(Pendencia::diasDeAtraso, Comparator.reverseOrder())
                .thenComparingInt(Pendencia::minutosPendentes)
                .thenComparing(p -> p.bloco().data())
                .thenComparingInt(p -> p.bloco().ordem())
                .thenComparing(p -> p.bloco().identificador());
    }

    private Proposta tentarInteira(Pendencia pendencia,
            Map<LocalDate, DistribuidorDeterministicoDeCapacidade.Dia> dias) {
        for (var dia : dias.values()) {
            boolean contaComoMateria = contaParaLimiteDeMaterias(pendencia.bloco());
            if (dia.comporta(pendencia.bloco().identificadorDaMateria(),
                    pendencia.minutosPendentes(), contaComoMateria)) {
                dia.alocar(pendencia.bloco().identificadorDaMateria(),
                        pendencia.minutosPendentes(), contaComoMateria);
                boolean confirma = pendencia.bloco().quantidadeDeReagendamentos() == 3;
                return new Proposta(pendencia, Decisao.ADIAR,
                        List.of(new Fragmento(dia.data(), pendencia.minutosPendentes(), 1)),
                        0, confirma, "Pendencia integral alocada no primeiro dia compativel.");
            }
        }
        return null;
    }

    private Proposta tentarDividir(Pendencia pendencia,
            Map<LocalDate, DistribuidorDeterministicoDeCapacidade.Dia> dias) {
        if (pendencia.minutosPendentes() < DistribuidorDeterministicoDeCapacidade.DURACAO_MINIMA) {
            return null;
        }
        List<AlocacaoTemporaria> temporarias = new ArrayList<>();
        int restante = pendencia.minutosPendentes();
        boolean contaComoMateria = contaParaLimiteDeMaterias(pendencia.bloco());
        for (var dia : dias.values()) {
            if (!dia.comporta(pendencia.bloco().identificadorDaMateria(),
                    DistribuidorDeterministicoDeCapacidade.DURACAO_MINIMA,
                    contaComoMateria)) continue;
            int minutos = Math.min(restante, dia.minutosLivres());
            int sobra = restante - minutos;
            if (sobra > 0 && sobra < DistribuidorDeterministicoDeCapacidade.DURACAO_MINIMA) {
                minutos -= DistribuidorDeterministicoDeCapacidade.DURACAO_MINIMA - sobra;
            }
            if (minutos >= DistribuidorDeterministicoDeCapacidade.DURACAO_MINIMA) {
                temporarias.add(new AlocacaoTemporaria(dia, minutos));
                restante -= minutos;
            }
            if (restante == 0) break;
        }
        if (restante != 0 || temporarias.size() < 2) return null;
        List<Fragmento> fragmentos = new ArrayList<>();
        int sequencia = 1;
        for (AlocacaoTemporaria temporaria : temporarias) {
            temporaria.dia().alocar(pendencia.bloco().identificadorDaMateria(),
                    temporaria.minutos(), contaComoMateria);
            fragmentos.add(new Fragmento(temporaria.dia().data(), temporaria.minutos(), sequencia++));
        }
        return new Proposta(pendencia, Decisao.DIVIDIR, fragmentos, 0,
                pendencia.bloco().quantidadeDeReagendamentos() == 3,
                "Pendencia dividida integralmente em fragmentos de ao menos 25 minutos.");
    }

    private boolean contaParaLimiteDeMaterias(BlocoDeEstudo bloco) {
        return bloco.tipoDeAtividade() != TipoDeAtividade.REVISAO;
    }

    private record AlocacaoTemporaria(DistribuidorDeterministicoDeCapacidade.Dia dia,
            int minutos) { }

    public enum Decisao { ADIAR, DIVIDIR, DECIDIR_MANUALMENTE, SEM_CAPACIDADE, IGNORAR }
    public enum Motivo { NAO_INICIADO, EXECUCAO_PARCIAL, REAGENDAMENTO_VENCIDO }
    public record Pendencia(BlocoDeEstudo bloco, PrioridadeDaMateriaNoPlano prioridade,
            Motivo motivo, int minutosExecutados, int minutosPendentes,
            long diasDeAtraso) { }
    public record Fragmento(LocalDate data, int minutos, int sequencia) { }
    public record Proposta(Pendencia pendencia, Decisao decisao,
            List<Fragmento> fragmentos, int minutosNaoAlocados,
            boolean exigeConfirmacao, String justificativa) { }
    public record Previa(LocalDate dataDeReferencia, LocalDate domingo,
            List<DistribuidorDeterministicoDeCapacidade.Dia> capacidades,
            List<Pendencia> pendencias, List<Proposta> propostas) { }
}
