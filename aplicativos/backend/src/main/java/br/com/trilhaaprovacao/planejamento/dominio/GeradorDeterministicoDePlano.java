package br.com.trilhaaprovacao.planejamento.dominio;

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

public final class GeradorDeterministicoDePlano {
    private static final int DURACAO_MINIMA =
            DistribuidorDeterministicoDeCapacidade.DURACAO_MINIMA;
    private static final int META_DE_MATERIAS =
            DistribuidorDeterministicoDeCapacidade.LIMITE_DE_MATERIAS;

    public PreviaDaGeracaoDaSemana gerar(UUID plano,
            List<CandidatoDeMateriaParaGeracao> candidatosInformados,
            List<EntradaDoDiaParaGeracao> diasInformados,
            ConfiguracaoDaGeracaoDeterministica configuracao) {
        if (plano == null || configuracao == null) {
            throw new IllegalArgumentException("Dados da geracao invalidos.");
        }
        List<CandidatoDeMateriaParaGeracao> candidatos = candidatosInformados.stream()
                .filter(item -> item.prioridade() != PrioridadeDaMateriaNoPlano.NAO_INCLUIR)
                .sorted(ordemEstavel()).toList();
        List<EntradaDoDiaParaGeracao> entradas = diasInformados.stream()
                .sorted(Comparator.comparing(EntradaDoDiaParaGeracao::data)).toList();
        if (entradas.size() != 7 || entradas.stream().map(EntradaDoDiaParaGeracao::data)
                .distinct().count() != 7) {
            throw new IllegalArgumentException("Informe sete dias distintos para a geracao.");
        }

        Map<UUID, Integer> carga = new HashMap<>();
        Map<UUID, Integer> ocorrencias = new HashMap<>();
        for (EntradaDoDiaParaGeracao entrada : entradas) {
            entrada.blocosPreservados().stream()
                    .filter(item -> item.identificadorDaMateria() != null)
                    .forEach(item -> {
                        carga.merge(item.identificadorDaMateria(), item.duracaoEmMinutos(), Integer::sum);
                        ocorrencias.merge(item.identificadorDaMateria(), 1, Integer::sum);
                    });
        }

        List<DiaDaPreviaDaGeracao> dias = new ArrayList<>();
        List<JustificativaDaGeracao> avisosDaSemana = new ArrayList<>();
        Set<UUID> materiasDoDiaAnterior = Set.of();
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
                    .map(BlocoPreservadoNaGeracao::identificadorDaMateria)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

            if (configuracao.duracaoDoBlocoDeRevisaoEmMinutos() > 0) {
                boolean possuiRevisao = preservados.stream()
                        .anyMatch(item -> item.tipoDeAtividade() == TipoDeAtividade.REVISAO);
                if (possuiRevisao) {
                    avisos.add(justificativa("REVISAO_JA_EXISTENTE",
                            "A revisao preservada foi mantida; nenhuma outra foi sugerida."));
                } else if (livres >= configuracao.duracaoDoBlocoDeRevisaoEmMinutos()) {
                    sugeridos.add(new BlocoSugerido(null, null, "Revisão do dia",
                            TipoDeAtividade.REVISAO,
                            configuracao.duracaoDoBlocoDeRevisaoEmMinutos(),
                            List.of(justificativa("REVISAO_RESERVADA",
                                    "A revisao foi reservada antes dos blocos principais."))));
                    livres -= configuracao.duracaoDoBlocoDeRevisaoEmMinutos();
                } else {
                    avisos.add(justificativa("REVISAO_NAO_CABE",
                            "A revisao completa nao cabe na capacidade restante do dia."));
                }
            }

            int vagas = Math.max(0, META_DE_MATERIAS - materiasDoDia.size());
            long candidatasDisponiveis = candidatos.stream()
                    .filter(item -> !materiasDoDia.contains(item.identificadorDaMateria())).count();
            int quantidade = Math.min(vagas,
                    Math.min((int) candidatasDisponiveis, livres / DURACAO_MINIMA));
            if (quantidade > 0) {
                int duracaoBase = Math.min(configuracao.duracaoPadraoDoBlocoPrincipalEmMinutos(),
                        livres / quantidade);
                int excedenteDistribuivel = Math.min(livres - duracaoBase * quantidade,
                        quantidade * Math.max(0,
                                configuracao.duracaoPadraoDoBlocoPrincipalEmMinutos() - duracaoBase));
                for (int indice = 0; indice < quantidade; indice++) {
                    CandidatoDeMateriaParaGeracao escolhido = escolher(candidatos,
                            materiasDoDia, materiasDoDiaAnterior, carga, ocorrencias);
                    int duracao = duracaoBase + (indice < excedenteDistribuivel ? 1 : 0);
                    List<JustificativaDaGeracao> justificativas = justificativas(escolhido,
                            materiasDoDiaAnterior, quantidade);
                    sugeridos.add(new BlocoSugerido(escolhido.identificadorDaMateria(),
                            escolhido.nome(), escolhido.nome(), TipoDeAtividade.TEORIA,
                            duracao, justificativas));
                    materiasDoDia.add(escolhido.identificadorDaMateria());
                    carga.merge(escolhido.identificadorDaMateria(), duracao, Integer::sum);
                    ocorrencias.merge(escolhido.identificadorDaMateria(), 1, Integer::sum);
                    livres -= duracao;
                }
            }

            if (entrada.minutosDisponiveis() > 0 && candidatos.isEmpty()) {
                avisos.add(justificativa("POUCAS_MATERIAS_ELEGIVEIS",
                        "Nao ha materias incluidas para sugerir neste dia."));
            } else if (vagas > 0 && quantidade < Math.min(META_DE_MATERIAS, vagas)) {
                String codigo = livres < DURACAO_MINIMA
                        ? "DISPONIBILIDADE_INSUFICIENTE" : "POUCAS_MATERIAS_ELEGIVEIS";
                String mensagem = livres < DURACAO_MINIMA
                        ? "A capacidade nao comporta outro bloco minimo de 25 minutos."
                        : "Ha poucas materias distintas elegiveis para atingir a meta do dia.";
                avisos.add(justificativa(codigo, mensagem));
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
        if (candidatos.size() < META_DE_MATERIAS) {
            avisosDaSemana.add(justificativa("POUCAS_MATERIAS_ELEGIVEIS",
                    "A meta de tres materias depende de ao menos tres materias incluidas."));
        }
        return new PreviaDaGeracaoDaSemana(plano, dias, avisosDaSemana);
    }

    private CandidatoDeMateriaParaGeracao escolher(
            List<CandidatoDeMateriaParaGeracao> candidatos, Set<UUID> usadasNoDia,
            Set<UUID> usadasNoDiaAnterior, Map<UUID, Integer> carga,
            Map<UUID, Integer> ocorrencias) {
        List<CandidatoDeMateriaParaGeracao> disponiveis = candidatos.stream()
                .filter(item -> !usadasNoDia.contains(item.identificadorDaMateria())).toList();
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
            int quantidade) {
        List<JustificativaDaGeracao> resultado = new ArrayList<>();
        resultado.add(justificativa("PRIORIDADE_" + candidato.prioridade().name(),
                "Materia com prioridade " + candidato.prioridade().name().toLowerCase() + "."));
        resultado.add(justificativa("EQUILIBRIO_DA_SEMANA",
                "Escolhida pela menor carga ponderada acumulada na semana."));
        if (!materiasDoDiaAnterior.contains(candidato.identificadorDaMateria())) {
            resultado.add(justificativa("ALTERNANCIA_ENTRE_DIAS",
                    "Favorece alternancia em relacao ao dia anterior."));
        }
        if (quantidade == META_DE_MATERIAS) {
            resultado.add(justificativa("META_DE_TRES_MATERIAS",
                    "Contribui para a meta de tres materias distintas no dia."));
        }
        return List.copyOf(resultado);
    }

    private JustificativaDaGeracao justificativa(String codigo, String mensagem) {
        return new JustificativaDaGeracao(codigo, mensagem);
    }
}
