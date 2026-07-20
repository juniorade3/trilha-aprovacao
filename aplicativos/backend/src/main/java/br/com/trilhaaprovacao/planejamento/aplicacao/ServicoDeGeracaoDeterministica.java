package br.com.trilhaaprovacao.planejamento.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.concursos.aplicacao.ConsultaDeMateriasElegiveisParaPlanejamento;
import br.com.trilhaaprovacao.concursos.aplicacao.MateriaElegivelParaPlanejamento;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeMaterias;
import br.com.trilhaaprovacao.planejamento.dominio.BlocoPreservadoNaGeracao;
import br.com.trilhaaprovacao.planejamento.dominio.BlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.CandidatoDeMateriaParaGeracao;
import br.com.trilhaaprovacao.planejamento.dominio.ConfiguracaoDaGeracaoDeterministica;
import br.com.trilhaaprovacao.planejamento.dominio.DiaDaPreviaDaGeracao;
import br.com.trilhaaprovacao.planejamento.dominio.EntradaDoDiaParaGeracao;
import br.com.trilhaaprovacao.planejamento.dominio.EstadoDoBlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.EstadoDoPlanoSemanal;
import br.com.trilhaaprovacao.planejamento.dominio.GeradorDeterministicoDePlano;
import br.com.trilhaaprovacao.planejamento.dominio.JustificativaDaGeracao;
import br.com.trilhaaprovacao.planejamento.dominio.OrigemDoBlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.PlanoSemanal;
import br.com.trilhaaprovacao.planejamento.dominio.PreviaDaGeracaoDaSemana;
import br.com.trilhaaprovacao.planejamento.dominio.PrioridadeDaMateriaNoPlano;
import br.com.trilhaaprovacao.planejamento.dominio.PrioridadeDeMateriaNoPlano;
import br.com.trilhaaprovacao.planejamento.infraestrutura.BlocoDeEstudoPersistido;
import br.com.trilhaaprovacao.planejamento.infraestrutura.DisponibilidadeDoDiaPersistida;
import br.com.trilhaaprovacao.planejamento.infraestrutura.PrioridadeDeMateriaNoPlanoPersistida;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDeBlocosDeEstudo;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDeDisponibilidadesDoDia;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDePlanosSemanais;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDePrioridadesDeMateriasNoPlano;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoDeGeracaoDeterministica {
    private final RepositorioDePlanosSemanais planos;
    private final RepositorioDeDisponibilidadesDoDia disponibilidades;
    private final RepositorioDeBlocosDeEstudo blocos;
    private final RepositorioDePrioridadesDeMateriasNoPlano prioridades;
    private final ConsultaDeMateriasElegiveisParaPlanejamento materiasElegiveis;
    private final ServicoDeMaterias materias;
    private final GeradorDeterministicoDePlano gerador = new GeradorDeterministicoDePlano();

    public ServicoDeGeracaoDeterministica(RepositorioDePlanosSemanais planos,
            RepositorioDeDisponibilidadesDoDia disponibilidades,
            RepositorioDeBlocosDeEstudo blocos,
            RepositorioDePrioridadesDeMateriasNoPlano prioridades,
            ConsultaDeMateriasElegiveisParaPlanejamento materiasElegiveis,
            ServicoDeMaterias materias) {
        this.planos = planos;
        this.disponibilidades = disponibilidades;
        this.blocos = blocos;
        this.prioridades = prioridades;
        this.materiasElegiveis = materiasElegiveis;
        this.materias = materias;
    }

    @Transactional(readOnly = true)
    public List<MateriaParaGeracao> listarMaterias(UUID usuario, UUID plano) {
        obterPlano(usuario, plano);
        return montarMaterias(usuario, plano);
    }

    @Transactional
    public List<MateriaParaGeracao> substituirPrioridades(UUID usuario, UUID plano,
            List<PrioridadeDeMateriaInformada> informadas) {
        exigirRascunho(obterPlano(usuario, plano).estado());
        List<MateriaParaGeracao> elegiveis = montarMaterias(usuario, plano);
        if (informadas == null || informadas.stream().anyMatch(item -> item == null
                || item.identificadorDaMateria() == null || item.prioridade() == null)) {
            throw new RegraDeDominio("PRIORIDADES_INVALIDAS",
                    "Informe a prioridade de cada materia elegivel.");
        }
        Set<UUID> identificadores = informadas.stream()
                .map(PrioridadeDeMateriaInformada::identificadorDaMateria)
                .collect(Collectors.toSet());
        Set<UUID> esperados = elegiveis.stream().map(MateriaParaGeracao::identificadorDaMateria)
                .collect(Collectors.toSet());
        if (identificadores.size() != informadas.size() || !identificadores.equals(esperados)) {
            throw new RegraDeDominio("PRIORIDADES_INVALIDAS",
                    "A lista deve conter cada materia elegivel uma unica vez.");
        }
        prioridades.deleteAllInBatch(prioridades.findByIdentificadorDoPlano(plano));
        prioridades.flush();
        List<PrioridadeDeMateriaNoPlanoPersistida> persistidas = informadas.stream()
                .filter(item -> item.prioridade() != PrioridadeDaMateriaNoPlano.NORMAL)
                .map(item -> new PrioridadeDeMateriaNoPlanoPersistida(
                        PrioridadeDeMateriaNoPlano.criar(plano,
                                item.identificadorDaMateria(), item.prioridade())))
                .toList();
        prioridades.saveAllAndFlush(persistidas);
        Map<UUID, PrioridadeDaMateriaNoPlano> porMateria = informadas.stream()
                .collect(Collectors.toMap(PrioridadeDeMateriaInformada::identificadorDaMateria,
                        PrioridadeDeMateriaInformada::prioridade));
        return elegiveis.stream().map(item -> new MateriaParaGeracao(
                item.identificadorDaMateria(), item.nome(), item.ordemEstavel(),
                porMateria.get(item.identificadorDaMateria()))).toList();
    }

    @Transactional(readOnly = true)
    public PreviaDaGeracaoDaSemana gerarPrevia(UUID usuario, UUID plano,
            ConfiguracaoDaGeracaoDeterministica configuracao) {
        exigirRascunho(obterPlano(usuario, plano).estado());
        return calcularPrevia(usuario, plano, configuracao,
                blocos.findByIdentificadorDoPlanoOrderByDataAscOrdemAsc(plano));
    }

    @Transactional
    public ResultadoDaAplicacaoDaGeracao aplicar(UUID usuario, UUID plano,
            ConfiguracaoDaGeracaoDeterministica configuracao,
            boolean substituirBlocosGerados) {
        PlanoSemanal planoSemanal = planos.encontrarParaAtualizacao(plano, usuario)
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "PLANO_SEMANAL_NAO_ENCONTRADO", "Plano semanal nao encontrado."))
                .paraDominio();
        exigirRascunho(planoSemanal.estado());
        List<BlocoDeEstudoPersistido> existentes = blocos
                .findByIdentificadorDoPlanoOrderByDataAscOrdemAsc(plano);
        List<BlocoDeEstudoPersistido> substituiveis = existentes.stream()
                .filter(this::eGeradoPuro).toList();
        if (!substituiveis.isEmpty() && !substituirBlocosGerados) {
            throw new ConflitoDeDominio("GERACAO_DETERMINISTICA_JA_APLICADA",
                    "O plano ja possui blocos gerados. Confirme a substituicao para regenerar.");
        }
        PreviaDaGeracaoDaSemana previa = calcularPrevia(
                usuario, plano, configuracao, existentes);
        if (!substituiveis.isEmpty()) {
            blocos.deleteAll(substituiveis);
            blocos.flush();
        }
        List<BlocoDeEstudoPersistido> preservados = existentes.stream()
                .filter(item -> !eGeradoPuro(item))
                .sorted(ordemDosBlocos()).collect(Collectors.toCollection(ArrayList::new));
        normalizarPreservados(preservados);
        List<BlocoDeEstudoPersistido> gerados = criarBlocosGerados(plano, previa, preservados);
        blocos.saveAll(gerados);
        blocos.flush();
        ResultadoDoPlanoSemanal atualizado = new ResultadoDoPlanoSemanal(planoSemanal,
                disponibilidades.findByIdentificadorDoPlanoOrderByDataAsc(plano).stream()
                        .map(DisponibilidadeDoDiaPersistida::paraDominio).toList(),
                blocos.findByIdentificadorDoPlanoOrderByDataAscOrdemAsc(plano).stream()
                        .map(BlocoDeEstudoPersistido::paraDominio).toList());
        return new ResultadoDaAplicacaoDaGeracao(atualizado, gerados.size(),
                substituiveis.size(), preservados.size());
    }

    private PreviaDaGeracaoDaSemana calcularPrevia(UUID usuario, UUID plano,
            ConfiguracaoDaGeracaoDeterministica configuracao,
            List<BlocoDeEstudoPersistido> blocosDoPlano) {
        List<MateriaParaGeracao> materiasParaGeracao = montarMaterias(usuario, plano);
        Map<UUID, MateriaElegivelParaPlanejamento> dadosElegiveis = materiasElegiveis
                .consultar(usuario).stream().collect(Collectors.toMap(
                        MateriaElegivelParaPlanejamento::identificadorDaMateria, item -> item));
        List<CandidatoDeMateriaParaGeracao> candidatos = materiasParaGeracao.stream()
                .map(item -> {
                    MateriaElegivelParaPlanejamento dados = dadosElegiveis
                            .get(item.identificadorDaMateria());
                    return new CandidatoDeMateriaParaGeracao(item.identificadorDaMateria(),
                            item.nome(), dados.nomeNormalizado(), item.ordemEstavel(),
                            item.prioridade());
                }).toList();
        Map<UUID, String> nomes = new HashMap<>();
        candidatos.forEach(item -> nomes.put(item.identificadorDaMateria(), item.nome()));
        List<BlocoPreservadoNaGeracao> preservados = blocosDoPlano.stream()
                .map(BlocoDeEstudoPersistido::paraDominio)
                .filter(item -> item.estado() != EstadoDoBlocoDeEstudo.CANCELADO)
                .filter(item -> item.origem()
                        != OrigemDoBlocoDeEstudo.GERADO_DETERMINISTICAMENTE)
                .map(item -> new BlocoPreservadoNaGeracao(item.identificador(),
                        item.identificadorDaMateria(), nomeDaMateria(usuario,
                                item.identificadorDaMateria(), nomes), item.titulo(),
                        item.tipoDeAtividade(), item.data(),
                        item.duracaoPrevistaEmMinutos(), item.ordem()))
                .toList();
        Map<java.time.LocalDate, List<BlocoPreservadoNaGeracao>> porData = preservados.stream()
                .collect(Collectors.groupingBy(BlocoPreservadoNaGeracao::data));
        List<EntradaDoDiaParaGeracao> entradas = disponibilidades
                .findByIdentificadorDoPlanoOrderByDataAsc(plano).stream()
                .map(item -> item.paraDominio())
                .map(item -> new EntradaDoDiaParaGeracao(item.data(),
                        item.minutosDisponiveis(), porData.getOrDefault(item.data(), List.of())))
                .toList();
        try {
            return gerador.gerar(plano, candidatos, entradas, configuracao);
        } catch (IllegalArgumentException excecao) {
            throw new RegraDeDominio("CONFIGURACAO_DA_GERACAO_INVALIDA", excecao.getMessage());
        }
    }

    private boolean eGeradoPuro(BlocoDeEstudoPersistido persistido) {
        return persistido.paraDominio().origem()
                == OrigemDoBlocoDeEstudo.GERADO_DETERMINISTICAMENTE;
    }

    private Comparator<BlocoDeEstudoPersistido> ordemDosBlocos() {
        return Comparator.comparing((BlocoDeEstudoPersistido item) ->
                        item.paraDominio().data())
                .thenComparingInt(item -> item.paraDominio().ordem())
                .thenComparing(item -> item.paraDominio().identificador());
    }

    private void normalizarPreservados(List<BlocoDeEstudoPersistido> preservados) {
        Map<LocalDate, List<BlocoDeEstudoPersistido>> porData = preservados.stream()
                .collect(Collectors.groupingBy(item -> item.paraDominio().data()));
        porData.values().forEach(itens -> {
            itens.sort(ordemDosBlocos());
            for (int indice = 0; indice < itens.size(); indice++) {
                BlocoDeEstudoPersistido persistido = itens.get(indice);
                BlocoDeEstudo atual = persistido.paraDominio();
                if (atual.ordem() != indice + 1) {
                    persistido.atualizarDe(atual.normalizarPosicao(atual.data(), indice + 1));
                }
            }
        });
    }

    private List<BlocoDeEstudoPersistido> criarBlocosGerados(UUID plano,
            PreviaDaGeracaoDaSemana previa,
            List<BlocoDeEstudoPersistido> preservados) {
        Map<LocalDate, Integer> proximaOrdem = preservados.stream()
                .collect(Collectors.groupingBy(item -> item.paraDominio().data(),
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
        List<BlocoDeEstudoPersistido> gerados = new ArrayList<>();
        for (DiaDaPreviaDaGeracao dia : previa.dias()) {
            int ordem = proximaOrdem.getOrDefault(dia.data(), 0);
            for (var sugestao : dia.blocosSugeridos()) {
                BlocoDeEstudo bloco = BlocoDeEstudo.criarGerado(plano,
                        sugestao.identificadorDaMateria(), sugestao.titulo(),
                        sugestao.tipoDeAtividade(), dia.data(),
                        sugestao.duracaoEmMinutos(), ++ordem,
                        resumir(sugestao.justificativas()));
                gerados.add(new BlocoDeEstudoPersistido(bloco));
            }
        }
        return gerados;
    }

    private String resumir(List<JustificativaDaGeracao> justificativas) {
        if (justificativas.isEmpty()) return null;
        String resumo = justificativas.stream()
                .map(item -> item.codigo() + ": " + item.mensagem())
                .collect(Collectors.joining(" | "));
        return resumo.length() <= 2000 ? resumo : resumo.substring(0, 2000);
    }

    private List<MateriaParaGeracao> montarMaterias(UUID usuario, UUID plano) {
        List<MateriaElegivelParaPlanejamento> elegiveis = materiasElegiveis.consultar(usuario);
        Map<UUID, PrioridadeDaMateriaNoPlano> registradas = prioridades
                .findByIdentificadorDoPlano(plano).stream()
                .map(PrioridadeDeMateriaNoPlanoPersistida::paraDominio)
                .collect(Collectors.toMap(PrioridadeDeMateriaNoPlano::identificadorDaMateria,
                        PrioridadeDeMateriaNoPlano::prioridade));
        return elegiveis.stream().map(item -> new MateriaParaGeracao(
                item.identificadorDaMateria(), item.nome(), item.ordemEstavel(),
                registradas.getOrDefault(item.identificadorDaMateria(),
                        PrioridadeDaMateriaNoPlano.NORMAL))).toList();
    }

    private String nomeDaMateria(UUID usuario, UUID identificador, Map<UUID, String> nomes) {
        if (identificador == null) return null;
        return nomes.computeIfAbsent(identificador,
                chave -> materias.obter(usuario, chave).nome());
    }

    private br.com.trilhaaprovacao.planejamento.dominio.PlanoSemanal obterPlano(
            UUID usuario, UUID identificador) {
        return planos.findByIdentificadorAndIdentificadorDoUsuario(identificador, usuario)
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "PLANO_SEMANAL_NAO_ENCONTRADO", "Plano semanal nao encontrado."))
                .paraDominio();
    }

    private void exigirRascunho(EstadoDoPlanoSemanal estado) {
        if (estado != EstadoDoPlanoSemanal.RASCUNHO) {
            throw new ConflitoDeDominio("PLANO_SEMANAL_NAO_ESTA_EM_RASCUNHO",
                    "Somente plano em rascunho permite prioridades e previa.");
        }
    }
}
