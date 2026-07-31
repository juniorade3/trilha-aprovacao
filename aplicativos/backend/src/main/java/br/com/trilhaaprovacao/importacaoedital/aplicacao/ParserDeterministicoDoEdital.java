package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.concursos.dominio.CaraterDaProva;
import br.com.trilhaaprovacao.concursos.dominio.NivelDeEscolaridade;
import br.com.trilhaaprovacao.concursos.dominio.TipoDeProva;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.CargoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ConcursoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.EditalExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.FonteDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.GrupoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ItemExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.MateriaExtraida;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ProvaExtraida;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.TopicoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ProvenienciaDoDado;
import br.com.trilhaaprovacao.importacaoedital.dominio.ValorExtraido;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser conservador para linhas rotuladas e para a estrutura explicita dos
 * editais Cebraspe. Campo nao reconhecido fica ausente.
 */
public class ParserDeterministicoDoEdital {
    public static final String VERSAO = "deterministico-2";
    private static final int SEM_DIFERENCA_DE_CAIXA =
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern LINHA = Pattern.compile(
            "^\\s*([\\p{L}][\\p{L} \\-/]*?)\\s*:\\s*(.+?)\\s*$");
    private static final Pattern NUMERADO = Pattern.compile(
            "^([0-9]+(?:\\.[0-9]+)*)\\s*(?:[-–—|:]\\s*)?(.+)$");
    private static final Pattern CONCURSO_CEBRASPE = Pattern.compile(
            "^CONCURSO\\s+P[ÚU]BLICO\\b.+$", SEM_DIFERENCA_DE_CAIXA);
    private static final Pattern EDITAL_CEBRASPE = Pattern.compile(
            "^EDITAL\\s+(?:N[º°]\\.?|N[ÚU]MERO)\\s*"
                    + "([\\p{Alnum}./-]+).*$",
            SEM_DIFERENCA_DE_CAIXA);
    private static final Pattern ANO_EXPLICITO = Pattern.compile(
            "\\b(?:19|20)[0-9]{2}\\b");
    private static final Pattern CARGO_CEBRASPE = Pattern.compile(
            "^[0-9]+(?:\\.[0-9]+)*\\s+DO\\s+CARGO\\s+DE\\s+(.+)$",
            SEM_DIFERENCA_DE_CAIXA);
    private static final Pattern SEGMENTO_DO_CARGO = Pattern.compile(
            "(?:^|\\s+[–—-]\\s*)([ÁA]REA|ESPECIALIDADE|"
                    + "ORIENTA[ÇC][ÃA]O)\\s*:\\s*",
            SEM_DIFERENCA_DE_CAIXA);
    private static final Pattern ESPECIALIDADE_COM_ORIENTACAO =
            Pattern.compile(
                    "^(.+?)\\s+[–—-]\\s+ORIENTA[ÇC][ÃA]O\\s*:\\s*(.+)$",
                    SEM_DIFERENCA_DE_CAIXA);
    private static final Pattern OBJETOS_DE_AVALIACAO = Pattern.compile(
            "^[0-9]+(?:\\.[0-9]+)*\\s+DOS\\s+OBJETOS\\s+DE\\s+"
                    + "AVALIA[ÇC][ÃA]O\\b(.*)$",
            SEM_DIFERENCA_DE_CAIXA);
    private static final Pattern GRUPO_CEBRASPE = Pattern.compile(
            "^[0-9]+(?:\\.[0-9]+)*\\s+(CONHECIMENTOS\\s+"
                    + "(?:B[ÁA]SICOS|ESPEC[ÍI]FICOS))\\s*$",
            SEM_DIFERENCA_DE_CAIXA);
    private static final Pattern INICIO_DE_ITEM_CEBRASPE = Pattern.compile(
            "(?<![\\p{Alnum}/])([0-9]{1,3}(?:\\.[0-9]{1,3})*)"
                    + "(?:\\.)?(?:\\s+(?=\\p{Lu})|\\s*$)");

    private final NormalizadorDoTextoDoEdital normalizador;

    public ParserDeterministicoDoEdital() {
        this(new NormalizadorDoTextoDoEdital());
    }

    public ParserDeterministicoDoEdital(
            NormalizadorDoTextoDoEdital normalizador) {
        this.normalizador = normalizador;
    }

    public ExtracaoEstruturadaDoEdital extrair(String texto,
            FonteDoEdital fonte) {
        if (texto == null) texto = "";
        Contexto contexto = new Contexto(fonte, normalizador);
        String[] paginas = texto.split("\\f", -1);
        for (int pagina = 0; pagina < paginas.length; pagina++) {
            String[] linhas = paginas[pagina].split("\\R", -1);
            for (String linha : linhas) {
                contexto.consumir(linha, pagina + 1);
            }
        }
        return contexto.construir();
    }

