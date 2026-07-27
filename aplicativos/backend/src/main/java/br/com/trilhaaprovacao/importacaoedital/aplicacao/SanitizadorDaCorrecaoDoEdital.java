package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.CargoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ConcursoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.EditalExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.GrupoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ItemExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.MateriaExtraida;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ProvaExtraida;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.TopicoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ProvenienciaDoDado;
import br.com.trilhaaprovacao.importacaoedital.dominio.ValorExtraido;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Não aceita confiança ou proveniência declaradas pelo navegador. Somente um
 * valor exatamente igual ao da versão corrente conserva os metadados
 * originais; todo valor novo ou alterado passa a ser uma correção humana.
 */
final class SanitizadorDaCorrecaoDoEdital {
    private static final BigDecimal CONFIANCA_DA_CORRECAO = BigDecimal.ONE;
    private static final ProvenienciaDoDado FONTE_DA_CORRECAO =
            new ProvenienciaDoDado(null, "Correção do usuário",
                    "Valor informado pelo usuário.");
    private final NormalizadorDoTextoDoEdital normalizador =
            new NormalizadorDoTextoDoEdital();

    ExtracaoEstruturadaDoEdital sanitizar(
            ExtracaoEstruturadaDoEdital anterior,
            ExtracaoEstruturadaDoEdital recebida) {
        return sanitizarComResultado(anterior, recebida, Set.of()).extracao();
    }

    ResultadoDaSanitizacao sanitizarComResultado(
            ExtracaoEstruturadaDoEdital anterior,
            ExtracaoEstruturadaDoEdital recebida,
            Set<ConfirmacaoDeCampoDaExtracao> confirmacoes) {
        ContextoDaSanitizacao contexto = new ContextoDaSanitizacao(
                confirmacoes);
        ConcursoExtraido concurso = concurso(
                anterior == null ? null : anterior.concurso(),
                recebida.concurso(), contexto);
        EditalExtraido edital = edital(
                anterior == null ? null : anterior.edital(),
                recebida.edital(), contexto);
        Map<String, CargoExtraido> cargosAnteriores = porChave(
                anterior == null ? java.util.List.of() : anterior.cargos(),
                CargoExtraido::chave);
        Map<String, ProvaExtraida> provasAnteriores = porChave(
                anterior == null ? java.util.List.of() : anterior.provas(),
                ProvaExtraida::chave);
        Map<String, MateriaExtraida> materiasAnteriores = porChave(
                anterior == null ? java.util.List.of() : anterior.materias(),
                MateriaExtraida::chave);

        ExtracaoEstruturadaDoEdital sanitizada =
                new ExtracaoEstruturadaDoEdital(
                recebida.versaoDoContrato(), recebida.fonte(), concurso,
                edital,
                recebida.cargos().stream().map(cargo ->
                        cargo(cargosAnteriores.get(cargo.chave()), cargo,
                                contexto))
                        .toList(),
                recebida.provas().stream().map(prova ->
                        prova(provasAnteriores.get(prova.chave()), prova,
                                contexto))
                        .toList(),
                recebida.materias().stream().map(materia ->
                        materia(materiasAnteriores.get(materia.chave()),
                                materia, contexto)).toList(),
                anterior == null ? java.util.List.of() : anterior.avisos(),
                anterior == null ? java.util.List.of()
                        : anterior.incertezas());
        detectarAssociacoesAlteradas(anterior, sanitizada, contexto);
        contexto.camposAlterados.addAll(
                camposAlteradosEntre(anterior, sanitizada));
        return new ResultadoDaSanitizacao(sanitizada,
                Set.copyOf(contexto.camposAlterados));
    }

