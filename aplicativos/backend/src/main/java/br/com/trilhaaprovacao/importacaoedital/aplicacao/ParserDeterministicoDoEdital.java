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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parser conservador para linhas rotuladas. Campo nao reconhecido fica ausente. */
public class ParserDeterministicoDoEdital {
    public static final String VERSAO = "deterministico-1";
    private static final Pattern LINHA = Pattern.compile(
            "^\\s*([\\p{L}][\\p{L} \\-/]*?)\\s*:\\s*(.+?)\\s*$");
    private static final Pattern NUMERADO = Pattern.compile(
            "^([0-9]+(?:\\.[0-9]+)*)\\s*(?:[-–—|:]\\s*)?(.+)$");

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

        private Contexto(FonteDoEdital fonte,
                NormalizadorDoTextoDoEdital normalizador) {
            if (fonte == null) throw new IllegalArgumentException(
                    "Fonte do edital obrigatoria.");
            this.fonte = fonte;
            this.normalizador = normalizador;
        }

        private void consumir(String linhaOriginal, int pagina) {
            String linha = linhaOriginal.strip();
            if (linha.isEmpty()) return;
            String normalizada = normalizador.normalizarNome(linha);
            if (normalizada != null && (normalizada.contains(
                    "ignore as instrucoes") || normalizada.contains(
                    "system prompt") || normalizada.contains(
                    "chame a ferramenta"))) {
                avisos.add("Possivel instrucao maliciosa ignorada na pagina "
                        + pagina + ".");
            }
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
                case "area" -> exigirCargo().area = new Campo(valor, origem);
                case "especialidade" -> exigirCargo().especialidade =
                        new Campo(valor, origem);
                case "escolaridade", "nivel de escolaridade" ->
                        exigirCargo().escolaridade = new Campo(valor, origem);
                case "prova" -> novaProva(valor, origem);
                case "tipo" -> exigirProva().tipo = new Campo(valor, origem);
                case "carater" -> exigirProva().carater =
                        new Campo(valor, origem);
                case "data da prova", "data hora" -> exigirProva().data =
                        new Campo(valor, origem);
                case "duracao", "duracao em minutos" ->
                        exigirProva().duracao = new Campo(valor, origem);
                case "questoes", "quantidade de questoes" ->
                        atribuirQuestoes(valor, origem);
                case "pontuacao maxima", "pontos" ->
                        atribuirPontuacaoMaxima(valor, origem);
                case "pontuacao minima" ->
                        atribuirPontuacaoMinima(valor, origem);
                case "grupo", "bloco" -> novoGrupo(valor, origem);
                case "materia", "disciplina" -> novaMateria(valor, origem);
                case "peso" -> exigirMateria().peso = new Campo(valor, origem);
                case "descricao da materia" -> exigirMateria().descricao =
                        new Campo(valor, origem);
                case "topico" -> novoTopico(valor, origem);
                case "item" -> novoItem(valor, origem);
                default -> { }
            }
        }

        private void novoCargo(String nome, ProvenienciaDoDado origem) {
            cargo = new Cargo(normalizador.criarChave("cargo",
                    cargos.size() + 1, nome), new Campo(nome, origem),
                    cargos.size() + 1);
            cargos.add(cargo);
            prova = null;
            grupo = null;
            materia = null;
        }

        private void novaProva(String nome, ProvenienciaDoDado origem) {
            Cargo atual = exigirCargo();
            prova = new Prova(normalizador.criarChave("prova",
                    provas.size() + 1, nome), atual.chave,
                    new Campo(nome, origem), provas.size() + 1);
            provas.add(prova);
            grupo = null;
            materia = null;
        }

        private void novoGrupo(String nome, ProvenienciaDoDado origem) {
            Prova atual = exigirProva();
            grupo = new Grupo(normalizador.criarChave(
                    "grupo-" + atual.chave,
                    atual.grupos.size() + 1, nome), new Campo(nome, origem),
                    atual.grupos.size() + 1);
            atual.grupos.add(grupo);
            materia = null;
        }

        private void novaMateria(String nome, ProvenienciaDoDado origem) {
            Cargo cargoAtual = exigirCargo();
            Prova provaAtual = exigirProva();
            Grupo grupoAtual = exigirGrupo();
            materia = new Materia(normalizador.criarChave("materia",
                    materias.size() + 1, nome), cargoAtual.chave,
                    provaAtual.chave, grupoAtual.chave,
                    new Campo(nome, origem), materias.size() + 1);
            materias.add(materia);
        }

        private void novoTopico(String valor, ProvenienciaDoDado origem) {
            Materia atual = exigirMateria();
            Numerado numerado = numerado(valor);
            String chave = normalizador.criarChave(
                    "topico-" + atual.chave,
                    atual.topicos.size() + 1, numerado.texto);
            atual.topicos.add(new Topico(chave,
                    pai(numerado.numero, atual.topicos), numerado.numero,
                    numerado.texto, origem, atual.topicos.size() + 1));
        }

        private void novoItem(String valor, ProvenienciaDoDado origem) {
            Materia atual = exigirMateria();
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
            else exigirProva().questoes = new Campo(valor, origem);
        }

        private void atribuirPontuacaoMaxima(String valor,
                ProvenienciaDoDado origem) {
            if (materia != null) materia.pontuacaoMaxima =
                    new Campo(valor, origem);
            else if (grupo != null) grupo.pontuacaoMaxima =
                    new Campo(valor, origem);
            else exigirProva().pontuacaoMaxima = new Campo(valor, origem);
        }

        private void atribuirPontuacaoMinima(String valor,
                ProvenienciaDoDado origem) {
            if (grupo != null) grupo.pontuacaoMinima = new Campo(valor, origem);
            else exigirProva().pontuacaoMinima = new Campo(valor, origem);
        }

        private Cargo exigirCargo() {
            if (cargo == null) throw estrutura("CARGO_AUSENTE",
                    "Campo depende de um cargo anterior.");
            return cargo;
        }

        private Prova exigirProva() {
            if (prova == null) throw estrutura("PROVA_AUSENTE",
                    "Campo depende de uma prova anterior.");
            return prova;
        }

        private Grupo exigirGrupo() {
            if (grupo == null) throw estrutura("GRUPO_AUSENTE",
                    "Materia depende de um grupo anterior.");
            return grupo;
        }

        private Materia exigirMateria() {
            if (materia == null) throw estrutura("MATERIA_AUSENTE",
                    "Campo depende de uma materia anterior.");
            return materia;
        }

        private FalhaNaExtracaoDoEdital estrutura(String codigo,
                String mensagem) {
            return new FalhaNaExtracaoDoEdital(codigo, mensagem);
        }

        private ExtracaoEstruturadaDoEdital construir() {
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

    private static final class Cargo {
        private final String chave;
        private final Campo nome;
        private final int ordem;
        private Campo area;
        private Campo especialidade;
        private Campo escolaridade;
        private Cargo(String chave, Campo nome, int ordem) {
            this.chave = chave; this.nome = nome; this.ordem = ordem;
        }
        private CargoExtraido construir() {
            return new CargoExtraido(chave, texto(nome), texto(area),
                    texto(especialidade), escolaridade(escolaridade), ordem);
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
            ProvenienciaDoDado origem, int ordem) {
        private TopicoExtraido construir() {
            return new TopicoExtraido(chave, pai, explicito(numero, origem),
                    explicito(nome, origem), ausente(), ordem);
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
}