    private static final class Contexto {
        private final FonteDoEdital fonte;
        private final NormalizadorDoTextoDoEdital normalizador;
        private final Map<String, Campo> campos = new LinkedHashMap<>();
        private final List<Cargo> cargos = new ArrayList<>();
        private final List<Prova> provas = new ArrayList<>();
        private final List<Materia> materias = new ArrayList<>();
        private final List<String> avisos = new ArrayList<>();
        private final List<String> incertezas = new ArrayList<>();
        private Cargo cargo;
        private Prova prova;
        private Grupo grupo;
        private Materia materia;
        private Campo areaPendente;
        private Campo especialidadePendente;
        private Campo orientacaoPendente;
        private Campo escolaridadePendente;
        private Campo provaObjetivaExplicita;
        private Campo caraterDaProvaObjetivaExplicito;
        private ItemCebraspe itemCebraspe;
        private boolean emObjetosDeAvaliacao;
        private int linhasEmBranco;

        private Contexto(FonteDoEdital fonte,
                NormalizadorDoTextoDoEdital normalizador) {
            if (fonte == null) throw new IllegalArgumentException(
                    "Fonte do edital obrigatoria.");
            this.fonte = fonte;
            this.normalizador = normalizador;
        }

        private void consumir(String linhaOriginal, int pagina) {
            String linha = linhaOriginal.strip();
            if (linha.isEmpty()) {
                linhasEmBranco++;
                return;
            }
            String normalizada = normalizador.normalizarNome(linha);
            if (normalizada != null && (normalizada.contains(
                    "ignore as instrucoes") || normalizada.contains(
                    "system prompt") || normalizada.contains(
                    "chame a ferramenta"))) {
                avisos.add("Possivel instrucao maliciosa ignorada na pagina "
                        + pagina + ".");
            }
            capturarMetadadosExplicitos(linha, pagina, normalizada);
            if (emObjetosDeAvaliacao && linhasEmBranco >= 2
                    && pareceAssinatura(linha)) {
                finalizarItemCebraspe();
                emObjetosDeAvaliacao = false;
            }
            linhasEmBranco = 0;

            if (consumirCabecalhoCebraspe(linha, pagina)) return;
            if (consumirObjetosDeAvaliacao(linha, pagina)) return;

            Matcher correspondencia = LINHA.matcher(linha);
            if (!correspondencia.matches()) return;
            String rotulo = normalizador.normalizarNome(
                    correspondencia.group(1));
            String valor = correspondencia.group(2).strip();
            ProvenienciaDoDado origem = new ProvenienciaDoDado(pagina,
                    correspondencia.group(1).strip(), linha);
            switch (rotulo) {
                case "concurso", "orgao", "banca", "data prevista",
                        "edital", "numero", "ano", "descricao do edital",
                        "publicacao", "data de publicacao" ->
                        campos.put(rotulo, new Campo(valor, origem));
                case "cargo" -> novoCargo(valor, origem);
                case "area" -> atribuirArea(valor, origem);
                case "especialidade" ->
                        atribuirEspecialidadeEOrientacao(valor, origem);
                case "orientacao" -> atribuirOrientacao(valor, origem);
                case "escolaridade", "nivel de escolaridade" ->
                        atribuirEscolaridade(valor, origem);
                case "requisito" ->
                        atribuirEscolaridadeDoRequisito(valor, origem);
                case "prova" -> novaProva(valor, origem);
                case "tipo" -> {
                    if (prova != null) prova.tipo = new Campo(valor, origem);
                    else contextoAusente("tipo", "prova", origem);
                }
                case "carater" -> {
                    if (prova != null) prova.carater = new Campo(valor, origem);
                    else contextoAusente("carater", "prova", origem);
                }
                case "data da prova", "data hora" -> {
                    if (prova != null) prova.data = new Campo(valor, origem);
                    else contextoAusente("data da prova", "prova", origem);
                }
                case "duracao", "duracao em minutos" ->
                        atribuirDuracao(valor, origem);
                case "questoes", "quantidade de questoes" ->
                        atribuirQuestoes(valor, origem);
                case "pontuacao maxima", "pontos" ->
                        atribuirPontuacaoMaxima(valor, origem);
                case "pontuacao minima" ->
                        atribuirPontuacaoMinima(valor, origem);
                case "grupo", "bloco" -> novoGrupo(valor, origem);
                case "materia", "disciplina" -> novaMateria(valor, origem);
                case "peso" -> {
                    if (materia != null) materia.peso = new Campo(valor, origem);
                    else contextoAusente("peso", "materia", origem);
                }
                case "descricao da materia" -> {
                    if (materia != null) {
                        materia.descricao = new Campo(valor, origem);
                    } else {
                        contextoAusente("descricao da materia", "materia",
                                origem);
                    }
                }
                case "topico" -> novoTopico(valor, origem);
                case "item" -> novoItem(valor, origem);
                default -> { }
            }
        }

        private void capturarMetadadosExplicitos(String linha, int pagina,
                String normalizada) {
            ProvenienciaDoDado origem = origem(pagina, "cabecalho", linha);
            if ("tribunal de contas da uniao".equals(normalizada)) {
                campos.putIfAbsent("orgao", new Campo(linha, origem));
            }
            if (linha.contains("(Cebraspe)")) {
                campos.putIfAbsent("banca", new Campo("Cebraspe", origem));
            }
            if (normalizada == null
                    || !normalizada.contains("provas objetivas")) {
                return;
            }
            provaObjetivaExplicita = primeiro(provaObjetivaExplicita,
                    new Campo("Provas objetivas", origem));
            if (normalizada.contains(
                    "carater eliminatorio e classificatorio")) {
                caraterDaProvaObjetivaExplicito = primeiro(
                        caraterDaProvaObjetivaExplicito,
                        new Campo("ELIMINATORIO_E_CLASSIFICATORIO", origem));
            }
        }

