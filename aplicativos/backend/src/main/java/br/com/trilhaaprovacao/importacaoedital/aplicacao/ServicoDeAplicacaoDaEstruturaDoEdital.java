package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.concursos.aplicacao.ServicoDaEstruturaDeConcursos;
import br.com.trilhaaprovacao.concursos.dominio.SituacaoDoConcurso;
import br.com.trilhaaprovacao.conteudoprogramatico.aplicacao.ServicoDeConteudoProgramatico;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeMaterias;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeTopicos;
import br.com.trilhaaprovacao.conteudos.dominio.Materia;
import br.com.trilhaaprovacao.conteudos.dominio.TopicoDaMateria;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.CargoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.GrupoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ItemExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.MateriaExtraida;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ProvaExtraida;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.TopicoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ModoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.PoliticaDeReutilizacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.ValorExtraido;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unico ponto que transforma uma extracao confirmada em dados do produto.
 * A fonte ja deve estar validada e a chamada participa da transacao SERIALIZABLE
 * da confirmacao assistida.
 */
@Service
public class ServicoDeAplicacaoDaEstruturaDoEdital {
    private static final String COR_NEUTRA_DO_SISTEMA = "#475569";

    private final ServicoDaEstruturaDeConcursos estrutura;
    private final ServicoDeMaterias materias;
    private final ServicoDeTopicos topicos;
    private final ServicoDeConteudoProgramatico conteudo;

    public ServicoDeAplicacaoDaEstruturaDoEdital(
            ServicoDaEstruturaDeConcursos estrutura,
            ServicoDeMaterias materias, ServicoDeTopicos topicos,
            ServicoDeConteudoProgramatico conteudo) {
        this.estrutura = estrutura;
        this.materias = materias;
        this.topicos = topicos;
        this.conteudo = conteudo;
    }

    @Transactional
    public ResultadoDaAplicacaoDaImportacao aplicar(
            SolicitacaoDeAplicacaoDaImportacao solicitacao) {
        UUID usuario = solicitacao.identificadorDoUsuario();
        ExtracaoEstruturadaDoEdital extracao = solicitacao.extracao();
        CargoExtraido cargoExtraido = cargoSelecionado(extracao,
                solicitacao.chaveDoCargoSelecionado());
        validarEscopoDoCargo(extracao, cargoExtraido.chave());

        Map<String, UUID> porChave = new LinkedHashMap<>();
        UUID concurso = concurso(usuario, solicitacao, extracao, porChave);
        UUID edital = criarEdital(usuario, concurso, extracao, solicitacao,
                porChave);
        UUID cargo = criarCargo(usuario, concurso, cargoExtraido, solicitacao,
                porChave);

        Map<String, UUID> provas = criarProvas(usuario, cargo, extracao,
                cargoExtraido.chave(), porChave);
        Map<String, UUID> grupos = criarGrupos(usuario, extracao, provas,
                cargoExtraido.chave(), porChave);
        Map<String, String> provaPorGrupo = new LinkedHashMap<>();
        extracao.provas().stream().filter(prova -> cargoExtraido.chave()
                        .equals(prova.chaveDoCargo()))
                .forEach(prova -> prova.grupos().forEach(grupo ->
                        provaPorGrupo.put(grupo.chave(), prova.chave())));
        Contadores contadores = new Contadores(provas.size(), grupos.size());
        for (MateriaExtraida informada : extracao.materias().stream()
                .filter(item -> cargoExtraido.chave().equals(
                        item.chaveDoCargo())).sorted(Comparator.comparingInt(
                                MateriaExtraida::ordem)).toList()) {
            UUID grupo = grupos.get(informada.chaveDoGrupo());
            if (grupo == null || !provas.containsKey(informada.chaveDaProva())
                    || !informada.chaveDaProva().equals(
                            provaPorGrupo.get(informada.chaveDoGrupo()))) {
                throw regra("ASSOCIACAO_DA_MATERIA_INVALIDA",
                        "A materia referencia prova ou grupo fora do cargo selecionado.");
            }
            Materia materia = resolverMateria(usuario, informada, solicitacao,
                    contadores);
            porChave.put(informada.chave(), materia.identificador());
            var materiaDaProva = estrutura.criarMateriaDaProva(usuario, grupo,
                    materia.identificador(), informada.ordem(),
                    valor(informada.peso()),
                    valor(informada.quantidadeDeQuestoes()),
                    valor(informada.pontuacaoMaxima()));
            porChave.put(informada.chave() + ":materiaDaProva",
                    materiaDaProva.identificador());
            Map<String, UUID> topicosDaMateria = resolverTopicos(usuario,
                    materia.identificador(), informada, solicitacao,
                    contadores, porChave);
            criarItens(usuario, edital, materiaDaProva.identificador(),
                    informada, solicitacao.identificadorDaImportacao(),
                    topicosDaMateria, porChave, contadores);
        }
        return new ResultadoDaAplicacaoDaImportacao(
                solicitacao.identificadorDaImportacao(), concurso, edital,
                cargo, estrutura.obterConcurso(usuario, concurso).situacao()
                        .name(),
                contadores.provas, contadores.grupos,
                contadores.materiasCriadas, contadores.materiasReutilizadas,
                contadores.topicosCriados, contadores.topicosReutilizados,
                contadores.itens, contadores.sugestoes, porChave);
    }