    private ConcursoExtraido concurso(ConcursoExtraido anterior,
            ConcursoExtraido recebida, ContextoDaSanitizacao contexto) {
        if (recebida == null) return null;
        return new ConcursoExtraido(
                valor(anterior == null ? null : anterior.nome(),
                        recebida.nome(), "concurso", "concurso", "nome",
                        contexto),
                valor(anterior == null ? null : anterior.descricao(),
                        recebida.descricao(), "concurso", "concurso",
                        "descricao", contexto),
                valor(anterior == null ? null : anterior.orgao(),
                        recebida.orgao(), "concurso", "concurso", "orgao",
                        contexto),
                valor(anterior == null ? null : anterior.banca(),
                        recebida.banca(), "concurso", "concurso", "banca",
                        contexto),
                valor(anterior == null ? null : anterior.dataPrevista(),
                        recebida.dataPrevista(), "concurso", "concurso",
                        "dataPrevista", contexto));
    }

    private EditalExtraido edital(EditalExtraido anterior,
            EditalExtraido recebida, ContextoDaSanitizacao contexto) {
        if (recebida == null) return null;
        return new EditalExtraido(
                valor(anterior == null ? null : anterior.titulo(),
                        recebida.titulo(), "edital", "edital", "titulo",
                        contexto),
                valor(anterior == null ? null : anterior.numero(),
                        recebida.numero(), "edital", "edital", "numero",
                        contexto),
                valor(anterior == null ? null : anterior.ano(),
                        recebida.ano(), "edital", "edital", "ano",
                        contexto),
                valor(anterior == null ? null : anterior.descricao(),
                        recebida.descricao(), "edital", "edital",
                        "descricao", contexto),
                valor(anterior == null ? null : anterior.dataDePublicacao(),
                        recebida.dataDePublicacao(), "edital", "edital",
                        "dataDePublicacao", contexto));
    }

    private CargoExtraido cargo(CargoExtraido anterior,
            CargoExtraido recebida, ContextoDaSanitizacao contexto) {
        return new CargoExtraido(recebida.chave(),
                valor(anterior == null ? null : anterior.nome(),
                        recebida.nome(), "cargo", recebida.chave(), "nome",
                        contexto),
                valor(anterior == null ? null : anterior.area(),
                        recebida.area(), "cargo", recebida.chave(), "area",
                        contexto),
                valor(anterior == null ? null : anterior.especialidade(),
                        recebida.especialidade(), "cargo", recebida.chave(),
                        "especialidade", contexto),
                valor(anterior == null ? null
                                : anterior.nivelDeEscolaridade(),
                        recebida.nivelDeEscolaridade(), "cargo",
                        recebida.chave(), "nivelDeEscolaridade", contexto),
                recebida.ordem());
    }

    private ProvaExtraida prova(ProvaExtraida anterior,
            ProvaExtraida recebida, ContextoDaSanitizacao contexto) {
        Map<String, GrupoExtraido> gruposAnteriores = anterior == null
                ? Map.of() : porChave(anterior.grupos(), GrupoExtraido::chave);
        return new ProvaExtraida(recebida.chave(), recebida.chaveDoCargo(),
                valor(anterior == null ? null : anterior.nome(),
                        recebida.nome(), "prova", recebida.chave(), "nome",
                        contexto),
                valor(anterior == null ? null : anterior.tipo(),
                        recebida.tipo(), "prova", recebida.chave(), "tipo",
                        contexto),
                valor(anterior == null ? null : anterior.carater(),
                        recebida.carater(), "prova", recebida.chave(),
                        "carater", contexto),
                recebida.ordem(),
                valor(anterior == null ? null : anterior.dataHora(),
                        recebida.dataHora(), "prova", recebida.chave(),
                        "dataHora", contexto),
                valor(anterior == null ? null : anterior.duracaoEmMinutos(),
                        recebida.duracaoEmMinutos(), "prova",
                        recebida.chave(), "duracaoEmMinutos", contexto),
                valor(anterior == null ? null
                                : anterior.quantidadeDeQuestoes(),
                        recebida.quantidadeDeQuestoes(), "prova",
                        recebida.chave(), "quantidadeDeQuestoes", contexto),
                valor(anterior == null ? null : anterior.pontuacaoMaxima(),
                        recebida.pontuacaoMaxima(), "prova",
                        recebida.chave(), "pontuacaoMaxima", contexto),
                valor(anterior == null ? null : anterior.pontuacaoMinima(),
                        recebida.pontuacaoMinima(), "prova",
                        recebida.chave(), "pontuacaoMinima", contexto),
                recebida.grupos().stream().map(grupo ->
                        grupo(gruposAnteriores.get(grupo.chave()), grupo,
                                contexto))
                        .toList());
    }