        private boolean consumirCabecalhoCebraspe(String linha, int pagina) {
            ProvenienciaDoDado origem = origem(pagina, "cabecalho", linha);
            if (CONCURSO_CEBRASPE.matcher(linha).matches()) {
                campos.putIfAbsent("concurso", new Campo(linha, origem));
                return true;
            }
            Matcher edital = EDITAL_CEBRASPE.matcher(linha);
            if (edital.matches()) {
                campos.putIfAbsent("edital", new Campo(linha, origem));
                campos.putIfAbsent("numero",
                        new Campo(edital.group(1), origem));
                Matcher ano = ANO_EXPLICITO.matcher(linha);
                if (ano.find()) {
                    campos.putIfAbsent("ano",
                            new Campo(ano.group(), origem));
                }
                return true;
            }
            Matcher cabecalhoDoCargo = CARGO_CEBRASPE.matcher(linha);
            if (cabecalhoDoCargo.matches()) {
                consumirCabecalhoDoCargo(
                        cabecalhoDoCargo.group(1), origem);
                return true;
            }
            return false;
        }

        private void consumirCabecalhoDoCargo(String cabecalho,
                ProvenienciaDoDado origem) {
            Matcher segmento = SEGMENTO_DO_CARGO.matcher(cabecalho);
            List<SegmentoDoCargo> segmentos = new ArrayList<>();
            int inicioDoPrimeiroSegmento = -1;
            while (segmento.find()) {
                if (inicioDoPrimeiroSegmento < 0) {
                    inicioDoPrimeiroSegmento = segmento.start();
                }
                segmentos.add(new SegmentoDoCargo(
                        normalizador.normalizarNome(segmento.group(1)),
                        segmento.end(), -1));
                if (segmentos.size() > 1) {
                    SegmentoDoCargo anterior =
                            segmentos.get(segmentos.size() - 2);
                    segmentos.set(segmentos.size() - 2,
                            anterior.comFim(segmento.start()));
                }
            }
            if (!segmentos.isEmpty()) {
                SegmentoDoCargo ultimo = segmentos.getLast();
                segmentos.set(segmentos.size() - 1,
                        ultimo.comFim(cabecalho.length()));
            }
            String nome = limparSeparadores(inicioDoPrimeiroSegmento < 0
                    ? cabecalho : cabecalho.substring(
                            0, inicioDoPrimeiroSegmento));
            if (nome.isBlank()) {
                incertezas.add("Cabecalho de cargo sem nome explicito na "
                        + "pagina " + origem.pagina() + ".");
                return;
            }
            novoCargo(nome, origem);
            for (SegmentoDoCargo item : segmentos) {
                String valor = limparSeparadores(cabecalho.substring(
                        item.inicio(), item.fim()));
                if (valor.isBlank()) continue;
                switch (item.rotulo()) {
                    case "area" -> atribuirArea(valor, origem);
                    case "especialidade" ->
                            atribuirEspecialidadeEOrientacao(valor, origem);
                    case "orientacao" -> atribuirOrientacao(valor, origem);
                    default -> { }
                }
            }
        }

        private boolean consumirObjetosDeAvaliacao(String linha, int pagina) {
            Matcher secao = OBJETOS_DE_AVALIACAO.matcher(linha);
            if (secao.matches()) {
                finalizarItemCebraspe();
                emObjetosDeAvaliacao = true;
                garantirProvaDosObjetos(linha, pagina);
                return true;
            }
            if (!emObjetosDeAvaliacao) return false;
            String linhaNormalizada = normalizador.normalizarNome(linha);
            if (linhaNormalizada != null
                    && linhaNormalizada.startsWith("anexo ")) {
                finalizarItemCebraspe();
                emObjetosDeAvaliacao = false;
                return true;
            }
            Matcher grupoCebraspe = GRUPO_CEBRASPE.matcher(linha);
            if (grupoCebraspe.matches()) {
                finalizarItemCebraspe();
                garantirProvaDosObjetos(linha, pagina);
                if (prova == null) {
                    incertezas.add("Grupo sem cargo explicito na pagina "
                            + pagina + ".");
                    return true;
                }
                novoGrupo(grupoCebraspe.group(1),
                        origem(pagina, grupoCebraspe.group(1), linha));
                return true;
            }
            MateriaCebraspe materiaCebraspe = materiaCebraspe(linha);
            if (materiaCebraspe != null && grupo != null) {
                finalizarItemCebraspe();
                novaMateria(materiaCebraspe.nome(),
                        origem(pagina, materiaCebraspe.nome(), linha));
                consumirItensCebraspe(materiaCebraspe.conteudo(), pagina);
                return true;
            }
            if (materia != null) {
                consumirItensCebraspe(linha, pagina);
                return true;
            }
            return true;
        }