    private UUID concurso(UUID usuario,
            SolicitacaoDeAplicacaoDaImportacao solicitacao,
            ExtracaoEstruturadaDoEdital extracao, Map<String, UUID> porChave) {
        if (solicitacao.modo()
                == ModoDaImportacaoDeEdital.COMPLEMENTAR_EXISTENTE) {
            UUID existente = estrutura.obterConcurso(usuario,
                    solicitacao.identificadorDoConcursoExistente())
                    .identificador();
            porChave.put("concurso", existente);
            return existente;
        }
        if (extracao.concurso() == null
                || valor(extracao.concurso().nome()) == null) {
            throw regra("CONCURSO_SEM_NOME",
                    "A extracao confirmada nao possui nome do concurso.");
        }
        var criado = estrutura.criarConcurso(usuario,
                valor(extracao.concurso().nome()),
                valor(extracao.concurso().descricao()),
                valor(extracao.concurso().orgao()),
                valor(extracao.concurso().banca()),
                SituacaoDoConcurso.PLANEJADO,
                valor(extracao.concurso().dataPrevista()));
        porChave.put("concurso", criado.identificador());
        return criado.identificador();
    }

    private UUID criarEdital(UUID usuario, UUID concurso,
            ExtracaoEstruturadaDoEdital extracao,
            SolicitacaoDeAplicacaoDaImportacao solicitacao,
            Map<String, UUID> porChave) {
        if (extracao.edital() == null || valor(extracao.edital().titulo()) == null) {
            throw regra("EDITAL_SEM_TITULO",
                    "A extracao confirmada nao possui titulo do edital.");
        }
        var edital = estrutura.criarEdital(usuario, concurso,
                valor(extracao.edital().titulo()),
                valor(extracao.edital().numero()), valor(extracao.edital().ano()),
                valor(extracao.edital().descricao()),
                valor(extracao.edital().dataDePublicacao()), null);
        if (solicitacao.modo() == ModoDaImportacaoDeEdital.CRIAR_NOVO
                || solicitacao.decisoes().definirEditalComoPrincipal()) {
            estrutura.definirEditalPrincipal(usuario, edital.identificador());
        }
        porChave.put("edital", edital.identificador());
        return edital.identificador();
    }

    private UUID criarCargo(UUID usuario, UUID concurso, CargoExtraido extraido,
            SolicitacaoDeAplicacaoDaImportacao solicitacao,
            Map<String, UUID> porChave) {
        var cargo = estrutura.criarCargo(usuario, concurso,
                valorObrigatorio(extraido.nome(), "CARGO_SEM_NOME"),
                valor(extraido.area()), valor(extraido.especialidade()),
                valorObrigatorio(extraido.nivelDeEscolaridade(),
                        "CARGO_SEM_ESCOLARIDADE"), extraido.ordem());
        if (solicitacao.modo() == ModoDaImportacaoDeEdital.CRIAR_NOVO
                || solicitacao.decisoes().selecionarCargoCriado()) {
            estrutura.selecionarCargo(usuario, cargo.identificador());
        }
        porChave.put(extraido.chave(), cargo.identificador());
        return cargo.identificador();
    }

    private Map<String, UUID> criarProvas(UUID usuario, UUID cargo,
            ExtracaoEstruturadaDoEdital extracao, String chaveDoCargo,
            Map<String, UUID> porChave) {
        Map<String, UUID> resultado = new LinkedHashMap<>();
        for (ProvaExtraida informada : extracao.provas().stream()
                .filter(item -> chaveDoCargo.equals(item.chaveDoCargo()))
                .sorted(Comparator.comparingInt(ProvaExtraida::ordem)).toList()) {
            var prova = estrutura.criarProva(usuario, cargo,
                    valorObrigatorio(informada.nome(), "PROVA_SEM_NOME"),
                    valorObrigatorio(informada.tipo(), "PROVA_SEM_TIPO"),
                    valorObrigatorio(informada.carater(),
                            "PROVA_SEM_CARATER"), informada.ordem(),
                    valor(informada.dataHora()),
                    valor(informada.duracaoEmMinutos()),
                    valor(informada.quantidadeDeQuestoes()),
                    valor(informada.pontuacaoMaxima()),
                    valor(informada.pontuacaoMinima()));
            resultado.put(informada.chave(), prova.identificador());
            porChave.put(informada.chave(), prova.identificador());
        }
        if (resultado.isEmpty()) {
            throw regra("CARGO_SEM_PROVA",
                    "O cargo selecionado nao possui prova confirmada.");
        }
        return resultado;
    }

