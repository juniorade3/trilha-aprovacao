package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.CargoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ItemExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.MateriaExtraida;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ProvaExtraida;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.TopicoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ProblemaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.SeveridadeDoProblemaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.ValorExtraido;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValidadorDaExtracaoDoEdital {
    private static final Pattern CAMINHO_DO_CARGO = Pattern.compile(
            "^cargos\\[(\\d+)](?:\\.(.+))?$");
    private static final Pattern CAMINHO_DO_GRUPO = Pattern.compile(
            "^provas\\[(\\d+)]\\.grupos\\[(\\d+)](?:\\.(.+))?$");
    private static final Pattern CAMINHO_DA_PROVA = Pattern.compile(
            "^provas\\[(\\d+)](?:\\.(.+))?$");
    private static final Pattern CAMINHO_DO_TOPICO = Pattern.compile(
            "^materias\\[(\\d+)]\\.topicos\\[(\\d+)](?:\\.(.+))?$");
    private static final Pattern CAMINHO_DO_ITEM = Pattern.compile(
            "^materias\\[(\\d+)]\\.itensDoEdital\\[(\\d+)](?:\\.(.+))?$");
    private static final Pattern CAMINHO_DA_MATERIA = Pattern.compile(
            "^materias\\[(\\d+)](?:\\.(.+))?$");

    private final NormalizadorDoTextoDoEdital normalizador;

    public ValidadorDaExtracaoDoEdital() {
        this(new NormalizadorDoTextoDoEdital());
    }

    public ValidadorDaExtracaoDoEdital(
            NormalizadorDoTextoDoEdital normalizador) {
        this.normalizador = normalizador;
    }

    public List<ProblemaDaImportacao> validar(
            ExtracaoEstruturadaDoEdital extracao) {
        List<ProblemaDaImportacao> problemas = new ArrayList<>();
        if (extracao == null) {
            problemas.add(bloqueante("EXTRACAO_AUSENTE",
                    "Extracao estruturada ausente.", null));
            return List.copyOf(problemas);
        }
        obrigatorio(extracao.concurso() == null ? null
                        : extracao.concurso().nome(), "CONCURSO_SEM_NOME",
                "Nome do concurso obrigatorio.", "concurso.nome", problemas);
        obrigatorio(extracao.edital() == null ? null
                        : extracao.edital().titulo(), "EDITAL_SEM_TITULO",
                "Titulo do edital obrigatorio.", "edital.titulo", problemas);
        if (extracao.cargos().isEmpty()) problemas.add(bloqueante(
                "CARGO_AUSENTE", "Ao menos um cargo e obrigatorio.", "cargos"));
        if (extracao.cargos().size() > 1) problemas.add(decisao(
                "SELECAO_DE_CARGO_OBRIGATORIA",
                "Edital possui varios cargos; escolha um explicitamente.",
                "cargos"));
        if (extracao.materias().isEmpty()) problemas.add(bloqueante(
                "MATERIA_AUSENTE", "Ao menos uma materia e obrigatoria.",
                "materias"));

        Set<String> cargos = chaves(extracao.cargos(),
                ExtracaoEstruturadaDoEdital.CargoExtraido::chave, "cargos",
                problemas);
        Set<String> provas = chaves(extracao.provas(), ProvaExtraida::chave,
                "provas", problemas);
        Set<String> grupos = new HashSet<>();
        Map<String, String> cargoDaProva = new HashMap<>();
        Map<String, String> provaDoGrupo = new HashMap<>();
        validarOrdens(extracao.cargos(),
                ExtracaoEstruturadaDoEdital.CargoExtraido::ordem, "cargos",
                problemas);
        extracao.provas().stream().collect(java.util.stream.Collectors.groupingBy(
                ProvaExtraida::chaveDoCargo)).forEach((cargo, itens) ->
                        validarOrdens(itens, ProvaExtraida::ordem,
                                "provas[cargo=" + cargo + "]", problemas));

        for (int indice = 0; indice < extracao.cargos().size(); indice++) {
            var cargo = extracao.cargos().get(indice);
            String caminho = "cargos[" + indice + "]";
            obrigatorio(cargo.nome(), "CARGO_SEM_NOME",
                    "Nome do cargo obrigatorio.", caminho + ".nome", problemas);
            obrigatorio(cargo.nivelDeEscolaridade(),
                    "CARGO_SEM_ESCOLARIDADE",
                    "Escolaridade do cargo exige revisao.",
                    caminho + ".nivelDeEscolaridade", problemas,
                    SeveridadeDoProblemaDaImportacao.EXIGE_DECISAO);
            tamanho(cargo.nome(), 160, caminho + ".nome", problemas);
            tamanho(cargo.area(), 160, caminho + ".area", problemas);
            tamanho(cargo.especialidade(), 160, caminho + ".especialidade",
                    problemas);
        }

        for (int indice = 0; indice < extracao.provas().size(); indice++) {
            ProvaExtraida prova = extracao.provas().get(indice);
            cargoDaProva.put(prova.chave(), prova.chaveDoCargo());
            String caminho = "provas[" + indice + "]";
            if (!cargos.contains(prova.chaveDoCargo())) problemas.add(bloqueante(
                    "PROVA_SEM_CARGO_VALIDO",
                    "Prova referencia cargo ausente.", caminho + ".chaveDoCargo"));
            obrigatorio(prova.nome(), "PROVA_SEM_NOME",
                    "Nome da prova obrigatorio.", caminho + ".nome", problemas);
            obrigatorio(prova.tipo(), "PROVA_SEM_TIPO",
                    "Tipo da prova exige revisao.", caminho + ".tipo", problemas,
                    SeveridadeDoProblemaDaImportacao.EXIGE_DECISAO);
            obrigatorio(prova.carater(), "PROVA_SEM_CARATER",
                    "Carater da prova exige revisao.", caminho + ".carater",
                    problemas, SeveridadeDoProblemaDaImportacao.EXIGE_DECISAO);
            tamanho(prova.nome(), 160, caminho + ".nome", problemas);
            positivo(prova.duracaoEmMinutos(), caminho + ".duracaoEmMinutos",
                    problemas);
            positivo(prova.quantidadeDeQuestoes(),
                    caminho + ".quantidadeDeQuestoes", problemas);
            pontuacao(prova.pontuacaoMinima(), prova.pontuacaoMaxima(), caminho,
                    problemas);
            if (prova.grupos().isEmpty()) problemas.add(bloqueante(
                    "PROVA_SEM_GRUPO", "Prova deve possuir grupo.",
                    caminho + ".grupos"));
            validarOrdens(prova.grupos(),
                    ExtracaoEstruturadaDoEdital.GrupoExtraido::ordem,
                    caminho + ".grupos", problemas);
            for (int indiceDoGrupo = 0;
                    indiceDoGrupo < prova.grupos().size(); indiceDoGrupo++) {
                var item = prova.grupos().get(indiceDoGrupo);
                String caminhoDoGrupo = caminho + ".grupos["
                        + indiceDoGrupo + "]";
                provaDoGrupo.put(item.chave(), prova.chave());
                if (!grupos.add(item.chave())) problemas.add(bloqueante(
                        "CHAVE_DUPLICADA", "Chave de grupo duplicada.",
                        caminhoDoGrupo));
                tamanho(item.nome(), 160, caminhoDoGrupo + ".nome", problemas);
                positivo(item.quantidadeDeQuestoes(),
                        caminhoDoGrupo + ".quantidadeDeQuestoes", problemas);
                pontuacao(item.pontuacaoMinima(), item.pontuacaoMaxima(),
                        caminhoDoGrupo, problemas);
            }
        }

        Set<String> nomesDeMaterias = new HashSet<>();
        Set<String> chavesDeTopicos = new HashSet<>();
        Set<String> chavesDeItens = new HashSet<>();
        chaves(extracao.materias(), MateriaExtraida::chave, "materias",
                problemas);
        extracao.materias().stream().collect(java.util.stream.Collectors.groupingBy(
                item -> item.chaveDoCargo() + "\0" + item.chaveDaProva()
                        + "\0" + item.chaveDoGrupo())).forEach((grupo, itens) ->
                                validarOrdens(itens, MateriaExtraida::ordem,
                                        "materias[grupo=" + grupo + "]",
                                        problemas));
        for (int indice = 0; indice < extracao.materias().size(); indice++) {
            MateriaExtraida materia = extracao.materias().get(indice);
            String caminho = "materias[" + indice + "]";
            if (!cargos.contains(materia.chaveDoCargo())
                    || !materia.chaveDoCargo().equals(
                            cargoDaProva.get(materia.chaveDaProva()))
                    || !materia.chaveDaProva().equals(
                            provaDoGrupo.get(materia.chaveDoGrupo()))) {
                problemas.add(bloqueante("ASSOCIACAO_DA_MATERIA_INVALIDA",
                        "Materia referencia cargo, prova ou grupo ausente.", caminho));
            }
            obrigatorio(materia.nome(), "MATERIA_SEM_NOME",
                    "Nome da materia obrigatorio.", caminho + ".nome", problemas);
            String nome = valor(materia.nome()) == null ? null
                    : normalizador.normalizarNome(valor(materia.nome()));
            if (nome != null && !nomesDeMaterias.add(nome)) problemas.add(decisao(
                    "MATERIA_DUPLICADA", "Materia equivalente repetida no lote.",
                    caminho + ".nome"));
            tamanho(materia.nome(), 120, caminho + ".nome", problemas);
            tamanho(materia.descricao(), 1000, caminho + ".descricao", problemas);
            positivo(materia.peso(), caminho + ".peso", problemas);
            positivo(materia.quantidadeDeQuestoes(),
                    caminho + ".quantidadeDeQuestoes", problemas);
            positivo(materia.pontuacaoMaxima(),
                    caminho + ".pontuacaoMaxima", problemas);
            if (materia.topicos().isEmpty() && materia.itensDoEdital().isEmpty()) {
                problemas.add(bloqueante("MATERIA_SEM_CONTEUDO",
                        "Materia deve possuir topico ou item literal.", caminho));
            }
            materia.topicos().forEach(topico -> {
                if (!chavesDeTopicos.add(topico.chave())) {
                    problemas.add(bloqueante("CHAVE_GLOBAL_DUPLICADA",
                            "Chave de topico repetida entre materias.",
                            caminho + ".topicos"));
                }
            });
            materia.itensDoEdital().forEach(item -> {
                if (!chavesDeItens.add(item.chave())) {
                    problemas.add(bloqueante("CHAVE_GLOBAL_DUPLICADA",
                            "Chave de item repetida entre materias.",
                            caminho + ".itensDoEdital"));
                }
            });
            validarHierarquia(materia, caminho, problemas);
        }
        validarLimitesDaRaiz(extracao, problemas);
        validarProveniencias(extracao, problemas);
        return estabilizarReferencias(extracao, problemas);
    }

    public List<ProblemaDaImportacao> validarParaCargo(
            ExtracaoEstruturadaDoEdital extracao, String chaveDoCargo) {
        if (extracao == null) return validar(null);
        if (chaveDoCargo == null || chaveDoCargo.isBlank()
                || extracao.cargos().stream().noneMatch(cargo ->
                        Objects.equals(cargo.chave(), chaveDoCargo))) {
            return List.of(new ProblemaDaImportacao(
                    SeveridadeDoProblemaDaImportacao.BLOQUEANTE,
                    "CARGO_ALVO_INVALIDO",
                    "Cargo selecionado nao pertence a extracao.", "cargos",
                    "cargo", chaveDoCargo, "chave"));
        }
        List<CargoExtraido> cargos = extracao.cargos().stream()
                .filter(cargo -> Objects.equals(cargo.chave(), chaveDoCargo))
                .toList();
        List<ProvaExtraida> provas = extracao.provas().stream()
                .filter(prova -> Objects.equals(prova.chaveDoCargo(),
                        chaveDoCargo))
                .toList();
        List<MateriaExtraida> materias = extracao.materias().stream()
                .filter(materia -> Objects.equals(materia.chaveDoCargo(),
                        chaveDoCargo))
                .toList();
        var extracaoDoCargo = new ExtracaoEstruturadaDoEdital(
                extracao.versaoDoContrato(), extracao.fonte(),
                extracao.concurso(), extracao.edital(), cargos, provas,
                materias, extracao.avisos(), extracao.incertezas());
        return referenciarColecoesNoCargo(validar(extracaoDoCargo),
                chaveDoCargo);
    }

    public List<AvaliacaoDoCargo> avaliarCargos(
            ExtracaoEstruturadaDoEdital extracao) {
        if (extracao == null) return List.of();
        Set<String> avaliadas = new LinkedHashSet<>();
        List<AvaliacaoDoCargo> avaliacoes = new ArrayList<>();
        for (CargoExtraido cargo : extracao.cargos()) {
            if (cargo.chave() == null || cargo.chave().isBlank()
                    || !avaliadas.add(cargo.chave())) {
                continue;
            }
            List<ProblemaDaImportacao> problemas = validarParaCargo(extracao,
                    cargo.chave());
            boolean pronto = !possuiBloqueante(problemas)
                    && !exigeDecisao(problemas);
            avaliacoes.add(new AvaliacaoDoCargo(cargo.chave(), pronto,
                    problemas));
        }
        return List.copyOf(avaliacoes);
    }

    public boolean possuiBloqueante(List<ProblemaDaImportacao> problemas) {
        return problemas.stream().anyMatch(item -> item.severidade()
                == SeveridadeDoProblemaDaImportacao.BLOQUEANTE);
    }

    public boolean exigeDecisao(List<ProblemaDaImportacao> problemas) {
        return problemas.stream().anyMatch(item -> item.severidade()
                == SeveridadeDoProblemaDaImportacao.EXIGE_DECISAO);
    }

    private void validarHierarquia(MateriaExtraida materia, String caminho,
            List<ProblemaDaImportacao> problemas) {
        Set<String> topicos = chaves(materia.topicos(), TopicoExtraido::chave,
                caminho + ".topicos", problemas);
        Set<String> itens = chaves(materia.itensDoEdital(), ItemExtraido::chave,
                caminho + ".itensDoEdital", problemas);
        validarOrdens(materia.topicos(), TopicoExtraido::ordem,
                caminho + ".topicos", problemas);
        validarOrdens(materia.itensDoEdital(), ItemExtraido::ordem,
                caminho + ".itensDoEdital", problemas);
        for (int indice = 0; indice < materia.topicos().size(); indice++) {
            TopicoExtraido item = materia.topicos().get(indice);
            String caminhoDoTopico = caminho + ".topicos[" + indice + "]";
            tamanho(item.nome(), 160, caminhoDoTopico + ".nome", problemas);
            tamanho(item.descricao(), 1000, caminhoDoTopico + ".descricao",
                    problemas);
            if (item.chaveDoPai() != null && (!topicos.contains(item.chaveDoPai())
                    || possuiCiclo(item.chave(), item.chaveDoPai(),
                            materia.topicos(), TopicoExtraido::chave,
                            TopicoExtraido::chaveDoPai))) {
                problemas.add(bloqueante("HIERARQUIA_DE_TOPICOS_INVALIDA",
                        "Topico possui pai ausente ou ciclo.",
                        caminhoDoTopico));
            }
        }
        for (int indice = 0;
                indice < materia.itensDoEdital().size(); indice++) {
            ItemExtraido item = materia.itensDoEdital().get(indice);
            String caminhoDoItem = caminho + ".itensDoEdital[" + indice + "]";
            if (item.chaveDoPai() != null && (!itens.contains(item.chaveDoPai())
                    || possuiCiclo(item.chave(), item.chaveDoPai(),
                            materia.itensDoEdital(), ItemExtraido::chave,
                            ItemExtraido::chaveDoPai))) {
                problemas.add(bloqueante("HIERARQUIA_DE_ITENS_INVALIDA",
                        "Item possui pai ausente ou ciclo.",
                        caminhoDoItem));
            }
            obrigatorio(item.descricaoLiteral(), "ITEM_SEM_DESCRICAO",
                    "Item literal sem descricao.",
                    caminhoDoItem + ".descricaoLiteral",
                    problemas);
            tamanho(item.numeroOficial(), 80,
                    caminhoDoItem + ".numeroOficial", problemas);
            if (item.chaveDoTopicoSugerido() != null
                    && !topicos.contains(item.chaveDoTopicoSugerido())) {
                problemas.add(bloqueante("TOPICO_SUGERIDO_INVALIDO",
                        "Item referencia topico sugerido ausente.",
                        caminhoDoItem + ".chaveDoTopicoSugerido"));
            }
        }
    }

    private void validarProveniencias(ExtracaoEstruturadaDoEdital extracao,
            List<ProblemaDaImportacao> problemas) {
        List<ValorExtraido<?>> valores = new ArrayList<>();
        if (extracao.concurso() != null) adicionarPresentes(valores,
                extracao.concurso().nome(), extracao.concurso().descricao(),
                extracao.concurso().orgao(), extracao.concurso().banca(),
                extracao.concurso().dataPrevista());
        if (extracao.edital() != null) adicionarPresentes(valores,
                extracao.edital().titulo(), extracao.edital().numero(),
                extracao.edital().ano(), extracao.edital().descricao(),
                extracao.edital().dataDePublicacao());
        extracao.cargos().forEach(item -> adicionarPresentes(valores,
                item.nome(), item.area(), item.especialidade(),
                item.nivelDeEscolaridade()));
        extracao.provas().forEach(item -> {
            adicionarPresentes(valores, item.nome(), item.tipo(), item.carater(),
                    item.dataHora(), item.duracaoEmMinutos(),
                    item.quantidadeDeQuestoes(), item.pontuacaoMaxima(),
                    item.pontuacaoMinima());
            item.grupos().forEach(grupo -> adicionarPresentes(valores,
                    grupo.nome(),
                    grupo.quantidadeDeQuestoes(), grupo.pontuacaoMaxima(),
                    grupo.pontuacaoMinima()));
        });
        extracao.materias().forEach(item -> {
            adicionarPresentes(valores, item.nome(), item.descricao(),
                    item.peso(), item.quantidadeDeQuestoes(),
                    item.pontuacaoMaxima());
            item.topicos().forEach(topico -> adicionarPresentes(valores,
                    topico.numeroOficial(), topico.nome(),
                    topico.descricao()));
            item.itensDoEdital().forEach(registro -> adicionarPresentes(valores,
                    registro.numeroOficial(), registro.descricaoLiteral()));
        });
        for (ValorExtraido<?> valor : valores) {
            if (valor.valor() != null && !valor.inferido()
                    && valor.fonte() == null) problemas.add(bloqueante(
                            "PROVENIENCIA_AUSENTE",
                            "Dado explicito sem proveniencia.", null));
            if (valor.fonte() != null
                    && valor.fonte().pagina() != null
                    && valor.fonte().pagina() > extracao.fonte().paginas()) {
                problemas.add(bloqueante("PAGINA_DE_PROVENIENCIA_INVALIDA",
                        "Proveniencia referencia pagina inexistente.", null));
            }
        }
    }

    private static void adicionarPresentes(List<ValorExtraido<?>> destino,
            ValorExtraido<?>... candidatos) {
        for (ValorExtraido<?> candidato : candidatos) {
            if (candidato != null) destino.add(candidato);
        }
    }

    private static <T> Set<String> chaves(List<T> itens,
            Function<T, String> chave, String caminho,
            List<ProblemaDaImportacao> problemas) {
        Set<String> chaves = new HashSet<>();
        for (T item : itens) {
            String valor = chave.apply(item);
            if (valor == null || valor.isBlank() || !chaves.add(valor)) {
                problemas.add(bloqueante("CHAVE_DUPLICADA",
                        "Chave ausente ou duplicada.", caminho));
            }
        }
        return chaves;
    }

    private static List<ProblemaDaImportacao> estabilizarReferencias(
            ExtracaoEstruturadaDoEdital extracao,
            List<ProblemaDaImportacao> problemas) {
        List<ProblemaDaImportacao> resultado = new ArrayList<>();
        for (ProblemaDaImportacao problema : problemas) {
            resultado.add(estabilizarReferencia(extracao, problema));
        }
        return List.copyOf(resultado);
    }

    private static ProblemaDaImportacao estabilizarReferencia(
            ExtracaoEstruturadaDoEdital extracao,
            ProblemaDaImportacao problema) {
        String caminho = problema.caminho();
        if (caminho == null) return problema;

        Matcher grupo = CAMINHO_DO_GRUPO.matcher(caminho);
        if (grupo.matches()) {
            ProvaExtraida prova = item(extracao.provas(), grupo.group(1));
            if (prova != null) {
                var registro = item(prova.grupos(), grupo.group(2));
                if (registro != null) return problema.comReferencia("grupo",
                        registro.chave(), grupo.group(3));
                return problema.comReferencia("prova", prova.chave(),
                        "grupos");
            }
        }

        Matcher topico = CAMINHO_DO_TOPICO.matcher(caminho);
        if (topico.matches()) {
            MateriaExtraida materia = item(extracao.materias(),
                    topico.group(1));
            if (materia != null) {
                TopicoExtraido registro = item(materia.topicos(),
                        topico.group(2));
                if (registro != null) return problema.comReferencia("topico",
                        registro.chave(), topico.group(3));
                return problema.comReferencia("materia", materia.chave(),
                        "topicos");
            }
        }

        Matcher itemDoEdital = CAMINHO_DO_ITEM.matcher(caminho);
        if (itemDoEdital.matches()) {
            MateriaExtraida materia = item(extracao.materias(),
                    itemDoEdital.group(1));
            if (materia != null) {
                ItemExtraido registro = item(materia.itensDoEdital(),
                        itemDoEdital.group(2));
                if (registro != null) return problema.comReferencia(
                        "itemDoEdital", registro.chave(),
                        itemDoEdital.group(3));
                return problema.comReferencia("materia", materia.chave(),
                        "itensDoEdital");
            }
        }

        Matcher cargo = CAMINHO_DO_CARGO.matcher(caminho);
        if (cargo.matches()) {
            CargoExtraido registro = item(extracao.cargos(), cargo.group(1));
            if (registro != null) return problema.comReferencia("cargo",
                    registro.chave(), cargo.group(2));
        }

        Matcher prova = CAMINHO_DA_PROVA.matcher(caminho);
        if (prova.matches()) {
            ProvaExtraida registro = item(extracao.provas(), prova.group(1));
            if (registro != null) return problema.comReferencia("prova",
                    registro.chave(), prova.group(2));
        }

        Matcher materia = CAMINHO_DA_MATERIA.matcher(caminho);
        if (materia.matches()) {
            MateriaExtraida registro = item(extracao.materias(),
                    materia.group(1));
            if (registro != null) return problema.comReferencia("materia",
                    registro.chave(), materia.group(2));
        }

        if (caminho.startsWith("concurso")) {
            return problema.comReferencia("concurso", "concurso",
                    campoDepoisDaRaiz(caminho));
        }
        if (caminho.startsWith("edital")) {
            return problema.comReferencia("edital", "edital",
                    campoDepoisDaRaiz(caminho));
        }
        if (caminho.startsWith("provas[cargo=")) {
            return problema.comReferencia("cargo",
                    entre(caminho, "provas[cargo=", "]"), "provas");
        }
        if (caminho.startsWith("materias[grupo=")) {
            String grupoComAssociacoes = entre(caminho, "materias[grupo=",
                    "]");
            int separador = grupoComAssociacoes == null ? -1
                    : grupoComAssociacoes.lastIndexOf('\0');
            String chaveDoGrupo = separador < 0 ? grupoComAssociacoes
                    : grupoComAssociacoes.substring(separador + 1);
            return problema.comReferencia("grupo", chaveDoGrupo, "materias");
        }
        if ("cargos".equals(caminho) || "provas".equals(caminho)
                || "materias".equals(caminho)) {
            return problema.comReferencia("extracao", "extracao", caminho);
        }
        return problema;
    }

    private static List<ProblemaDaImportacao> referenciarColecoesNoCargo(
            List<ProblemaDaImportacao> problemas, String chaveDoCargo) {
        return problemas.stream().map(problema -> switch (problema.codigo()) {
            case "MATERIA_AUSENTE" -> problema.comReferencia("cargo",
                    chaveDoCargo, "materias");
            default -> problema;
        }).toList();
    }

    private static <T> T item(List<T> itens, String indice) {
        try {
            int valor = Integer.parseInt(indice);
            return valor >= 0 && valor < itens.size() ? itens.get(valor) : null;
        } catch (NumberFormatException excecao) {
            return null;
        }
    }

    private static String campoDepoisDaRaiz(String caminho) {
        int separador = caminho.indexOf('.');
        return separador < 0 ? null : caminho.substring(separador + 1);
    }

    private static String entre(String valor, String inicio, String fim) {
        int primeiro = valor.indexOf(inicio);
        if (primeiro < 0) return null;
        primeiro += inicio.length();
        int ultimo = valor.indexOf(fim, primeiro);
        return ultimo < 0 ? null : valor.substring(primeiro, ultimo);
    }

    private static <T> void validarOrdens(List<T> itens,
            Function<T, Integer> ordem, String caminho,
            List<ProblemaDaImportacao> problemas) {
        Set<Integer> ordens = new HashSet<>();
        if (itens.stream().anyMatch(item -> ordem.apply(item) < 1
                || !ordens.add(ordem.apply(item)))) problemas.add(bloqueante(
                        "ORDEM_INVALIDA", "Ordem ausente, negativa ou repetida.",
                        caminho));
    }

    private static <T> boolean possuiCiclo(String inicial, String pai,
            List<T> itens, Function<T, String> chave,
            Function<T, String> chaveDoPai) {
        Map<String, String> pais = new HashMap<>();
        itens.forEach(item -> pais.put(chave.apply(item), chaveDoPai.apply(item)));
        Set<String> visitados = new HashSet<>();
        visitados.add(inicial);
        String atual = pai;
        while (atual != null) {
            if (!visitados.add(atual)) return true;
            atual = pais.get(atual);
        }
        return false;
    }

    private static void pontuacao(ValorExtraido<BigDecimal> minima,
            ValorExtraido<BigDecimal> maxima, String caminho,
            List<ProblemaDaImportacao> problemas) {
        positivo(minima, caminho + ".pontuacaoMinima", problemas);
        positivo(maxima, caminho + ".pontuacaoMaxima", problemas);
        if (valor(minima) != null && valor(maxima) != null
                && valor(minima).compareTo(valor(maxima)) > 0) {
            problemas.add(bloqueante("PONTUACAO_INCOERENTE",
                    "Pontuacao minima supera maxima.", caminho));
        }
    }

    private static void positivo(ValorExtraido<? extends Number> valor,
            String caminho, List<ProblemaDaImportacao> problemas) {
        if (valor(valor) != null && new BigDecimal(valor(valor).toString())
                .compareTo(BigDecimal.ZERO) <= 0) problemas.add(bloqueante(
                        "NUMERO_NAO_POSITIVO", "Numero deve ser positivo.", caminho));
    }

    private static void validarLimitesDaRaiz(
            ExtracaoEstruturadaDoEdital extracao,
            List<ProblemaDaImportacao> problemas) {
        if (extracao.concurso() != null) {
            tamanho(extracao.concurso().nome(), 160, "concurso.nome", problemas);
            tamanho(extracao.concurso().descricao(), 1000,
                    "concurso.descricao", problemas);
            tamanho(extracao.concurso().orgao(), 160, "concurso.orgao", problemas);
            tamanho(extracao.concurso().banca(), 160, "concurso.banca", problemas);
        }
        if (extracao.edital() != null) {
            tamanho(extracao.edital().titulo(), 200, "edital.titulo", problemas);
            tamanho(extracao.edital().numero(), 80, "edital.numero", problemas);
            tamanho(extracao.edital().descricao(), 1000, "edital.descricao",
                    problemas);
            positivo(extracao.edital().ano(), "edital.ano", problemas);
        }
    }

    private static void tamanho(ValorExtraido<String> campo, int limite,
            String caminho, List<ProblemaDaImportacao> problemas) {
        if (valor(campo) != null && valor(campo).length() > limite) {
            problemas.add(bloqueante("TEXTO_EXCEDE_LIMITE",
                    "Texto excede limite de " + limite + " caracteres.", caminho));
        }
    }

    private static void obrigatorio(ValorExtraido<?> valor, String codigo,
            String mensagem, String caminho,
            List<ProblemaDaImportacao> problemas) {
        obrigatorio(valor, codigo, mensagem, caminho, problemas,
                SeveridadeDoProblemaDaImportacao.BLOQUEANTE);
    }

    private static void obrigatorio(ValorExtraido<?> valor, String codigo,
            String mensagem, String caminho, List<ProblemaDaImportacao> problemas,
            SeveridadeDoProblemaDaImportacao severidade) {
        if (valor(valor) == null) problemas.add(new ProblemaDaImportacao(
                severidade, codigo, mensagem, caminho));
    }

    private static <T> T valor(ValorExtraido<T> valor) {
        return valor == null ? null : valor.valor();
    }

    private static ProblemaDaImportacao bloqueante(String codigo,
            String mensagem, String caminho) {
        return new ProblemaDaImportacao(
                SeveridadeDoProblemaDaImportacao.BLOQUEANTE, codigo, mensagem,
                caminho);
    }

    private static ProblemaDaImportacao decisao(String codigo,
            String mensagem, String caminho) {
        return new ProblemaDaImportacao(
                SeveridadeDoProblemaDaImportacao.EXIGE_DECISAO, codigo,
                mensagem, caminho);
    }
}