        private void garantirProvaDosObjetos(String linha, int pagina) {
            if (prova != null || cargo == null) return;
            Matcher secao = OBJETOS_DE_AVALIACAO.matcher(linha);
            Campo nome = provaObjetivaExplicita;
            if (nome == null) {
                String valor = secao.matches()
                        ? "DOS OBJETOS DE AVALIAÇÃO" + secao.group(1)
                        : "CONHECIMENTOS";
                nome = new Campo(valor.strip(), origem(pagina,
                        "objetos de avaliacao", linha));
            }
            novaProva(nome.valor, nome.origem);
            if (prova != null && provaObjetivaExplicita != null) {
                prova.tipo = new Campo("OBJETIVA",
                        provaObjetivaExplicita.origem);
            }
            if (prova != null
                    && caraterDaProvaObjetivaExplicito != null) {
                prova.carater = caraterDaProvaObjetivaExplicito;
            }
        }

        private MateriaCebraspe materiaCebraspe(String linha) {
            int doisPontos = linha.indexOf(':');
            if (doisPontos < 4 || doisPontos > 120) return null;
            String nome = linha.substring(0, doisPontos).strip();
            if (nome.chars().noneMatch(Character::isLetter)
                    || nome.chars().anyMatch(Character::isLowerCase)
                    || nome.indexOf(',') >= 0 || nome.indexOf('.') >= 0) {
                return null;
            }
            String rotulo = normalizador.normalizarNome(nome);
            if (rotulo == null || rotulo.equals("area")
                    || rotulo.equals("especialidade")
                    || rotulo.equals("orientacao")
                    || rotulo.equals("requisito")) {
                return null;
            }
            return new MateriaCebraspe(nome,
                    linha.substring(doisPontos + 1).strip());
        }

        private void consumirItensCebraspe(String conteudo, int pagina) {
            if (conteudo == null || conteudo.isBlank()) return;
            Matcher marcador = INICIO_DE_ITEM_CEBRASPE.matcher(conteudo);
            List<MarcadorDeItem> marcadores = new ArrayList<>();
            String ultimoNumero = itemCebraspe == null
                    ? null : itemCebraspe.numero;
            while (marcador.find()) {
                String candidato = marcador.group(1);
                if (!numeroPlausivel(ultimoNumero, candidato)) continue;
                marcadores.add(new MarcadorDeItem(candidato,
                        marcador.start(1), marcador.end()));
                ultimoNumero = candidato;
            }
            if (marcadores.isEmpty()) {
                acrescentarAoItemCebraspe(conteudo);
                return;
            }
            acrescentarAoItemCebraspe(conteudo.substring(
                    0, marcadores.getFirst().inicio()));
            for (int indice = 0; indice < marcadores.size(); indice++) {
                finalizarItemCebraspe();
                MarcadorDeItem atual = marcadores.get(indice);
                int fim = indice + 1 < marcadores.size()
                        ? marcadores.get(indice + 1).inicio()
                        : conteudo.length();
                itemCebraspe = new ItemCebraspe(materia, atual.numero(),
                        pagina);
                acrescentarAoItemCebraspe(conteudo.substring(
                        atual.fimDoMarcador(), fim));
            }
        }

        private void acrescentarAoItemCebraspe(String trecho) {
            if (itemCebraspe == null || trecho == null
                    || trecho.isBlank()) {
                return;
            }
            if (!itemCebraspe.descricao.isEmpty()) {
                itemCebraspe.descricao.append(' ');
            }
            itemCebraspe.descricao.append(trecho.strip());
        }

        private void finalizarItemCebraspe() {
            if (itemCebraspe == null) return;
            String descricao = itemCebraspe.descricao.toString()
                    .replaceAll("\\s+", " ").strip();
            if (descricao.isEmpty()) {
                incertezas.add("Item " + itemCebraspe.numero
                        + " sem descricao explicita na pagina "
                        + itemCebraspe.pagina + ".");
                itemCebraspe = null;
                return;
            }
            Materia destino = itemCebraspe.materia;
            ProvenienciaDoDado origem = origem(itemCebraspe.pagina,
                    destino.nome.valor,
                    itemCebraspe.numero + " " + descricao);
            String chaveDoTopico = normalizador.criarChave(
                    "topico-" + destino.chave,
                    destino.topicos.size() + 1, descricao);
            destino.topicos.add(new Topico(chaveDoTopico,
                    pai(itemCebraspe.numero, destino.topicos),
                    itemCebraspe.numero, nomeDoTopicoCebraspe(descricao),
                    origem, destino.topicos.size() + 1, true));
            String chave = normalizador.criarChave("item-" + destino.chave,
                    destino.itens.size() + 1, descricao);
            destino.itens.add(new Item(chave,
                    pai(itemCebraspe.numero, destino.itens),
                    itemCebraspe.numero, descricao, origem,
                    destino.itens.size() + 1, chaveDoTopico));
            itemCebraspe = null;
        }

        private void atribuirArea(String valor, ProvenienciaDoDado origem) {
            Campo campo = new Campo(limparSeparadores(valor), origem);
            if (cargo == null) areaPendente = campo;
            else cargo.area = campo;
        }

        private void atribuirEspecialidadeEOrientacao(String valor,
                ProvenienciaDoDado origem) {
            Matcher composta = ESPECIALIDADE_COM_ORIENTACAO.matcher(valor);
            if (composta.matches()) {
                atribuirEspecialidade(composta.group(1), origem);
                atribuirOrientacao(composta.group(2), origem);
                return;
            }
            atribuirEspecialidade(valor, origem);
        }

