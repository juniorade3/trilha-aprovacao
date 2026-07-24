package br.com.trilhaaprovacao.automacao.aplicacao;

import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeOperacoesAssistidas.OperacaoPreparada;
import br.com.trilhaaprovacao.automacao.infraestrutura.ContextoDaChamadaMcp;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.concursos.aplicacao.ServicoDaEstruturaDeConcursos;
import br.com.trilhaaprovacao.concursos.dominio.CaraterDaProva;
import br.com.trilhaaprovacao.concursos.dominio.NivelDeEscolaridade;
import br.com.trilhaaprovacao.concursos.dominio.SituacaoDoConcurso;
import br.com.trilhaaprovacao.concursos.dominio.TipoDeProva;
import br.com.trilhaaprovacao.conteudoprogramatico.aplicacao.ServicoDeConteudoProgramatico;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeMaterias;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeTopicos;
import br.com.trilhaaprovacao.conteudos.dominio.Materia;
import br.com.trilhaaprovacao.conteudos.dominio.TopicoDaMateria;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Importacao assistida: a leitura da fonte ocorre fora deste servico e a escrita
 * somente ocorre depois da confirmacao confiavel da operacao. */
@Service
public class ServicoDeCadastroAssistidoDeConcursos {
    private static final TypeReference<Map<String, Object>> MAPA =
            new TypeReference<>() { };
    private final ServicoDeOperacoesAssistidas operacoes;
    private final ServicoDaEstruturaDeConcursos estrutura;
    private final ServicoDeMaterias materias;
    private final ServicoDeTopicos topicos;
    private final ServicoDeConteudoProgramatico conteudo;
    private final JdbcTemplate banco;
    private final ObjectMapper mapeador;