    private Map<String, UUID> criarGrupos(UUID usuario,
            ExtracaoEstruturadaDoEdital extracao, Map<String, UUID> provas,
            String chaveDoCargo, Map<String, UUID> porChave) {
        Map<String, UUID> resultado = new LinkedHashMap<>();
        extracao.provas().stream()
                .filter(item -> chaveDoCargo.equals(item.chaveDoCargo()))
                .forEach(prova -> prova.grupos().stream()
                        .sorted(Comparator.comparingInt(GrupoExtraido::ordem))
                        .forEach(grupo -> {
                            var criado = estrutura.criarGrupo(usuario,
                                    provas.get(prova.chave()),
                                    valorObrigatorio(grupo.nome(),
                                            "GRUPO_SEM_NOME"), grupo.ordem(),
                                    valor(grupo.quantidadeDeQuestoes()),
                                    valor(grupo.pontuacaoMaxima()),
                                    valor(grupo.pontuacaoMinima()));
                            resultado.put(grupo.chave(),
                                    criado.identificador());
                            porChave.put(grupo.chave(),
                                    criado.identificador());
                        }));
        if (resultado.isEmpty()) {
            throw regra("CARGO_SEM_GRUPO_DE_CONTEUDO",
                    "O cargo selecionado nao possui grupo confirmado.");
        }
        return resultado;
    }

    private Materia resolverMateria(UUID usuario, MateriaExtraida informada,
            SolicitacaoDeAplicacaoDaImportacao solicitacao,
            Contadores contadores) {
        UUID reutilizar = solicitacao.decisoes().recursosParaReutilizar()
                .get(informada.chave());
        if (reutilizar != null) {
            if (solicitacao.politicaDeReutilizacao()
                    == PoliticaDeReutilizacao.CRIAR_SEPARADO) {
                throw regra("REUTILIZACAO_NAO_PERMITIDA",
                        "A politica escolhida nao permite reutilizar materias.");
            }
            contadores.materiasReutilizadas++;
            return materias.obter(usuario, reutilizar);
        }
        contadores.materiasCriadas++;
        return materias.criar(usuario,
                valorObrigatorio(informada.nome(), "MATERIA_SEM_NOME"),
                valor(informada.descricao()), COR_NEUTRA_DO_SISTEMA);
    }

    private Map<String, UUID> resolverTopicos(UUID usuario, UUID materia,
            MateriaExtraida informada,
            SolicitacaoDeAplicacaoDaImportacao solicitacao,
            Contadores contadores, Map<String, UUID> porChave) {
        Map<String, UUID> resultado = new LinkedHashMap<>();
        List<TopicoExtraido> pendentes = new ArrayList<>(informada.topicos());
        while (!pendentes.isEmpty()) {
            int antes = pendentes.size();
            pendentes.removeIf(topico -> {
                if (topico.chaveDoPai() != null
                        && !resultado.containsKey(topico.chaveDoPai())) {
                    return false;
                }
                UUID reutilizar = solicitacao.decisoes()
                        .recursosParaReutilizar().get(topico.chave());
                UUID resolvido;
                if (reutilizar != null) {
                    if (solicitacao.politicaDeReutilizacao()
                            == PoliticaDeReutilizacao.CRIAR_SEPARADO) {
                        throw regra("REUTILIZACAO_NAO_PERMITIDA",
                                "A politica escolhida nao permite reutilizar topicos.");
                    }
                    TopicoDaMateria existente = topicos.obter(usuario,
                            reutilizar);
                    if (!materia.equals(existente.identificadorDaMateria())) {
                        throw regra("TOPICO_DE_OUTRA_MATERIA",
                                "O topico escolhido nao pertence a materia confirmada.");
                    }
                    UUID paiEsperado = topico.chaveDoPai() == null ? null
                            : resultado.get(topico.chaveDoPai());
                    if (!java.util.Objects.equals(
                            paiEsperado,
                            existente.identificadorDoTopicoPai())) {
                        throw regra("HIERARQUIA_DO_TOPICO_REUTILIZADO_DIVERGENTE",
                                "O topico escolhido nao preserva a hierarquia confirmada.");
                    }
                    resolvido = existente.identificador();
                    contadores.topicosReutilizados++;
                } else {
                    resolvido = topicos.criar(usuario, materia,
                            topico.chaveDoPai() == null ? null
                                    : resultado.get(topico.chaveDoPai()),
                            valor(topico.numeroOficial()),
                            valorObrigatorio(topico.nome(),
                                    "TOPICO_SEM_NOME"),
                            valor(topico.descricao()), topico.ordem())
                            .identificador();
                    contadores.topicosCriados++;
                }
                resultado.put(topico.chave(), resolvido);
                porChave.put(topico.chave(), resolvido);
                return true;
            });
            if (pendentes.size() == antes) {
                throw regra("HIERARQUIA_DE_TOPICOS_INVALIDA",
                        "A hierarquia de topicos possui pai ausente ou ciclo.");
            }
        }
        return resultado;
    }