    private GrupoExtraido grupo(GrupoExtraido anterior,
            GrupoExtraido recebida, ContextoDaSanitizacao contexto) {
        return new GrupoExtraido(recebida.chave(),
                valor(anterior == null ? null : anterior.nome(),
                        recebida.nome(), "grupo", recebida.chave(), "nome",
                        contexto),
                recebida.ordem(),
                valor(anterior == null ? null
                                : anterior.quantidadeDeQuestoes(),
                        recebida.quantidadeDeQuestoes(), "grupo",
                        recebida.chave(), "quantidadeDeQuestoes", contexto),
                valor(anterior == null ? null : anterior.pontuacaoMaxima(),
                        recebida.pontuacaoMaxima(), "grupo",
                        recebida.chave(), "pontuacaoMaxima", contexto),
                valor(anterior == null ? null : anterior.pontuacaoMinima(),
                        recebida.pontuacaoMinima(), "grupo",
                        recebida.chave(), "pontuacaoMinima", contexto));
    }

    private MateriaExtraida materia(MateriaExtraida anterior,
            MateriaExtraida recebida, ContextoDaSanitizacao contexto) {
        Map<String, TopicoExtraido> topicosAnteriores = anterior == null
                ? Map.of() : porChave(anterior.topicos(),
                        TopicoExtraido::chave);
        Map<String, ItemExtraido> itensAnteriores = anterior == null
                ? Map.of() : porChave(anterior.itensDoEdital(),
                        ItemExtraido::chave);
        return new MateriaExtraida(recebida.chave(),
                recebida.chaveDoCargo(), recebida.chaveDaProva(),
                recebida.chaveDoGrupo(),
                valor(anterior == null ? null : anterior.nome(),
                        recebida.nome(), "materia", recebida.chave(), "nome",
                        contexto),
                valor(anterior == null ? null : anterior.descricao(),
                        recebida.descricao(), "materia", recebida.chave(),
                        "descricao", contexto),
                recebida.ordem(),
                valor(anterior == null ? null : anterior.peso(),
                        recebida.peso(), "materia", recebida.chave(), "peso",
                        contexto),
                valor(anterior == null ? null
                                : anterior.quantidadeDeQuestoes(),
                        recebida.quantidadeDeQuestoes(), "materia",
                        recebida.chave(), "quantidadeDeQuestoes", contexto),
                valor(anterior == null ? null : anterior.pontuacaoMaxima(),
                        recebida.pontuacaoMaxima(), "materia",
                        recebida.chave(), "pontuacaoMaxima", contexto),
                recebida.topicos().stream().map(topico ->
                        topico(topicosAnteriores.get(topico.chave()), topico,
                                contexto))
                        .toList(),
                recebida.itensDoEdital().stream().map(item ->
                        item(itensAnteriores.get(item.chave()), item,
                                contexto))
                        .toList());
    }

    private TopicoExtraido topico(TopicoExtraido anterior,
            TopicoExtraido recebida, ContextoDaSanitizacao contexto) {
        return new TopicoExtraido(recebida.chave(), recebida.chaveDoPai(),
                valor(anterior == null ? null : anterior.numeroOficial(),
                        recebida.numeroOficial(), "topico",
                        recebida.chave(), "numeroOficial", contexto),
                valor(anterior == null ? null : anterior.nome(),
                        recebida.nome(), "topico", recebida.chave(), "nome",
                        contexto),
                valor(anterior == null ? null : anterior.descricao(),
                        recebida.descricao(), "topico", recebida.chave(),
                        "descricao", contexto),
                recebida.ordem());
    }