    public ServicoDeCadastroAssistidoDeConcursos(
            ServicoDeOperacoesAssistidas operacoes,
            ServicoDaEstruturaDeConcursos estrutura,
            ServicoDeMaterias materias, ServicoDeTopicos topicos,
            ServicoDeConteudoProgramatico conteudo, JdbcTemplate banco,
            ObjectMapper mapeador) {
        this.operacoes = operacoes;
        this.estrutura = estrutura;
        this.materias = materias;
        this.topicos = topicos;
        this.conteudo = conteudo;
        this.banco = banco;
        this.mapeador = mapeador;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResultadoDaConsultaMcp validar(ContextoDaChamadaMcp contexto,
            Map<String, Object> argumentos) {
        Map<String, Object> analise = analisar(
                contexto.identidade().identificadorDoUsuario(), argumentos);
        return new ResultadoDaConsultaMcp("1",
                contexto.identificadorDaCorrelacao(),
                OffsetDateTime.now(ZoneOffset.UTC), analise, List.of());
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ResultadoDaConsultaMcp preparar(String tipo,
            ContextoDaChamadaMcp contexto, Map<String, Object> argumentos) {
        UUID usuario = contexto.identidade().identificadorDoUsuario();
        Map<String, Object> proposta = new LinkedHashMap<>(argumentos);
        Map<String, Object> analise = analisar(usuario, proposta);
        if (!lista(analise, "conflitos").isEmpty()) {
            throw new RegraDeDominio("CADASTRO_ASSISTIDO_POSSUI_CONFLITOS",
                    "Resolva os conflitos indicados antes de preparar o cadastro.");
        }
        proposta.put("analise", analise);
        String chave = contexto.identificadorDoEventoExterno() == null
                ? contexto.identificadorDaCorrelacao().toString()
                : contexto.identificadorDoEventoExterno();
        OperacaoPreparada preparada = operacoes.prepararParaConfirmacao(
                usuario, contexto.identidade().identificadorDoVinculo(), tipo,
                "Cadastrar concurso em rascunho. Nada foi alterado.",
                json(proposta), json(versoesAtuais(usuario)),
                "mcp:" + tipo + ":" + chave);
        Map<String, Object> dados = new LinkedHashMap<>(analise);
        dados.put("identificadorDaOperacao",
                preparada.operacao().identificador());
        dados.put("tipo", tipo);
        dados.put("estado", "AGUARDANDO_CONFIRMACAO");
        dados.put("nadaFoiAlterado", true);
        dados.put("codigoDeConfirmacao", preparada.codigoDeConfirmacao());
        dados.put("fraseDeConfirmacao",
                "/confirmar " + preparada.codigoDeConfirmacao());
        dados.put("expiraEm", preparada.operacao().expiraEm());
        return new ResultadoDaConsultaMcp("1",
                contexto.identificadorDaCorrelacao(),
                OffsetDateTime.now(ZoneOffset.UTC), dados, List.of());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> versoesAtuais(UUID usuario) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("materias", banco.queryForObject(
                "SELECT COUNT(*) FROM materias WHERE usuario_id = ?",
                Long.class, usuario));
        resultado.put("versoesDasMaterias", banco.queryForObject(
                "SELECT COALESCE(SUM(versao), 0) FROM materias WHERE usuario_id = ?",
                Long.class, usuario));
        resultado.put("topicos", banco.queryForObject("""
                SELECT COUNT(*) FROM topicos_da_materia t
                JOIN materias m ON m.identificador = t.materia_id
                WHERE m.usuario_id = ?
                """, Long.class, usuario));
        resultado.put("versoesDosTopicos", banco.queryForObject("""
                SELECT COALESCE(SUM(t.versao), 0) FROM topicos_da_materia t
                JOIN materias m ON m.identificador = t.materia_id
                WHERE m.usuario_id = ?
                """, Long.class, usuario));
        resultado.put("concursos", banco.queryForObject(
                "SELECT COUNT(*) FROM concursos WHERE usuario_id = ?",
                Long.class, usuario));
        resultado.put("marcaTemporal", banco.queryForObject("""
                SELECT GREATEST(
                    COALESCE(MAX(atualizado_em), TIMESTAMPTZ '1970-01-01')
                )::text FROM concursos WHERE usuario_id = ?
                """, String.class, usuario));
        return resultado;
    }

    @Transactional
    public Map<String, Object> aplicar(UUID usuario,
            Map<String, Object> proposta) {
        Map<String, Object> concursoInformado = mapa(proposta, "concurso");
        var concurso = estrutura.criarConcurso(usuario,
                texto(concursoInformado, "nome"),
                textoOpcional(concursoInformado, "descricao"),
                textoOpcional(concursoInformado, "orgao"),
                textoOpcional(concursoInformado, "banca"),
                SituacaoDoConcurso.PLANEJADO,
                dataOpcional(concursoInformado, "dataPrevista"));

        Map<String, Object> editalInformado = mapa(proposta, "edital");
        var edital = estrutura.criarEdital(usuario, concurso.identificador(),
                texto(editalInformado, "titulo"),
                textoOpcional(editalInformado, "numero"),
                inteiroOpcional(editalInformado, "ano"),
                textoOpcional(editalInformado, "descricao"),
                dataOpcional(editalInformado, "dataDePublicacao"),
                textoOpcional(editalInformado, "enderecoDoDocumento"));
        estrutura.definirEditalPrincipal(usuario, edital.identificador());

        Map<String, Object> cargoInformado = mapa(proposta, "cargo");
        var cargo = estrutura.criarCargo(usuario, concurso.identificador(),
                texto(cargoInformado, "nome"),
                textoOpcional(cargoInformado, "area"),
                textoOpcional(cargoInformado, "especialidade"),
                enumeracao(cargoInformado, "nivelDeEscolaridade",
                        NivelDeEscolaridade.class,
                        NivelDeEscolaridade.NAO_INFORMADO), 1);
        estrutura.selecionarCargo(usuario, cargo.identificador());

        Map<String, Object> provaInformada = mapaOpcional(proposta, "prova");
        var prova = estrutura.criarProva(usuario, cargo.identificador(),
                textoOu(provaInformada, "nome", "Prova principal"),
                enumeracao(provaInformada, "tipo", TipoDeProva.class,
                        TipoDeProva.OBJETIVA),
                enumeracao(provaInformada, "carater", CaraterDaProva.class,
                        CaraterDaProva.NAO_INFORMADO), 1,
                dataHoraOpcional(provaInformada, "dataHora"),
                inteiroOpcional(provaInformada, "duracaoEmMinutos"),
                inteiroOpcional(provaInformada, "quantidadeDeQuestoes"),
                decimalOpcional(provaInformada, "pontuacaoMaxima"),
                decimalOpcional(provaInformada, "pontuacaoMinima"));
        var grupo = estrutura.criarGrupo(usuario, prova.identificador(),
                textoOu(provaInformada, "nomeDoGrupo", "Conteudos"), 1,
                inteiroOpcional(provaInformada, "quantidadeDeQuestoes"),
                decimalOpcional(provaInformada, "pontuacaoMaxima"),
                decimalOpcional(provaInformada, "pontuacaoMinima"));

        int materiasCriadas = 0;
        int materiasReutilizadas = 0;
        int topicosCriados = 0;
        int itensCriados = 0;
        int sugestoesCriadas = 0;
        int ordemDaMateria = 0;
        for (Map<String, Object> informada : lista(proposta, "materias")) {
            Materia materia = encontrarMateria(usuario, texto(informada, "nome"));
            if (materia == null) {
                materia = materias.criar(usuario, texto(informada, "nome"),
                        textoOpcional(informada, "descricao"),
                        textoOu(informada, "cor", "#0f766e"));
                materiasCriadas++;
            } else {
                materiasReutilizadas++;
            }
            Map<String, TopicoDaMateria> topicosDaMateria =
                    topicosPorNome(usuario, materia.identificador());
            int ordemDoTopico = 0;
            for (Map<String, Object> informado : listaOpcional(informada,
                    "topicos")) {
                String chave = normalizar(texto(informado, "nome"));
                if (!topicosDaMateria.containsKey(chave)) {
                    TopicoDaMateria criado = topicos.criar(usuario,
                            materia.identificador(), null,
                            texto(informado, "nome"),
                            textoOpcional(informado, "descricao"),
                            inteiroOu(informado, "ordem", ++ordemDoTopico));
                    topicosDaMateria.put(chave, criado);
                    topicosCriados++;
                }
            }
            var materiaDaProva = estrutura.criarMateriaDaProva(usuario,
                    grupo.identificador(), materia.identificador(),
                    ++ordemDaMateria, decimalOpcional(informada, "peso"),
                    inteiroOpcional(informada, "quantidadeDeQuestoes"),
                    decimalOpcional(informada, "pontuacaoMaxima"));
            int ordemDoItem = 0;
            for (Map<String, Object> informado : listaOpcional(informada,
                    "itensDoEdital")) {
                var item = conteudo.criarItem(usuario,
                        materiaDaProva.identificador(), edital.identificador(),
                        texto(informado, "descricao"), null,
                        inteiroOu(informado, "ordem", ++ordemDoItem));
                itensCriados++;
                String nomeDoTopico = textoOpcional(informado, "topicoSugerido");
                TopicoDaMateria topico = nomeDoTopico == null ? null
                        : topicosDaMateria.get(normalizar(nomeDoTopico));
                if (topico != null) {
                    conteudo.criarSugestaoDeMapeamento(usuario,
                            item.identificador(), topico.identificador());
                    sugestoesCriadas++;
                }
            }
        }
        return Map.ofEntries(
                Map.entry("identificadorDoConcurso", concurso.identificador()),
                Map.entry("situacao", concurso.situacao().name()),
                Map.entry("ativo", concurso.ativo()),
                Map.entry("materiasCriadas", materiasCriadas),
                Map.entry("materiasReutilizadas", materiasReutilizadas),
                Map.entry("topicosCriados", topicosCriados),
                Map.entry("itensCriados", itensCriados),
                Map.entry("sugestoesDeMapeamentoPendentes", sugestoesCriadas));
    }

    private Map<String, Object> analisar(UUID usuario,
            Map<String, Object> proposta) {
        mapa(proposta, "concurso");
        mapa(proposta, "edital");
        mapa(proposta, "cargo");
        List<Map<String, Object>> classificacoes = new ArrayList<>();
        List<Map<String, Object>> conflitos = new ArrayList<>();
        Set<String> nomesNoLote = new LinkedHashSet<>();
        int itens = 0;
        int pendentes = 0;
        for (Map<String, Object> informada : lista(proposta, "materias")) {
            String nome = texto(informada, "nome");
            String chave = normalizar(nome);
            if (!nomesNoLote.add(chave)) {
                conflitos.add(Map.of("tipo", "MATERIA_DUPLICADA_NO_LOTE",
                        "nome", nome));
                continue;
            }
            Materia existente = encontrarMateria(usuario, nome);
            classificacoes.add(Map.of("elemento", "MATERIA", "nome", nome,
                    "decisao", existente == null ? "CRIAR" : "REUTILIZAR"));
            Set<String> topicosNoLote = new LinkedHashSet<>();
            for (Map<String, Object> topico : listaOpcional(informada, "topicos")) {
                String nomeDoTopico = texto(topico, "nome");
                if (!topicosNoLote.add(normalizar(nomeDoTopico))) {
                    conflitos.add(Map.of("tipo", "TOPICO_DUPLICADO_NO_LOTE",
                            "nome", nomeDoTopico, "materia", nome));
                } else {
                    classificacoes.add(Map.of("elemento", "TOPICO",
                            "nome", nomeDoTopico, "materia", nome,
                            "decisao", "CRIAR_OU_REUTILIZAR"));
                }
            }
            for (Map<String, Object> item : listaOpcional(informada,
                    "itensDoEdital")) {
                texto(item, "descricao");
                itens++;
                if (textoOpcional(item, "topicoSugerido") != null) pendentes++;
            }
        }
        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("materias", nomesNoLote.size());
        resumo.put("itensDoEdital", itens);
        resumo.put("mapeamentosSugeridosPendentes", pendentes);
        resumo.put("classificacoes", classificacoes);
        resumo.put("conflitos", conflitos);
        resumo.put("nadaFoiAlterado", true);
        resumo.put("aviso", "Sugestoes de mapeamento permanecem pendentes ate confirmacao humana.");
        return resumo;
    }

    private Materia encontrarMateria(UUID usuario, String nome) {
        return materias.listar(usuario, "", false, 0, 10_000).getContent()
                .stream().filter(item -> normalizar(item.nome()).equals(
                        normalizar(nome))).findFirst().orElse(null);
    }

    private Map<String, TopicoDaMateria> topicosPorNome(UUID usuario,
            UUID materia) {
        Map<String, TopicoDaMateria> resultado = new LinkedHashMap<>();
        topicos.listar(usuario, materia, "", false, 0, 10_000).getContent()
                .forEach(item -> resultado.put(normalizar(item.nome()), item));
        return resultado;
    }

    private String normalizar(String valor) {
        return Normalizer.normalize(valor.trim().toLowerCase(Locale.ROOT),
                Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", " ").trim();
    }

    private Map<String, Object> mapa(Map<String, Object> origem, String chave) {
        Object valor = origem.get(chave);
        if (!(valor instanceof Map<?, ?> bruto)) {
            throw new IllegalArgumentException(chave + " e obrigatorio.");
        }
        return converterMapa(bruto);
    }

    private Map<String, Object> mapaOpcional(Map<String, Object> origem,
            String chave) {
        Object valor = origem.get(chave);
        return valor instanceof Map<?, ?> bruto ? converterMapa(bruto) : Map.of();
    }

    private Map<String, Object> converterMapa(Map<?, ?> bruto) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        bruto.forEach((chave, valor) -> resultado.put(chave.toString(), valor));
        return resultado;
    }

    private List<Map<String, Object>> lista(Map<String, Object> origem,
            String chave) {
        List<Map<String, Object>> resultado = listaOpcional(origem, chave);
        if (resultado.isEmpty()) throw new IllegalArgumentException(
                chave + " deve possuir ao menos um item.");
        return resultado;
    }

    private List<Map<String, Object>> listaOpcional(Map<String, Object> origem,
            String chave) {
        Object valor = origem.get(chave);
        if (!(valor instanceof List<?> bruta)) return List.of();
        return bruta.stream().filter(Map.class::isInstance)
                .map(Map.class::cast).map(this::converterMapa).toList();
    }

    private String texto(Map<String, Object> mapa, String chave) {
        String valor = textoOpcional(mapa, chave);
        if (valor == null) throw new IllegalArgumentException(
                chave + " e obrigatorio.");
        return valor;
    }

    private String textoOpcional(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave);
        return valor == null || valor.toString().isBlank()
                ? null : valor.toString().trim();
    }

    private String textoOu(Map<String, Object> mapa, String chave, String padrao) {
        String valor = textoOpcional(mapa, chave);
        return valor == null ? padrao : valor;
    }

    private int inteiroOu(Map<String, Object> mapa, String chave, int padrao) {
        Integer valor = inteiroOpcional(mapa, chave);
        return valor == null ? padrao : valor;
    }

    private Integer inteiroOpcional(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave);
        if (valor == null) return null;
        return valor instanceof Number numero ? numero.intValue()
                : Integer.valueOf(valor.toString());
    }

    private BigDecimal decimalOpcional(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave);
        return valor == null ? null : new BigDecimal(valor.toString());
    }

    private LocalDate dataOpcional(Map<String, Object> mapa, String chave) {
        String valor = textoOpcional(mapa, chave);
        return valor == null ? null : LocalDate.parse(valor);
    }

    private OffsetDateTime dataHoraOpcional(Map<String, Object> mapa,
            String chave) {
        String valor = textoOpcional(mapa, chave);
        return valor == null ? null : OffsetDateTime.parse(valor);
    }

    private <T extends Enum<T>> T enumeracao(Map<String, Object> mapa,
            String chave, Class<T> tipo, T padrao) {
        String valor = textoOpcional(mapa, chave);
        return valor == null ? padrao : Enum.valueOf(tipo, valor);
    }

    private String json(Object valor) {
        try { return mapeador.writeValueAsString(valor); }
        catch (Exception excecao) { throw new IllegalArgumentException(excecao); }
    }
}