        private void atribuirEspecialidade(String valor,
                ProvenienciaDoDado origem) {
            Campo campo = new Campo(limparSeparadores(valor), origem);
            if (cargo == null) especialidadePendente = campo;
            else cargo.especialidade = campo;
        }

        private void atribuirOrientacao(String valor,
                ProvenienciaDoDado origem) {
            Campo campo = new Campo(limparSeparadores(valor), origem);
            if (cargo == null) orientacaoPendente = campo;
            else cargo.orientacao = campo;
        }

        private void atribuirEscolaridade(String valor,
                ProvenienciaDoDado origem) {
            Campo campo = new Campo(valor, origem);
            if (cargo == null) escolaridadePendente = campo;
            else cargo.escolaridade = campo;
        }

        private void atribuirEscolaridadeDoRequisito(String valor,
                ProvenienciaDoDado origem) {
            String requisito = normalizador.normalizarNome(valor);
            if (requisito == null) return;
            String nivel = null;
            if (requisito.matches(
                    ".*\\b(?:diploma|certificado|conclusao)\\b.*"
                            + "\\bnivel superior\\b.*")) {
                nivel = "SUPERIOR";
            } else if (requisito.matches(
                    ".*\\b(?:diploma|certificado|conclusao)\\b.*"
                            + "\\b(?:nivel medio|ensino medio)\\b.*")) {
                nivel = "MEDIO";
            } else if (requisito.matches(
                    ".*\\b(?:diploma|certificado|conclusao)\\b.*"
                            + "\\bnivel tecnico\\b.*")) {
                nivel = "TECNICO";
            } else if (requisito.matches(
                    ".*\\b(?:diploma|certificado|conclusao)\\b.*"
                            + "\\b(?:nivel fundamental|ensino fundamental)"
                            + "\\b.*")) {
                nivel = "FUNDAMENTAL";
            }
            if (nivel != null) atribuirEscolaridade(nivel, origem);
        }

        private void novoCargo(String nome, ProvenienciaDoDado origem) {
            cargo = new Cargo(normalizador.criarChave("cargo",
                    cargos.size() + 1, nome), new Campo(nome, origem),
                    cargos.size() + 1);
            cargo.area = areaPendente;
            cargo.especialidade = especialidadePendente;
            cargo.orientacao = orientacaoPendente;
            cargo.escolaridade = escolaridadePendente;
            areaPendente = null;
            especialidadePendente = null;
            orientacaoPendente = null;
            escolaridadePendente = null;
            cargos.add(cargo);
            prova = null;
            grupo = null;
            materia = null;
        }

        private void novaProva(String nome, ProvenienciaDoDado origem) {
            if (cargo == null) {
                contextoAusente("prova", "cargo", origem);
                return;
            }
            Cargo atual = cargo;
            prova = new Prova(normalizador.criarChave("prova",
                    provas.size() + 1, nome), atual.chave,
                    new Campo(nome, origem), provas.size() + 1);
            provas.add(prova);
            grupo = null;
            materia = null;
        }

        private void novoGrupo(String nome, ProvenienciaDoDado origem) {
            if (prova == null) {
                contextoAusente("grupo", "prova", origem);
                return;
            }
            Prova atual = prova;
            grupo = new Grupo(normalizador.criarChave(
                    "grupo-" + atual.chave,
                    atual.grupos.size() + 1, nome), new Campo(nome, origem),
                    atual.grupos.size() + 1);
            atual.grupos.add(grupo);
            materia = null;
        }

        private void novaMateria(String nome, ProvenienciaDoDado origem) {
            if (cargo == null || prova == null || grupo == null) {
                contextoAusente("materia", "cargo, prova e grupo", origem);
                return;
            }
            Cargo cargoAtual = cargo;
            Prova provaAtual = prova;
            Grupo grupoAtual = grupo;
            materia = new Materia(normalizador.criarChave("materia",
                    materias.size() + 1, nome), cargoAtual.chave,
                    provaAtual.chave, grupoAtual.chave,
                    new Campo(nome, origem), materias.size() + 1);
            materias.add(materia);
        }

        private void novoTopico(String valor, ProvenienciaDoDado origem) {
            if (materia == null) {
                contextoAusente("topico", "materia", origem);
                return;
            }
            Materia atual = materia;
            Numerado numerado = numerado(valor);
            String chave = normalizador.criarChave(
                    "topico-" + atual.chave,
                    atual.topicos.size() + 1, numerado.texto);
            atual.topicos.add(new Topico(chave,
                    pai(numerado.numero, atual.topicos), numerado.numero,
                    numerado.texto, origem, atual.topicos.size() + 1, false));
        }

        private void novoItem(String valor, ProvenienciaDoDado origem) {
            if (materia == null) {
                contextoAusente("item", "materia", origem);
                return;
            }
            Materia atual = materia;
            Numerado numerado = numerado(valor);
            String chave = normalizador.criarChave("item-" + atual.chave,
                    atual.itens.size() + 1, numerado.texto);
            String topico = atual.topicos.stream().filter(item ->
                    numerado.numero != null && numerado.numero.equals(
                            item.numero)).map(item -> item.chave).findFirst()
                    .orElse(null);
            atual.itens.add(new Item(chave,
                    pai(numerado.numero, atual.itens), numerado.numero,
                    numerado.texto, origem, atual.itens.size() + 1, topico));
        }