    private ItemExtraido item(ItemExtraido anterior,
            ItemExtraido recebida, ContextoDaSanitizacao contexto) {
        ValorExtraido<String> descricao = valor(
                anterior == null ? null : anterior.descricaoLiteral(),
                recebida.descricaoLiteral(), "itemDoEdital",
                recebida.chave(), "descricaoLiteral", contexto);
        return new ItemExtraido(recebida.chave(), recebida.chaveDoPai(),
                valor(anterior == null ? null : anterior.numeroOficial(),
                        recebida.numeroOficial(), "itemDoEdital",
                        recebida.chave(), "numeroOficial", contexto),
                descricao,
                normalizador.normalizarNome(descricao == null
                        ? null : descricao.valor()),
                recebida.ordem(),
                recebida.chaveDoTopicoSugerido());
    }

    private <T> ValorExtraido<T> valor(ValorExtraido<T> anterior,
            ValorExtraido<T> recebido, String tipo, String chave,
            String campo, ContextoDaSanitizacao contexto) {
        ConfirmacaoDeCampoDaExtracao referencia =
                new ConfirmacaoDeCampoDaExtracao(tipo, chave, campo);
        if (recebido == null) {
            if (anterior != null) contexto.camposAlterados.add(referencia);
            return null;
        }
        if (anterior != null
                && Objects.equals(anterior.valor(), recebido.valor())
                && !contexto.confirmacoes.contains(referencia)) {
            return anterior;
        }
        contexto.camposAlterados.add(referencia);
        return new ValorExtraido<>(recebido.valor(), CONFIANCA_DA_CORRECAO,
                FONTE_DA_CORRECAO, false);
    }

    private static void detectarAssociacoesAlteradas(
            ExtracaoEstruturadaDoEdital anterior,
            ExtracaoEstruturadaDoEdital recebida,
            ContextoDaSanitizacao contexto) {
        if (anterior == null) return;
        Map<String, MateriaExtraida> materiasAnteriores = porChave(
                anterior.materias(), MateriaExtraida::chave);
        for (MateriaExtraida materia : recebida.materias()) {
            MateriaExtraida materiaAnterior = materiasAnteriores.get(
                    materia.chave());
            if (materiaAnterior == null) continue;
            Map<String, TopicoExtraido> topicosAnteriores = porChave(
                    materiaAnterior.topicos(), TopicoExtraido::chave);
            for (TopicoExtraido topico : materia.topicos()) {
                TopicoExtraido topicoAnterior = topicosAnteriores.get(
                        topico.chave());
                if (topicoAnterior != null && !Objects.equals(
                        topicoAnterior.chaveDoPai(), topico.chaveDoPai())) {
                    contexto.camposAlterados.add(
                            new ConfirmacaoDeCampoDaExtracao("topico",
                                    topico.chave(), "chaveDoPai"));
                }
            }
            Map<String, ItemExtraido> itensAnteriores = porChave(
                    materiaAnterior.itensDoEdital(), ItemExtraido::chave);
            for (ItemExtraido item : materia.itensDoEdital()) {
                ItemExtraido itemAnterior = itensAnteriores.get(item.chave());
                if (itemAnterior != null && !Objects.equals(
                        itemAnterior.chaveDoTopicoSugerido(),
                        item.chaveDoTopicoSugerido())) {
                    contexto.camposAlterados.add(
                            new ConfirmacaoDeCampoDaExtracao("itemDoEdital",
                                    item.chave(),
                                    "chaveDoTopicoSugerido"));
                }
            }
        }
    }

    private static <T> Map<String, T> porChave(Iterable<T> itens,
            Function<T, String> chave) {
        return java.util.stream.StreamSupport.stream(
                        itens.spliterator(), false)
                .filter(Objects::nonNull)
                .filter(item -> chave.apply(item) != null)
                .collect(Collectors.toMap(chave, Function.identity(),
                        (primeiro, ignorado) -> primeiro));
    }