    private void criarItens(UUID usuario, UUID edital, UUID materiaDaProva,
            MateriaExtraida materia, UUID importacao,
            Map<String, UUID> topicosDaMateria, Map<String, UUID> porChave,
            Contadores contadores) {
        Map<String, UUID> itens = new LinkedHashMap<>();
        List<ItemExtraido> pendentes = new ArrayList<>(materia.itensDoEdital());
        while (!pendentes.isEmpty()) {
            int antes = pendentes.size();
            pendentes.removeIf(informado -> {
                if (informado.chaveDoPai() != null
                        && !itens.containsKey(informado.chaveDoPai())) {
                    return false;
                }
                var criado = conteudo.criarItem(usuario, materiaDaProva,
                        edital, valorObrigatorio(informado.descricaoLiteral(),
                                "ITEM_SEM_DESCRICAO"),
                        informado.chaveDoPai() == null ? null
                                : itens.get(informado.chaveDoPai()),
                        informado.ordem(), valor(informado.numeroOficial()),
                        informado.nomeNormalizado(), importacao);
                itens.put(informado.chave(), criado.identificador());
                porChave.put(informado.chave(), criado.identificador());
                contadores.itens++;
                if (informado.chaveDoTopicoSugerido() != null) {
                    UUID topico = topicosDaMateria.get(
                            informado.chaveDoTopicoSugerido());
                    if (topico == null) {
                        throw regra("TOPICO_SUGERIDO_INVALIDO",
                                "O item referencia topico ausente da materia.");
                    }
                    conteudo.criarSugestaoDeMapeamento(usuario,
                            criado.identificador(), topico);
                    contadores.sugestoes++;
                }
                return true;
            });
            if (pendentes.size() == antes) {
                throw regra("HIERARQUIA_DE_ITENS_INVALIDA",
                        "A hierarquia de itens possui pai ausente ou ciclo.");
            }
        }
    }

    private CargoExtraido cargoSelecionado(ExtracaoEstruturadaDoEdital extracao,
            String chave) {
        return extracao.cargos().stream()
                .filter(cargo -> chave.equals(cargo.chave())).findFirst()
                .orElseThrow(() -> regra("CARGO_SELECIONADO_INVALIDO",
                        "Selecione explicitamente um cargo da extracao atual."));
    }

    private void validarEscopoDoCargo(ExtracaoEstruturadaDoEdital extracao,
            String chaveDoCargo) {
        Set<String> provas = extracao.provas().stream()
                .filter(prova -> chaveDoCargo.equals(prova.chaveDoCargo()))
                .map(ProvaExtraida::chave).collect(
                        java.util.stream.Collectors.toSet());
        for (MateriaExtraida materia : extracao.materias()) {
            if (chaveDoCargo.equals(materia.chaveDoCargo())
                    && !provas.contains(materia.chaveDaProva())) {
                throw regra("MISTURA_DE_CARGOS",
                        "Uma materia do lote aponta para prova de outro cargo.");
            }
        }
    }

    private <T> T valor(ValorExtraido<T> valor) {
        return valor == null ? null : valor.valor();
    }

    private <T> T valorObrigatorio(ValorExtraido<T> valor, String codigo) {
        T encontrado = valor(valor);
        if (encontrado == null) {
            throw regra(codigo,
                    "A extracao confirmada possui um campo obrigatorio ausente.");
        }
        return encontrado;
    }

    private RegraDeDominio regra(String codigo, String mensagem) {
        return new RegraDeDominio(codigo, mensagem);
    }

    private static final class Contadores {
        private final int provas;
        private final int grupos;
        private int materiasCriadas;
        private int materiasReutilizadas;
        private int topicosCriados;
        private int topicosReutilizados;
        private int itens;
        private int sugestoes;

        private Contadores(int provas, int grupos) {
            this.provas = provas;
            this.grupos = grupos;
        }
    }
}