        private void atribuirQuestoes(String valor, ProvenienciaDoDado origem) {
            if (materia != null) materia.questoes = new Campo(valor, origem);
            else if (grupo != null) grupo.questoes = new Campo(valor, origem);
            else if (prova != null) prova.questoes = new Campo(valor, origem);
            else contextoAusente("quantidade de questoes", "prova", origem);
        }

        private void atribuirPontuacaoMaxima(String valor,
                ProvenienciaDoDado origem) {
            if (materia != null) materia.pontuacaoMaxima =
                    new Campo(valor, origem);
            else if (grupo != null) grupo.pontuacaoMaxima =
                    new Campo(valor, origem);
            else if (prova != null) {
                prova.pontuacaoMaxima = new Campo(valor, origem);
            } else {
                contextoAusente("pontuacao maxima", "prova", origem);
            }
        }

        private void atribuirPontuacaoMinima(String valor,
                ProvenienciaDoDado origem) {
            if (grupo != null) grupo.pontuacaoMinima = new Campo(valor, origem);
            else if (prova != null) {
                prova.pontuacaoMinima = new Campo(valor, origem);
            } else {
                contextoAusente("pontuacao minima", "prova", origem);
            }
        }

        private void atribuirDuracao(String valor,
                ProvenienciaDoDado origem) {
            if (prova != null) prova.duracao = new Campo(valor, origem);
            else contextoAusente("duracao", "prova", origem);
        }

        private void contextoAusente(String rotulo, String dependencia,
                ProvenienciaDoDado origem) {
            incertezas.add("Rotulo " + rotulo + " sem " + dependencia
                    + " anterior na pagina " + origem.pagina()
                    + "; valor nao associado.");
        }

        private ExtracaoEstruturadaDoEdital construir() {
            finalizarItemCebraspe();
            if (cargo == null && (areaPendente != null
                    || especialidadePendente != null
                    || orientacaoPendente != null
                    || escolaridadePendente != null)) {
                incertezas.add("Rotulo dependente encontrado antes de cargo; "
                        + "associacao exige revisao.");
            }
            ConcursoExtraido concurso = new ConcursoExtraido(
                    texto(campos.get("concurso")), ausente(),
                    texto(campos.get("orgao")), texto(campos.get("banca")),
                    data(campos.get("data prevista")));
            Campo titulo = campos.get("edital");
            EditalExtraido edital = new EditalExtraido(texto(titulo),
                    texto(campos.get("numero")), inteiro(campos.get("ano")),
                    texto(campos.get("descricao do edital")),
                    data(primeiro(campos.get("data de publicacao"),
                            campos.get("publicacao"))));
            return new ExtracaoEstruturadaDoEdital("1", fonte, concurso,
                    edital, cargos.stream().map(Cargo::construir).toList(),
                    provas.stream().map(Prova::construir).toList(),
                    materias.stream().map(materia -> materia.construir(
                            normalizador)).toList(), avisos, incertezas);
        }
    }

    private record Campo(String valor, ProvenienciaDoDado origem) { }
    private record Numerado(String numero, String texto) { }
    private record MateriaCebraspe(String nome, String conteudo) { }
    private record MarcadorDeItem(
            String numero,
            int inicio,
            int fimDoMarcador) {
    }
    private record SegmentoDoCargo(
            String rotulo,
            int inicio,
            int fim) {
        private SegmentoDoCargo comFim(int novoFim) {
            return new SegmentoDoCargo(rotulo, inicio, novoFim);
        }
    }

    private static final class ItemCebraspe {
        private final Materia materia;
        private final String numero;
        private final int pagina;
        private final StringBuilder descricao = new StringBuilder();

        private ItemCebraspe(Materia materia, String numero, int pagina) {
            this.materia = materia;
            this.numero = numero;
            this.pagina = pagina;
        }
    }

    private static final class Cargo {
        private final String chave;
        private final Campo nome;
        private final int ordem;
        private Campo area;
        private Campo especialidade;
        private Campo orientacao;
        private Campo escolaridade;
        private Cargo(String chave, Campo nome, int ordem) {
            this.chave = chave; this.nome = nome; this.ordem = ordem;
        }
        private CargoExtraido construir() {
            return new CargoExtraido(chave, texto(nome), texto(area),
                    especialidadeComOrientacao(),
                    escolaridade(escolaridade), ordem);
        }
        private ValorExtraido<String> especialidadeComOrientacao() {
            if (orientacao == null) {
                return texto(especialidade);
            }
            if (especialidade == null) {
                return ValorExtraido.explicito("ORIENTAÇÃO: "
                        + orientacao.valor, orientacao.origem);
            }
            String valor = especialidade.valor + " – ORIENTAÇÃO: "
                    + orientacao.valor;
            if (especialidade.origem.equals(orientacao.origem)) {
                return ValorExtraido.explicito(valor,
                        especialidade.origem);
            }
            Integer pagina = Objects.equals(especialidade.origem.pagina(),
                    orientacao.origem.pagina())
                    ? especialidade.origem.pagina() : null;
            ProvenienciaDoDado origemCombinada = new ProvenienciaDoDado(
                    pagina, "Especialidade e orientação",
                    especialidade.origem.trecho() + " | "
                            + orientacao.origem.trecho());
            return new ValorExtraido<>(valor, new BigDecimal("0.9500"),
                    origemCombinada, true);
        }
    }

