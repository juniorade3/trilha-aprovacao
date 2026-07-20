package br.com.trilhaaprovacao.planejamento.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.planejamento.dominio.BlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.DisponibilidadeDoDia;
import br.com.trilhaaprovacao.planejamento.dominio.EstadoDoBlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.EstadoDoPlanoSemanal;
import br.com.trilhaaprovacao.planejamento.dominio.ExecucaoDoBloco;
import br.com.trilhaaprovacao.planejamento.dominio.PlanoSemanal;
import br.com.trilhaaprovacao.planejamento.dominio.PrioridadeDaMateriaNoPlano;
import br.com.trilhaaprovacao.planejamento.dominio.ReplanejadorDeterministicoDePlano;
import br.com.trilhaaprovacao.planejamento.dominio.ReplanejadorDeterministicoDePlano.Decisao;
import br.com.trilhaaprovacao.planejamento.dominio.ReplanejadorDeterministicoDePlano.Previa;
import br.com.trilhaaprovacao.planejamento.dominio.ReplanejadorDeterministicoDePlano.Proposta;
import br.com.trilhaaprovacao.planejamento.dominio.ResultadoDaExecucao;
import br.com.trilhaaprovacao.planejamento.infraestrutura.BlocoDeEstudoPersistido;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDeBlocosDeEstudo;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDeDisponibilidadesDoDia;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDeExecucoesDeBloco;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDePlanosSemanais;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDePrioridadesDeMateriasNoPlano;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDeReplanejamentos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoDeReplanejamento {
    private final RepositorioDePlanosSemanais planos;
    private final RepositorioDeDisponibilidadesDoDia disponibilidades;
    private final RepositorioDeBlocosDeEstudo blocos;
    private final RepositorioDeExecucoesDeBloco execucoes;
    private final RepositorioDePrioridadesDeMateriasNoPlano prioridades;
    private final RepositorioDeReplanejamentos replanejamentos;
    private final ReplanejadorDeterministicoDePlano replanejador =
            new ReplanejadorDeterministicoDePlano();

    public ServicoDeReplanejamento(RepositorioDePlanosSemanais planos,
            RepositorioDeDisponibilidadesDoDia disponibilidades,
            RepositorioDeBlocosDeEstudo blocos,
            RepositorioDeExecucoesDeBloco execucoes,
            RepositorioDePrioridadesDeMateriasNoPlano prioridades,
            RepositorioDeReplanejamentos replanejamentos) {
        this.planos = planos;
        this.disponibilidades = disponibilidades;
        this.blocos = blocos;
        this.execucoes = execucoes;
        this.prioridades = prioridades;
        this.replanejamentos = replanejamentos;
    }

    @Transactional(readOnly = true)
    public ResultadoDaPreviaDoReplanejamento gerarPrevia(UUID usuario, UUID plano,
            LocalDate referencia, Set<UUID> ignoradas) {
        Contexto contexto = carregar(usuario, plano, referencia, true);
        return resultado(contexto, calcular(contexto, ignoradas), ignoradas);
    }

    @Transactional
    public ResultadoDaAplicacaoDoReplanejamento aplicar(UUID usuario, UUID plano,
            LocalDate referencia, Set<UUID> ignoradas, Set<UUID> confirmadas,
            String assinaturaInformada) {
        planos.encontrarParaAtualizacao(plano, usuario).orElseThrow(this::naoEncontrado);
        replanejamentos.bloquearEstadoDoPlano(plano);
        Contexto contexto = carregar(usuario, plano, referencia, true);
        Previa previa = calcular(contexto, ignoradas);
        ResultadoDaPreviaDoReplanejamento resposta = resultado(contexto, previa, ignoradas);
        if (assinaturaInformada == null
                || !MessageDigest.isEqual(assinaturaInformada.getBytes(StandardCharsets.UTF_8),
                        resposta.assinaturaDaPrevia().getBytes(StandardCharsets.UTF_8))) {
            throw new ConflitoDeDominio("PREVIA_DE_REPLANEJAMENTO_DESATUALIZADA",
                    "A previa mudou. Recalcule antes de aplicar.");
        }
        List<Proposta> aplicaveis = previa.propostas().stream()
                .filter(p -> p.decisao() == Decisao.ADIAR || p.decisao() == Decisao.DIVIDIR)
                .toList();
        for (Proposta proposta : aplicaveis) {
            if (proposta.exigeConfirmacao()
                    && !confirmadas.contains(proposta.pendencia().bloco().identificador())) {
                throw new RegraDeDominio("CONFIRMACAO_DE_REAGENDAMENTO_OBRIGATORIA",
                        "Confirme individualmente pendencias com tres reagendamentos.");
            }
        }
        if (aplicaveis.isEmpty()) {
            throw new RegraDeDominio("REPLANEJAMENTO_SEM_TRANSFERENCIAS",
                    "A previa nao possui pendencias que possam ser transferidas.");
        }
        Map<LocalDate, Integer> proximasOrdens = contexto.blocos().stream()
                .collect(Collectors.toMap(BlocoDeEstudo::data, BlocoDeEstudo::ordem,
                        Integer::max, HashMap::new));
        Map<UUID, List<BlocoDeEstudo>> criadosPorOrigem = new HashMap<>();
        List<BlocoDeEstudoPersistido> persistidos = new ArrayList<>();
        for (Proposta proposta : aplicaveis) {
            List<BlocoDeEstudo> criados = proposta.fragmentos().stream().map(fragmento -> {
                int ordem = proximasOrdens.merge(fragmento.data(), 1, Integer::sum);
                return BlocoDeEstudo.criarReplanejado(proposta.pendencia().bloco(),
                        fragmento.data(), fragmento.minutos(), ordem, proposta.justificativa());
            }).toList();
            criadosPorOrigem.put(proposta.pendencia().bloco().identificador(), criados);
            criados.stream().map(BlocoDeEstudoPersistido::new).forEach(persistidos::add);
        }
        blocos.saveAllAndFlush(persistidos);
        UUID identificador = replanejamentos.registrar(plano, referencia,
                aplicaveis, criadosPorOrigem);
        ResultadoDoPlanoSemanal atualizado = new ResultadoDoPlanoSemanal(contexto.plano(),
                contexto.disponibilidades(), blocos
                        .findByIdentificadorDoPlanoOrderByDataAscOrdemAsc(plano)
                        .stream().map(BlocoDeEstudoPersistido::paraDominio).toList());
        return new ResultadoDaAplicacaoDoReplanejamento(identificador,
                OffsetDateTime.now(), atualizado, aplicaveis.size(), persistidos.size());
    }

    @Transactional(readOnly = true)
    public ResultadoDoHistoricoSemanal obterHistorico(UUID usuario, UUID plano,
            LocalDate referencia) {
        Contexto contexto = carregar(usuario, plano, referencia, false);
        var snapshots = replanejamentos.snapshots(plano);
        var transferencias = replanejamentos.transferencias(plano);
        Set<UUID> transferidos = replanejamentos.blocosJaTransferidos(plano);
        int planejados = snapshots.stream().mapToInt(
                RepositorioDeReplanejamentos.Snapshot::duracaoPrevistaEmMinutos).sum();
        int executados = contexto.execucoes().values().stream()
                .map(ExecucaoDoBloco::duracaoExecutadaEmMinutos)
                .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
        int concluidos = contexto.execucoes().values().stream()
                .filter(e -> e.resultado() == ResultadoDaExecucao.CONCLUIDO)
                .mapToInt(e -> e.duracaoExecutadaEmMinutos()).sum();
        int interrompidos = contexto.execucoes().values().stream()
                .filter(e -> e.resultado() == ResultadoDaExecucao.PARCIALMENTE_CONCLUIDO)
                .mapToInt(e -> e.duracaoExecutadaEmMinutos()).sum();
        List<ResultadoDoHistoricoSemanal.PendenciaAtual> pendenciasAtuais = contexto.blocos()
                .stream().filter(b -> !transferidos.contains(b.identificador()))
                .filter(b -> b.estado() == EstadoDoBlocoDeEstudo.PLANEJADO
                        || b.estado() == EstadoDoBlocoDeEstudo.PARCIALMENTE_CONCLUIDO)
                .map(b -> {
                    ExecucaoDoBloco e = contexto.execucoes().get(b.identificador());
                    int feito = e == null || e.duracaoExecutadaEmMinutos() == null
                            ? 0 : e.duracaoExecutadaEmMinutos();
                    return new ResultadoDoHistoricoSemanal.PendenciaAtual(b.identificador(),
                            b.titulo(), b.data(),
                            Math.max(0, b.duracaoPrevistaEmMinutos() - feito), b.estado().name());
                }).filter(p -> p.minutosPendentes() > 0).toList();
        int pendentes = pendenciasAtuais.stream()
                .mapToInt(ResultadoDoHistoricoSemanal.PendenciaAtual::minutosPendentes).sum();
        BigDecimal taxa = planejados == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(executados).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(planejados), 2, RoundingMode.HALF_UP);
        var resumo = new ResultadoDoHistoricoSemanal.Resumo(planejados, executados,
                concluidos, interrompidos, pendentes,
                (int) contexto.blocos().stream().filter(
                        b -> b.estado() == EstadoDoBlocoDeEstudo.CONCLUIDO).count(),
                (int) contexto.blocos().stream().filter(
                        b -> b.estado() == EstadoDoBlocoDeEstudo.PARCIALMENTE_CONCLUIDO).count(),
                (int) contexto.blocos().stream().filter(
                        b -> b.estado() == EstadoDoBlocoDeEstudo.PLANEJADO).count(),
                (int) contexto.blocos().stream().filter(
                        b -> b.quantidadeDeReagendamentos() > 0).count(), taxa);
        return new ResultadoDoHistoricoSemanal(plano, referencia,
                contexto.plano().estado().name(), resumo, snapshots,
                contexto.execucoes().values().stream()
                        .sorted(Comparator.comparing(ExecucaoDoBloco::iniciadaEm)
                                .thenComparing(ExecucaoDoBloco::identificador)).toList(),
                contexto.blocos().stream()
                        .filter(b -> b.estado() == EstadoDoBlocoDeEstudo.CANCELADO).toList(),
                transferencias, pendenciasAtuais,
                "Planos ativados antes da V13 refletem o estado encontrado na migracao; "
                        + "alteracoes anteriores nao podem ser reconstruidas.");
    }

    private Contexto carregar(UUID usuario, UUID identificador, LocalDate referencia,
            boolean exigirAtivo) {
        var persistido = planos.findByIdentificadorAndIdentificadorDoUsuario(
                identificador, usuario).orElseThrow(this::naoEncontrado);
        PlanoSemanal plano = persistido.paraDominio();
        if (exigirAtivo && plano.estado() != EstadoDoPlanoSemanal.ATIVO) {
            throw new ConflitoDeDominio("PLANO_SEMANAL_NAO_ATIVO",
                    "Somente o plano ativo pode ser replanejado.");
        }
        if (!plano.contem(referencia)) {
            throw new RegraDeDominio("DATA_DE_REFERENCIA_FORA_DA_SEMANA",
                    "A data de referencia deve pertencer a semana do plano.");
        }
        List<DisponibilidadeDoDia> dias = disponibilidades
                .findByIdentificadorDoPlanoOrderByDataAsc(identificador).stream()
                .map(item -> item.paraDominio()).toList();
        List<BlocoDeEstudo> lista = blocos
                .findByIdentificadorDoPlanoOrderByDataAscOrdemAsc(identificador).stream()
                .map(BlocoDeEstudoPersistido::paraDominio).toList();
        Map<UUID, ExecucaoDoBloco> porBloco = execucoes
                .encontrarDoPlanoEUsuario(identificador, usuario).stream()
                .map(item -> item.paraDominio())
                .collect(Collectors.toMap(ExecucaoDoBloco::identificadorDoBloco, e -> e));
        Map<UUID, PrioridadeDaMateriaNoPlano> mapaPrioridades = prioridades
                .findByIdentificadorDoPlano(identificador).stream()
                .map(item -> item.paraDominio())
                .collect(Collectors.toMap(item -> item.identificadorDaMateria(),
                        item -> item.prioridade()));
        return new Contexto(plano, referencia, dias, lista, porBloco, mapaPrioridades,
                replanejamentos.blocosJaTransferidos(identificador));
    }

    private Previa calcular(Contexto contexto, Set<UUID> ignoradas) {
        return replanejador.replanejar(contexto.referencia(),
                contexto.disponibilidades(), contexto.blocos(), contexto.execucoes(),
                contexto.prioridades(), contexto.transferidos(), ignoradas);
    }

    private ResultadoDaPreviaDoReplanejamento resultado(Contexto contexto,
            Previa previa, Set<UUID> ignoradas) {
        Map<LocalDate, Integer> alocados = previa.propostas().stream()
                .flatMap(p -> p.fragmentos().stream())
                .collect(Collectors.groupingBy(f -> f.data(),
                        Collectors.summingInt(f -> f.minutos())));
        var capacidades = previa.capacidades().stream().map(dia -> {
            int novos = alocados.getOrDefault(dia.data(), 0);
            return new ResultadoDaPreviaDoReplanejamento.Capacidade(dia.data(),
                    dia.capacidade(), dia.minutosOcupados() - novos, novos,
                    dia.minutosLivres(), dia.materias().size());
        }).toList();
        Set<UUID> idsPendentes = previa.pendencias().stream()
                .map(p -> p.bloco().identificador()).collect(Collectors.toSet());
        var preservados = contexto.blocos().stream()
                .filter(b -> !idsPendentes.contains(b.identificador()))
                .filter(b -> !b.data().isBefore(previa.dataDeReferencia())
                        && !b.data().isAfter(previa.domingo()))
                .map(b -> new ResultadoDaPreviaDoReplanejamento.BlocoPreservado(
                        b.identificador(), b.titulo(), b.data(),
                        b.duracaoPrevistaEmMinutos(), b.estado().name())).toList();
        var pendencias = previa.propostas().stream().map(p ->
                new ResultadoDaPreviaDoReplanejamento.Pendencia(
                        p.pendencia().bloco().identificador(),
                        p.pendencia().bloco().identificadorDaMateria(),
                        p.pendencia().bloco().identificadorDoTopico(),
                        p.pendencia().bloco().titulo(), p.pendencia().bloco().data(),
                        p.pendencia().bloco().ordem(),
                        p.pendencia().bloco().duracaoPrevistaEmMinutos(),
                        p.pendencia().minutosExecutados(), p.pendencia().minutosPendentes(),
                        p.pendencia().bloco().quantidadeDeReagendamentos(),
                        p.pendencia().prioridade().name(), p.pendencia().motivo().name(),
                        p.decisao().name(), p.exigeConfirmacao(), p.minutosNaoAlocados(),
                        p.justificativa(), p.fragmentos().stream().map(f ->
                                new ResultadoDaPreviaDoReplanejamento.Fragmento(
                                        f.data(), f.minutos(), f.sequencia())).toList(),
                        p.decisao() == Decisao.DECIDIR_MANUALMENTE
                                ? List.of("REDUZIR", "CANCELAR_MANUALMENTE", "MANTER_PENDENTE")
                                : List.of())).toList();
        int minutosPendentes = previa.pendencias().stream()
                .mapToInt(ReplanejadorDeterministicoDePlano.Pendencia::minutosPendentes).sum();
        int minutosAlocados = alocados.values().stream().mapToInt(Integer::intValue).sum();
        int naoAlocados = previa.propostas().stream().mapToInt(Proposta::minutosNaoAlocados).sum();
        int fragmentos = previa.propostas().stream().mapToInt(p -> p.fragmentos().size()).sum();
        int confirmacoes = (int) previa.propostas().stream()
                .filter(Proposta::exigeConfirmacao).count();
        String assinatura = assinar(contexto, previa, ignoradas);
        return new ResultadoDaPreviaDoReplanejamento(contexto.plano().identificador(),
                previa.dataDeReferencia(), previa.domingo(), assinatura,
                new ResultadoDaPreviaDoReplanejamento.Resumo(previa.pendencias().size(),
                        fragmentos, minutosPendentes, minutosAlocados,
                        naoAlocados, confirmacoes), capacidades, preservados, pendencias);
    }

    private String assinar(Contexto contexto, Previa previa, Set<UUID> ignoradas) {
        try {
            StringBuilder valor = new StringBuilder().append(contexto.plano().identificador())
                    .append('|').append(contexto.plano().versao()).append('|')
                    .append(previa.dataDeReferencia());
            ignoradas.stream().sorted().forEach(id -> valor.append("|i:").append(id));
            contexto.disponibilidades().stream().sorted(Comparator.comparing(
                    DisponibilidadeDoDia::data)).forEach(d -> valor.append("|d:")
                    .append(d.data()).append(':').append(d.minutosDisponiveis())
                    .append(':').append(d.versao()));
            contexto.blocos().stream().sorted(Comparator.comparing(BlocoDeEstudo::identificador))
                    .forEach(b -> valor.append("|b:").append(b.identificador()).append(':')
                            .append(b.data()).append(':').append(b.ordem()).append(':')
                            .append(b.duracaoPrevistaEmMinutos()).append(':').append(b.estado())
                            .append(':').append(b.quantidadeDeReagendamentos()).append(':')
                            .append(b.versao()));
            contexto.execucoes().values().stream().sorted(Comparator.comparing(
                    ExecucaoDoBloco::identificador)).forEach(e -> valor.append("|e:")
                    .append(e.identificador()).append(':').append(e.resultado()).append(':')
                    .append(e.duracaoExecutadaEmMinutos()).append(':').append(e.versao()));
            contexto.transferidos().stream().sorted().forEach(id -> valor.append("|t:").append(id));
            previa.propostas().forEach(p -> valor.append("|p:")
                    .append(p.pendencia().bloco().identificador()).append(':')
                    .append(p.decisao()).append(':').append(p.fragmentos()));
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(valor.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException excecao) {
            throw new IllegalStateException(excecao);
        }
    }

    private RecursoNaoEncontrado naoEncontrado() {
        return new RecursoNaoEncontrado("PLANO_SEMANAL_NAO_ENCONTRADO",
                "Plano semanal nao encontrado.");
    }

    private record Contexto(PlanoSemanal plano, LocalDate referencia,
            List<DisponibilidadeDoDia> disponibilidades,
            List<BlocoDeEstudo> blocos, Map<UUID, ExecucaoDoBloco> execucoes,
            Map<UUID, PrioridadeDaMateriaNoPlano> prioridades, Set<UUID> transferidos) { }
}