    static Set<ConfirmacaoDeCampoDaExtracao> camposAlteradosEntre(
            ExtracaoEstruturadaDoEdital anterior,
            ExtracaoEstruturadaDoEdital atual) {
        Map<ConfirmacaoDeCampoDaExtracao, Object> antes =
                valoresPorCampo(anterior);
        Map<ConfirmacaoDeCampoDaExtracao, Object> depois =
                valoresPorCampo(atual);
        Set<ConfirmacaoDeCampoDaExtracao> alterados = new LinkedHashSet<>();
        Set<ConfirmacaoDeCampoDaExtracao> referencias =
                new LinkedHashSet<>(antes.keySet());
        referencias.addAll(depois.keySet());
        referencias.stream().filter(referencia ->
                        !Objects.equals(valorComparavel(
                                        antes.get(referencia)),
                                valorComparavel(depois.get(referencia)))
                                || antes.containsKey(referencia)
                                != depois.containsKey(referencia))
                .forEach(alterados::add);
        return Set.copyOf(alterados);
    }

    static Set<ConfirmacaoDeCampoDaExtracao> camposInferidosComValor(
            ExtracaoEstruturadaDoEdital extracao) {
        Set<ConfirmacaoDeCampoDaExtracao> inferidos = new LinkedHashSet<>();
        valoresPorCampo(extracao).forEach((referencia, valor) -> {
            if (valor instanceof ValorExtraido<?> extraido
                    && extraido.valor() != null && extraido.inferido()) {
                inferidos.add(referencia);
            }
        });
        return Set.copyOf(inferidos);
    }

    private static Object valorComparavel(Object valor) {
        return valor instanceof ValorExtraido<?> extraido
                ? extraido.valor() : valor;
    }