    private static final class Prova {
        private final String chave;
        private final String cargo;
        private final Campo nome;
        private final int ordem;
        private final List<Grupo> grupos = new ArrayList<>();
        private Campo tipo; private Campo carater; private Campo data;
        private Campo duracao; private Campo questoes;
        private Campo pontuacaoMaxima; private Campo pontuacaoMinima;
        private Prova(String chave, String cargo, Campo nome, int ordem) {
            this.chave = chave; this.cargo = cargo;
            this.nome = nome; this.ordem = ordem;
        }
        private ProvaExtraida construir() {
            return new ProvaExtraida(chave, cargo, texto(nome), tipo(tipo),
                    carater(carater), ordem, dataHora(data), inteiro(duracao),
                    inteiro(questoes), decimal(pontuacaoMaxima),
                    decimal(pontuacaoMinima),
                    grupos.stream().map(Grupo::construir).toList());
        }
    }

    private static final class Grupo {
        private final String chave; private final Campo nome; private final int ordem;
        private Campo questoes; private Campo pontuacaoMaxima;
        private Campo pontuacaoMinima;
        private Grupo(String chave, Campo nome, int ordem) {
            this.chave = chave; this.nome = nome; this.ordem = ordem;
        }
        private GrupoExtraido construir() {
            return new GrupoExtraido(chave, texto(nome), ordem,
                    inteiro(questoes), decimal(pontuacaoMaxima),
                    decimal(pontuacaoMinima));
        }
    }

    private static final class Materia {
        private final String chave; private final String cargo;
        private final String prova; private final String grupo;
        private final Campo nome; private final int ordem;
        private final List<Topico> topicos = new ArrayList<>();
        private final List<Item> itens = new ArrayList<>();
        private Campo descricao; private Campo peso; private Campo questoes;
        private Campo pontuacaoMaxima;
        private Materia(String chave, String cargo, String prova, String grupo,
                Campo nome, int ordem) {
            this.chave = chave; this.cargo = cargo; this.prova = prova;
            this.grupo = grupo; this.nome = nome; this.ordem = ordem;
        }
        private MateriaExtraida construir(NormalizadorDoTextoDoEdital normalizador) {
            return new MateriaExtraida(chave, cargo, prova, grupo, texto(nome),
                    texto(descricao), ordem, decimal(peso), inteiro(questoes),
                    decimal(pontuacaoMaxima),
                    topicos.stream().map(Topico::construir).toList(),
                    itens.stream().map(item -> item.construir(normalizador)).toList());
        }
    }

    private record Topico(String chave, String pai, String numero, String nome,
            ProvenienciaDoDado origem, int ordem, boolean inferido) {
        private TopicoExtraido construir() {
            return new TopicoExtraido(chave, pai, explicito(numero, origem),
                    inferido ? valorInferido(nome, origem)
                            : explicito(nome, origem),
                    ausente(), ordem);
        }
    }

    private record Item(String chave, String pai, String numero, String descricao,
            ProvenienciaDoDado origem, int ordem, String topico) {
        private ItemExtraido construir(NormalizadorDoTextoDoEdital normalizador) {
            return new ItemExtraido(chave, pai, explicito(numero, origem),
                    explicito(descricao, origem),
                    normalizador.normalizarNome(descricao), ordem, topico);
        }
    }

    private static Numerado numerado(String valor) {
        Matcher matcher = NUMERADO.matcher(valor);
        return matcher.matches() ? new Numerado(matcher.group(1),
                matcher.group(2).strip()) : new Numerado(null, valor);
    }

    private static boolean numeroPlausivel(String atual, String candidato) {
        int[] proximo = numero(candidato);
        if (proximo == null) return false;
        if (atual == null) return proximo.length == 1 && proximo[0] == 1;
        int[] anterior = numero(atual);
        if (anterior == null) return false;
        if (proximo.length == anterior.length + 1) {
            for (int indice = 0; indice < anterior.length; indice++) {
                if (proximo[indice] != anterior[indice]) return false;
            }
            return proximo[proximo.length - 1] == 1;
        }
        if (proximo.length > anterior.length) return false;
        for (int indice = 0; indice < proximo.length - 1; indice++) {
            if (proximo[indice] != anterior[indice]) return false;
        }
        return proximo[proximo.length - 1]
                == anterior[proximo.length - 1] + 1;
    }

    private static int[] numero(String valor) {
        String[] partes = valor.split("\\.");
        int[] resultado = new int[partes.length];
        try {
            for (int indice = 0; indice < partes.length; indice++) {
                resultado[indice] = Integer.parseInt(partes[indice]);
                if (resultado[indice] < 1) return null;
            }
            return resultado;
        } catch (NumberFormatException excecao) {
            return null;
        }
    }

