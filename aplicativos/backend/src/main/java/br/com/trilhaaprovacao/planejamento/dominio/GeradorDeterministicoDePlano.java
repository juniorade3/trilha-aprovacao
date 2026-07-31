package br.com.trilhaaprovacao.planejamento.dominio;

import br.com.trilhaaprovacao.priorizacao.dominio.GrupoDePriorizacao;
import br.com.trilhaaprovacao.revisoes.dominio.ConfiguracaoDaFilaDeRevisoes;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class GeradorDeterministicoDePlano {
    private static final int DURACAO_MINIMA =
            DistribuidorDeterministicoDeCapacidade.DURACAO_MINIMA;
    public static final int DURACAO_DA_REVISAO_ESPECIFICA_EM_MINUTOS =
            ConfiguracaoDaFilaDeRevisoes.DURACAO_ESTIMADA_POR_REVISAO_EM_MINUTOS;
    public static final int LIMITE_DE_REVISOES_ESPECIFICAS_POR_DIA =
            ConfiguracaoDaFilaDeRevisoes.LIMITE_DE_PRIORIDADES_DA_FILA;

    /**
     * Geracao baseada no ranking por topico e na agenda calculada de revisoes.
     * Nenhum dado e consultado ou persistido por este componente puro.
     */
    public PreviaDaGeracaoDaSemana gerar(UUID plano, LocalDate dataDeReferencia,
            List<CandidatoDeMateriaParaGeracao> candidatosInformados,
            List<CandidatoDeTopicoParaGeracao> topicosInformados,
            List<CandidatoDeRevisaoParaGeracao> revisoesInformadas,
            List<EntradaDoDiaParaGeracao> diasInformados,
            ConfiguracaoDaGeracaoDeterministica configuracao) {
        if (plano == null || dataDeReferencia == null || configuracao == null
                || candidatosInformados == null || topicosInformados == null
                || revisoesInformadas == null || diasInformados == null) {
            throw new IllegalArgumentException("Dados da geracao invalidos.");
        }
        List<CandidatoDeMateriaParaGeracao> candidatos = candidatosInformados.stream()
                .filter(item -> item.prioridade() != PrioridadeDaMateriaNoPlano.NAO_INCLUIR)
                .sorted(ordemEstavel()).toList();
        Set<UUID> materiasIncluidas = candidatos.stream()
                .map(CandidatoDeMateriaParaGeracao::identificadorDaMateria)
                .collect(java.util.stream.Collectors.toSet());
        List<CandidatoDeTopicoParaGeracao> topicos = topicosInformados.stream()
                .filter(item -> materiasIncluidas.contains(item.identificadorDaMateria()))
                .sorted(ordemDosTopicos()).toList();
        if (topicos.isEmpty()) throw new SemTopicosElegiveisParaGeracao();
        int metaDeMaterias = configuracao.quantidadeDeMateriasPorDia();

        List<EntradaDoDiaParaGeracao> entradas = ordenarEValidarDias(diasInformados);
        LocalDate ultimoDia = entradas.getLast().data();
        Set<UUID> topicosOficiais = topicos.stream()
                .map(CandidatoDeTopicoParaGeracao::identificadorDoTopico)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> revisoesJaPreservadas = entradas.stream()
                .flatMap(entrada -> entrada.blocosPreservados().stream())
                .filter(item -> item.tipoDeAtividade() == TipoDeAtividade.REVISAO)
                .map(BlocoPreservadoNaGeracao::identificadorDoTopico)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        List<CandidatoDeRevisaoParaGeracao> revisoes = revisoesInformadas.stream()
                .filter(item -> materiasIncluidas.contains(item.identificadorDaMateria()))
                .filter(item -> !item.possuiBlocoAberto())
                .filter(item -> !revisoesJaPreservadas.contains(item.identificadorDoTopico()))
                .sorted(ordemDasRevisoes()).toList();

        Map<UUID, Integer> carga = new HashMap<>();
        Map<UUID, Integer> ocorrencias = new HashMap<>();
        for (EntradaDoDiaParaGeracao entrada : entradas) {
            entrada.blocosPreservados().stream()
                    .filter(this::eBlocoPrincipalComMateria)
                    .forEach(item -> {
                        carga.merge(item.identificadorDaMateria(),
                                item.duracaoEmMinutos(), Integer::sum);
                        ocorrencias.merge(item.identificadorDaMateria(), 1, Integer::sum);
                    });
        }

        List<DiaDaPreviaDaGeracao> dias = new ArrayList<>();
        List<JustificativaDaGeracao> avisosDaSemana = new ArrayList<>();
        Set<UUID> materiasDoDiaAnterior = Set.of();
        Set<UUID> revisoesAlocadas = new HashSet<>(revisoesJaPreservadas);
        Map<UUID, Integer> ocorrenciasComTopicoOficial = new HashMap<>();

        for (EntradaDoDiaParaGeracao entrada : entradas) {
            List<BlocoPreservadoNaGeracao> preservados = entrada.blocosPreservados().stream()
                    .sorted(Comparator.comparingInt(BlocoPreservadoNaGeracao::ordem)
                            .thenComparing(BlocoPreservadoNaGeracao::identificador))
                    .toList();
            int minutosPreservados = preservados.stream()
                    .mapToInt(BlocoPreservadoNaGeracao::duracaoEmMinutos).sum();
            int livres = Math.max(0, entrada.minutosDisponiveis() - minutosPreservados);
            List<BlocoSugerido> sugeridos = new ArrayList<>();
            List<JustificativaDaGeracao> avisos = new ArrayList<>();
            Set<UUID> materiasDoDia = preservados.stream()
                    .filter(this::eBlocoPrincipalComMateria)
                    .map(BlocoPreservadoNaGeracao::identificadorDaMateria)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            Set<UUID> topicosPrincipaisPreservados = preservados.stream()
                    .filter(item -> item.tipoDeAtividade() != TipoDeAtividade.REVISAO)
                    .map(BlocoPreservadoNaGeracao::identificadorDoTopico)
                    .filter(topicosOficiais::contains)
                    .collect(java.util.stream.Collectors.toSet());
            Set<UUID> materiasOficiaisPreservadas = preservados.stream()
                    .filter(item -> item.tipoDeAtividade() != TipoDeAtividade.REVISAO)
                    .filter(item -> topicosOficiais.contains(item.identificadorDoTopico()))
                    .map(BlocoPreservadoNaGeracao::identificadorDaMateria)
                    .collect(java.util.stream.Collectors.toSet());
            materiasOficiaisPreservadas.forEach(materia ->
                    ocorrenciasComTopicoOficial.merge(materia, 1, Integer::sum));
            Set<UUID> topicosComRevisaoNoDia = preservados.stream()
                    .filter(item -> item.tipoDeAtividade() == TipoDeAtividade.REVISAO)
                    .map(BlocoPreservadoNaGeracao::identificadorDoTopico)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toCollection(HashSet::new));

            if (entrada.data().isBefore(dataDeReferencia)) {
                avisos.add(justificativa("DIA_ANTERIOR_A_REFERENCIA",
                        "Os blocos existentes foram preservados; a data nao recebe novas sugestoes."));
            } else {
                int revisoesPreservadasNoDia = (int) preservados.stream()
                        .filter(item -> item.tipoDeAtividade() == TipoDeAtividade.REVISAO)
                        .filter(item -> item.identificadorDoTopico() != null)
                        .count();
                int vagasDeRevisao = Math.max(0,
                        LIMITE_DE_REVISOES_ESPECIFICAS_POR_DIA - revisoesPreservadasNoDia);
                for (CandidatoDeRevisaoParaGeracao revisao : revisoes) {
                    if (vagasDeRevisao == 0
                            || livres < DURACAO_DA_REVISAO_ESPECIFICA_EM_MINUTOS) break;
                    if (revisoesAlocadas.contains(revisao.identificadorDoTopico())
                            || revisao.dataDevida().isAfter(entrada.data())
                            || topicosPrincipaisPreservados.contains(
                                    revisao.identificadorDoTopico())) continue;
                    sugeridos.add(blocoDeRevisao(revisao));
                    revisoesAlocadas.add(revisao.identificadorDoTopico());
                    topicosComRevisaoNoDia.add(revisao.identificadorDoTopico());
                    livres -= DURACAO_DA_REVISAO_ESPECIFICA_EM_MINUTOS;
                    vagasDeRevisao--;
                }

                int vagas = Math.max(0, metaDeMaterias - materiasDoDia.size());
                List<CandidatoDeMateriaParaGeracao> materiasComTopico = candidatos.stream()
                        .filter(item -> !materiasDoDia.contains(item.identificadorDaMateria()))
                        .filter(item -> possuiTopicoDisponivel(item.identificadorDaMateria(),
                                topicos, topicosComRevisaoNoDia))
                        .toList();
                int quantidade = Math.min(vagas,
                        Math.min(materiasComTopico.size(), livres / DURACAO_MINIMA));
                if (quantidade > 0) {
                    int duracaoBase = Math.min(
                            configuracao.duracaoPadraoDoBlocoPrincipalEmMinutos(),
                            livres / quantidade);
                    int excedenteDistribuivel = Math.min(livres - duracaoBase * quantidade,
                            quantidade * Math.max(0,
                                    configuracao.duracaoPadraoDoBlocoPrincipalEmMinutos()
                                            - duracaoBase));
                    for (int indice = 0; indice < quantidade; indice++) {
                        Predicate<CandidatoDeMateriaParaGeracao> possuiTopico = item ->
                                possuiTopicoDisponivel(item.identificadorDaMateria(),
                                        topicos, topicosComRevisaoNoDia);
                        CandidatoDeMateriaParaGeracao escolhido = escolher(candidatos,
                                materiasDoDia, materiasDoDiaAnterior, carga, ocorrencias,
                                possuiTopico);
                        CandidatoDeTopicoParaGeracao topico = escolherTopico(escolhido,
                                topicos, topicosComRevisaoNoDia,
                                ocorrenciasComTopicoOficial.getOrDefault(
                                        escolhido.identificadorDaMateria(), 0));
                        int duracao = duracaoBase
                                + (indice < excedenteDistribuivel ? 1 : 0);
                        sugeridos.add(blocoPrincipal(escolhido, topico, duracao,
                                materiasDoDiaAnterior, quantidade, metaDeMaterias));
                        materiasDoDia.add(escolhido.identificadorDaMateria());
                        carga.merge(escolhido.identificadorDaMateria(), duracao, Integer::sum);
                        ocorrencias.merge(escolhido.identificadorDaMateria(), 1, Integer::sum);
                        ocorrenciasComTopicoOficial.merge(
                                escolhido.identificadorDaMateria(), 1, Integer::sum);
                        livres -= duracao;
                    }
                }

                if (vagas > 0 && quantidade < Math.min(metaDeMaterias, vagas)) {
                    String codigo = livres < DURACAO_MINIMA
                            ? "DISPONIBILIDADE_INSUFICIENTE"
                            : "POUCOS_TOPICOS_ELEGIVEIS";
                    String mensagem = livres < DURACAO_MINIMA
                            ? "A capacidade nao comporta outro bloco minimo de 25 minutos."
                            : "Ha poucos topicos elegiveis sem conflito para atingir a meta do dia.";
                    avisos.add(justificativa(codigo, mensagem));
                }
            }
            if (livres > 0) {
                avisos.add(justificativa("MINUTOS_LIVRES_NAO_UTILIZADOS",
                        livres + " minuto(s) permanecem livres neste dia."));
            }
            int sugeridosEmMinutos = sugeridos.stream()
                    .mapToInt(BlocoSugerido::duracaoEmMinutos).sum();
            dias.add(new DiaDaPreviaDaGeracao(entrada.data(),
                    new CapacidadeDoDia(entrada.minutosDisponiveis(), minutosPreservados,
                            sugeridosEmMinutos, Math.max(0, entrada.minutosDisponiveis()
                                    - minutosPreservados - sugeridosEmMinutos)),
                    preservados, sugeridos, avisos));
            materiasDoDiaAnterior = Set.copyOf(materiasDoDia);
        }

        long revisoesSemAlocacao = revisoes.stream()
                .filter(item -> !item.dataDevida().isAfter(ultimoDia))
                .filter(item -> !revisoesAlocadas.contains(item.identificadorDoTopico()))
                .count();
        if (revisoesSemAlocacao > 0) {
            avisosDaSemana.add(justificativa("REVISOES_SEM_CAPACIDADE",
                    revisoesSemAlocacao
                            + " revisao(oes) devida(s) nao couberam integralmente na semana."));
        }
        if (candidatos.size() < metaDeMaterias) {
            avisosDaSemana.add(justificativa("POUCAS_MATERIAS_ELEGIVEIS",
                    "A meta de " + metaDeMaterias
                            + " materia(s) por dia depende de materias elegiveis suficientes."));
        }
        return new PreviaDaGeracaoDaSemana(plano, dias, avisosDaSemana);
    }

    private CandidatoDeMateriaParaGeracao escolher(
            List<CandidatoDeMateriaParaGeracao> candidatos, Set<UUID> usadasNoDia,
            Set<UUID> usadasNoDiaAnterior, Map<UUID, Integer> carga,
            Map<UUID, Integer> ocorrencias,
            Predicate<CandidatoDeMateriaParaGeracao> filtro) {
        List<CandidatoDeMateriaParaGeracao> disponiveis = candidatos.stream()
                .filter(item -> !usadasNoDia.contains(item.identificadorDaMateria()))
                .filter(filtro).toList();
        boolean existeAlternativa = disponiveis.stream()
                .anyMatch(item -> !usadasNoDiaAnterior.contains(item.identificadorDaMateria()));
        return disponiveis.stream().min(Comparator
                .comparing((CandidatoDeMateriaParaGeracao item) -> existeAlternativa
                        && usadasNoDiaAnterior.contains(item.identificadorDaMateria()))
                .thenComparing(item -> cargaNormalizada(item, carga))
                .thenComparing(CandidatoDeMateriaParaGeracao::prioridade,
                        Comparator.comparingInt(PrioridadeDaMateriaNoPlano::peso).reversed())
                .thenComparingInt(item -> ocorrencias.getOrDefault(item.identificadorDaMateria(), 0))
                .thenComparing(ordemEstavel()))
                .orElseThrow(() -> new IllegalStateException("Nao ha materia disponivel."));
    }

    private List<EntradaDoDiaParaGeracao> ordenarEValidarDias(
            List<EntradaDoDiaParaGeracao> diasInformados) {
        List<EntradaDoDiaParaGeracao> entradas = diasInformados.stream()
                .sorted(Comparator.comparing(EntradaDoDiaParaGeracao::data)).toList();
        if (entradas.size() != 7 || entradas.stream().map(EntradaDoDiaParaGeracao::data)
                .distinct().count() != 7) {
            throw new IllegalArgumentException("Informe sete dias distintos para a geracao.");
        }
        return entradas;
    }

    private boolean eBlocoPrincipalComMateria(BlocoPreservadoNaGeracao bloco) {
        return bloco.identificadorDaMateria() != null
                && bloco.tipoDeAtividade() != TipoDeAtividade.REVISAO;
    }

    private Comparator<CandidatoDeTopicoParaGeracao> ordemDosTopicos() {
        return Comparator.comparingInt(CandidatoDeTopicoParaGeracao::posicaoNoGrupo)
                .thenComparingInt(CandidatoDeTopicoParaGeracao::ordemOficial)
                .thenComparing(CandidatoDeTopicoParaGeracao::nomeNormalizado)
                .thenComparing(CandidatoDeTopicoParaGeracao::identificadorDoTopico);
    }

    private Comparator<CandidatoDeRevisaoParaGeracao> ordemDasRevisoes() {
        return Comparator.comparing(CandidatoDeRevisaoParaGeracao::dataDevida)
                .thenComparingInt(CandidatoDeRevisaoParaGeracao::etapa)
                .thenComparing(CandidatoDeRevisaoParaGeracao::ultimaRecordacao,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(CandidatoDeRevisaoParaGeracao::ordemDoTopico)
                .thenComparing(CandidatoDeRevisaoParaGeracao::identificadorDoTopico);
    }

    private boolean possuiTopicoDisponivel(UUID materia,
            List<CandidatoDeTopicoParaGeracao> topicos, Set<UUID> topicosBloqueados) {
        return topicos.stream().anyMatch(item -> item.identificadorDaMateria().equals(materia)
                && !topicosBloqueados.contains(item.identificadorDoTopico()));
    }

    private CandidatoDeTopicoParaGeracao escolherTopico(
            CandidatoDeMateriaParaGeracao materia,
            List<CandidatoDeTopicoParaGeracao> topicos,
            Set<UUID> topicosBloqueados, int ocorrenciasAnteriores) {
        GrupoDePriorizacao grupoEsperado = ocorrenciasAnteriores % 2 == 0
                ? GrupoDePriorizacao.LACUNA : GrupoDePriorizacao.FRAQUEZA;
        GrupoDePriorizacao grupoAlternativo = grupoEsperado == GrupoDePriorizacao.LACUNA
                ? GrupoDePriorizacao.FRAQUEZA : GrupoDePriorizacao.LACUNA;
        for (GrupoDePriorizacao grupo : List.of(grupoEsperado, grupoAlternativo,
                GrupoDePriorizacao.CONSOLIDADO)) {
            var encontrado = topicos.stream()
                    .filter(item -> item.identificadorDaMateria()
                            .equals(materia.identificadorDaMateria()))
                    .filter(item -> item.grupoDaPriorizacao() == grupo)
                    .filter(item -> !topicosBloqueados.contains(item.identificadorDoTopico()))
                    .findFirst();
            if (encontrado.isPresent()) return encontrado.get();
        }
        throw new IllegalStateException("Nao ha topico disponivel para a materia.");
    }

    private BlocoSugerido blocoDeRevisao(CandidatoDeRevisaoParaGeracao revisao) {
        return new BlocoSugerido(revisao.identificadorDaMateria(),
                revisao.identificadorDoTopico(), revisao.nomeDaMateria(),
                revisao.nomeDoTopico(), "Revisão de " + revisao.nomeDoTopico(),
                TipoDeAtividade.REVISAO, null, null,
                DURACAO_DA_REVISAO_ESPECIFICA_EM_MINUTOS,
                List.of(
                        justificativa("REVISAO_ESPECIFICA",
                                "Revisao espacada especifica reservada antes dos blocos principais."),
                        justificativa("REVISAO_DEVIDA_EM_" + revisao.dataDevida(),
                                "A revisao respeita a data devida e a ordem deterministica da agenda.")));
    }

    private BlocoSugerido blocoPrincipal(CandidatoDeMateriaParaGeracao materia,
            CandidatoDeTopicoParaGeracao topico, int duracao,
            Set<UUID> materiasDoDiaAnterior, int quantidade,
            int metaDeMaterias) {
        TipoDeAtividade tipo = topico.jaFoiEstudado()
                ? TipoDeAtividade.QUESTOES : TipoDeAtividade.TEORIA;
        List<JustificativaDaGeracao> justificativas = new ArrayList<>(
                justificativas(materia, materiasDoDiaAnterior, quantidade, metaDeMaterias));
        justificativas.add(justificativa(
                "GRUPO_" + topico.grupoDaPriorizacao().name(),
                "Topico selecionado no grupo "
                        + topico.grupoDaPriorizacao().name().toLowerCase() + "."));
        justificativas.add(justificativa(
                "FAIXA_" + topico.faixaDaPriorizacao().name(),
                "Faixa objetiva do ranking: "
                        + topico.faixaDaPriorizacao().name().toLowerCase() + "."));
        justificativas.add(justificativa("TIPO_" + tipo.name(),
                tipo == TipoDeAtividade.TEORIA
                        ? "Teoria indicada porque o topico nunca foi estudado."
                        : "Questoes indicadas porque o topico ja possui estudo."));
        return new BlocoSugerido(materia.identificadorDaMateria(),
                topico.identificadorDoTopico(), materia.nome(), topico.nomeDoTopico(),
                topico.nomeDoTopico(), tipo, topico.grupoDaPriorizacao(),
                topico.faixaDaPriorizacao(), duracao, justificativas);
    }

    private BigDecimal cargaNormalizada(CandidatoDeMateriaParaGeracao candidato,
            Map<UUID, Integer> carga) {
        return BigDecimal.valueOf(carga.getOrDefault(candidato.identificadorDaMateria(), 0))
                .divide(BigDecimal.valueOf(candidato.prioridade().peso()), 8, RoundingMode.HALF_UP);
    }

    private Comparator<CandidatoDeMateriaParaGeracao> ordemEstavel() {
        return Comparator.comparingInt(CandidatoDeMateriaParaGeracao::ordemEstavel)
                .thenComparing(CandidatoDeMateriaParaGeracao::nomeNormalizado)
                .thenComparing(CandidatoDeMateriaParaGeracao::identificadorDaMateria);
    }

    private List<JustificativaDaGeracao> justificativas(
            CandidatoDeMateriaParaGeracao candidato, Set<UUID> materiasDoDiaAnterior,
            int quantidade, int metaDeMaterias) {
        List<JustificativaDaGeracao> resultado = new ArrayList<>();
        resultado.add(justificativa("PRIORIDADE_" + candidato.prioridade().name(),
                "Materia com prioridade " + candidato.prioridade().name().toLowerCase() + "."));
        resultado.add(justificativa("EQUILIBRIO_DA_SEMANA",
                "Escolhida pela menor carga ponderada acumulada na semana."));
        if (!materiasDoDiaAnterior.contains(candidato.identificadorDaMateria())) {
            resultado.add(justificativa("ALTERNANCIA_ENTRE_DIAS",
                    "Favorece alternancia em relacao ao dia anterior."));
        }
        if (quantidade == metaDeMaterias) {
            resultado.add(justificativa("META_DE_MATERIAS_POR_DIA",
                    "Contribui para a meta de " + metaDeMaterias
                            + " materia(s) distinta(s) no dia."));
        }
        return List.copyOf(resultado);
    }

    private JustificativaDaGeracao justificativa(String codigo, String mensagem) {
        return new JustificativaDaGeracao(codigo, mensagem);
    }
}