    private static Map<ConfirmacaoDeCampoDaExtracao, Object> valoresPorCampo(
            ExtracaoEstruturadaDoEdital extracao) {
        Map<ConfirmacaoDeCampoDaExtracao, Object> valores =
                new java.util.LinkedHashMap<>();
        if (extracao == null) return valores;
        if (extracao.concurso() != null) {
            adicionar(valores, "concurso", "concurso", "nome",
                    extracao.concurso().nome());
            adicionar(valores, "concurso", "concurso", "descricao",
                    extracao.concurso().descricao());
            adicionar(valores, "concurso", "concurso", "orgao",
                    extracao.concurso().orgao());
            adicionar(valores, "concurso", "concurso", "banca",
                    extracao.concurso().banca());
            adicionar(valores, "concurso", "concurso", "dataPrevista",
                    extracao.concurso().dataPrevista());
        }
        if (extracao.edital() != null) {
            adicionar(valores, "edital", "edital", "titulo",
                    extracao.edital().titulo());
            adicionar(valores, "edital", "edital", "numero",
                    extracao.edital().numero());
            adicionar(valores, "edital", "edital", "ano",
                    extracao.edital().ano());
            adicionar(valores, "edital", "edital", "descricao",
                    extracao.edital().descricao());
            adicionar(valores, "edital", "edital", "dataDePublicacao",
                    extracao.edital().dataDePublicacao());
        }
        extracao.cargos().forEach(cargo -> {
            adicionar(valores, "cargo", cargo.chave(), "nome", cargo.nome());
            adicionar(valores, "cargo", cargo.chave(), "area", cargo.area());
            adicionar(valores, "cargo", cargo.chave(), "especialidade",
                    cargo.especialidade());
            adicionar(valores, "cargo", cargo.chave(),
                    "nivelDeEscolaridade", cargo.nivelDeEscolaridade());
        });
        extracao.provas().forEach(prova -> {
            adicionar(valores, "prova", prova.chave(), "nome", prova.nome());
            adicionar(valores, "prova", prova.chave(), "tipo", prova.tipo());
            adicionar(valores, "prova", prova.chave(), "carater",
                    prova.carater());
            adicionar(valores, "prova", prova.chave(), "dataHora",
                    prova.dataHora());
            adicionar(valores, "prova", prova.chave(),
                    "duracaoEmMinutos", prova.duracaoEmMinutos());
            adicionar(valores, "prova", prova.chave(),
                    "quantidadeDeQuestoes", prova.quantidadeDeQuestoes());
            adicionar(valores, "prova", prova.chave(), "pontuacaoMaxima",
                    prova.pontuacaoMaxima());
            adicionar(valores, "prova", prova.chave(), "pontuacaoMinima",
                    prova.pontuacaoMinima());
            prova.grupos().forEach(grupo -> {
                adicionar(valores, "grupo", grupo.chave(), "nome",
                        grupo.nome());
                adicionar(valores, "grupo", grupo.chave(),
                        "quantidadeDeQuestoes",
                        grupo.quantidadeDeQuestoes());
                adicionar(valores, "grupo", grupo.chave(),
                        "pontuacaoMaxima", grupo.pontuacaoMaxima());
                adicionar(valores, "grupo", grupo.chave(),
                        "pontuacaoMinima", grupo.pontuacaoMinima());
            });
        });
        extracao.materias().forEach(materia -> {
            adicionar(valores, "materia", materia.chave(), "nome",
                    materia.nome());
            adicionar(valores, "materia", materia.chave(), "descricao",
                    materia.descricao());
            adicionar(valores, "materia", materia.chave(), "peso",
                    materia.peso());
            adicionar(valores, "materia", materia.chave(),
                    "quantidadeDeQuestoes",
                    materia.quantidadeDeQuestoes());
            adicionar(valores, "materia", materia.chave(),
                    "pontuacaoMaxima", materia.pontuacaoMaxima());
            materia.topicos().forEach(topico -> {
                adicionar(valores, "topico", topico.chave(),
                        "numeroOficial", topico.numeroOficial());
                adicionar(valores, "topico", topico.chave(), "nome",
                        topico.nome());
                adicionar(valores, "topico", topico.chave(), "descricao",
                        topico.descricao());
                valores.put(new ConfirmacaoDeCampoDaExtracao("topico",
                        topico.chave(), "chaveDoPai"), topico.chaveDoPai());
            });
            materia.itensDoEdital().forEach(item -> {
                adicionar(valores, "itemDoEdital", item.chave(),
                        "numeroOficial", item.numeroOficial());
                adicionar(valores, "itemDoEdital", item.chave(),
                        "descricaoLiteral", item.descricaoLiteral());
                valores.put(new ConfirmacaoDeCampoDaExtracao(
                        "itemDoEdital", item.chave(),
                        "chaveDoTopicoSugerido"),
                        item.chaveDoTopicoSugerido());
            });
        });
        return valores;
    }

    private static void adicionar(
            Map<ConfirmacaoDeCampoDaExtracao, Object> valores,
            String tipo, String chave, String campo,
            ValorExtraido<?> valor) {
        valores.put(new ConfirmacaoDeCampoDaExtracao(tipo, chave, campo),
                valor);
    }

    record ResultadoDaSanitizacao(
            ExtracaoEstruturadaDoEdital extracao,
            Set<ConfirmacaoDeCampoDaExtracao> camposAlterados) {
    }

    private static final class ContextoDaSanitizacao {
        private final Set<ConfirmacaoDeCampoDaExtracao> confirmacoes;
        private final Set<ConfirmacaoDeCampoDaExtracao> camposAlterados =
                new LinkedHashSet<>();

        private ContextoDaSanitizacao(
                Set<ConfirmacaoDeCampoDaExtracao> confirmacoes) {
            this.confirmacoes = confirmacoes == null
                    ? Set.of() : Set.copyOf(confirmacoes);
            camposAlterados.addAll(this.confirmacoes);
        }
    }
}