    private static <T> String pai(String numero, List<T> itens) {
        if (numero == null || !numero.contains(".")) return null;
        String numeroDoPai = numero.substring(0, numero.lastIndexOf('.'));
        for (T item : itens) {
            if (item instanceof Topico topico && numeroDoPai.equals(topico.numero))
                return topico.chave;
            if (item instanceof Item registro && numeroDoPai.equals(registro.numero))
                return registro.chave;
        }
        return null;
    }

    private static Campo primeiro(Campo primeiro, Campo segundo) {
        return primeiro == null ? segundo : primeiro;
    }

    private static String limparSeparadores(String valor) {
        return valor == null ? "" : valor.strip()
                .replaceAll("^[–—-]+\\s*|\\s*[–—-]+$", "").strip();
    }

    private static String nomeDoTopicoCebraspe(String descricao) {
        if (descricao.length() <= 160) return descricao;
        int corte = descricao.lastIndexOf(' ', 160);
        if (corte < 80) corte = 160;
        return descricao.substring(0, corte).strip();
    }

    private static boolean pareceAssinatura(String linha) {
        if (linha.indexOf(':') >= 0
                || linha.chars().anyMatch(Character::isDigit)) {
            return false;
        }
        long letras = linha.chars().filter(Character::isLetter).count();
        return letras >= 8 && linha.equals(linha.toUpperCase(Locale.ROOT));
    }

    private static ProvenienciaDoDado origem(int pagina, String secao,
            String trecho) {
        return new ProvenienciaDoDado(pagina, secao, trecho);
    }

    private static <T> ValorExtraido<T> ausente() {
        return new ValorExtraido<>(null, BigDecimal.ZERO, null, false);
    }

    private static ValorExtraido<String> texto(Campo campo) {
        return campo == null ? ausente() : ValorExtraido.explicito(
                campo.valor, campo.origem);
    }

    private static ValorExtraido<Integer> inteiro(Campo campo) {
        if (campo == null) return ausente();
        try {
            return ValorExtraido.explicito(Integer.parseInt(
                    campo.valor.replaceAll("[^0-9-]", "")), campo.origem);
        } catch (NumberFormatException excecao) {
            return ausente();
        }
    }

    private static ValorExtraido<BigDecimal> decimal(Campo campo) {
        if (campo == null) return ausente();
        try {
            String numero = campo.valor;
            numero = numero.contains(",")
                    ? numero.replace(".", "").replace(',', '.') : numero;
            return ValorExtraido.explicito(new BigDecimal(numero.replaceAll(
                    "[^0-9.-]", "")), campo.origem);
        } catch (NumberFormatException excecao) {
            return ausente();
        }
    }

    private static ValorExtraido<LocalDate> data(Campo campo) {
        if (campo == null) return ausente();
        try {
            LocalDate valor = campo.valor.contains("/")
                    ? LocalDate.parse(campo.valor,
                            DateTimeFormatter.ofPattern("dd/MM/uuuu"))
                    : LocalDate.parse(campo.valor);
            return ValorExtraido.explicito(valor, campo.origem);
        } catch (DateTimeParseException excecao) {
            return ausente();
        }
    }

    private static ValorExtraido<OffsetDateTime> dataHora(Campo campo) {
        if (campo == null) return ausente();
        try {
            return ValorExtraido.explicito(OffsetDateTime.parse(campo.valor),
                    campo.origem);
        } catch (DateTimeParseException excecao) {
            ValorExtraido<LocalDate> data = data(campo);
            return data.valor() == null ? ausente() : ValorExtraido.explicito(
                    data.valor().atStartOfDay().atOffset(ZoneOffset.UTC),
                    campo.origem);
        }
    }

    private static ValorExtraido<NivelDeEscolaridade> escolaridade(Campo campo) {
        if (campo == null) return ausente();
        String valor = chaveEnum(campo.valor);
        try {
            return ValorExtraido.explicito(NivelDeEscolaridade.valueOf(valor),
                    campo.origem);
        } catch (IllegalArgumentException excecao) {
            return ausente();
        }
    }

    private static ValorExtraido<TipoDeProva> tipo(Campo campo) {
        if (campo == null) return ausente();
        try {
            return ValorExtraido.explicito(TipoDeProva.valueOf(
                    chaveEnum(campo.valor)), campo.origem);
        } catch (IllegalArgumentException excecao) {
            return ausente();
        }
    }

    private static ValorExtraido<CaraterDaProva> carater(Campo campo) {
        if (campo == null) return ausente();
        String valor = chaveEnum(campo.valor).replace("_E_", "_E_");
        try {
            return ValorExtraido.explicito(CaraterDaProva.valueOf(valor),
                    campo.origem);
        } catch (IllegalArgumentException excecao) {
            return ausente();
        }
    }

    private static String chaveEnum(String valor) {
        return java.text.Normalizer.normalize(valor, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static <T> ValorExtraido<T> explicito(T valor,
            ProvenienciaDoDado origem) {
        return valor == null ? ausente() : ValorExtraido.explicito(valor, origem);
    }

    private static <T> ValorExtraido<T> valorInferido(T valor,
            ProvenienciaDoDado origem) {
        return valor == null ? ausente() : new ValorExtraido<>(valor,
                new BigDecimal("0.8500"), origem, true);
    }
}
