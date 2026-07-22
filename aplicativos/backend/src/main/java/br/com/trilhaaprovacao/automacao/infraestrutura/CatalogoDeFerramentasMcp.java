package br.com.trilhaaprovacao.automacao.infraestrutura;

import br.com.trilhaaprovacao.automacao.aplicacao.ResultadoDaConsultaMcp;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeAuditoriaMcp;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeConsultasMcp;
import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class CatalogoDeFerramentasMcp {
    public static final String CHAVE_DO_CONTEXTO =
            ContextoDaChamadaMcp.class.getName();
    private static final Logger LOG =
            LoggerFactory.getLogger(CatalogoDeFerramentasMcp.class);
    private static final TypeReference<Map<String, Object>> TIPO_DE_MAPA =
            new TypeReference<>() { };

    private final ServicoDeConsultasMcp consultas;
    private final ServicoDeAuditoriaMcp auditoria;
    private final ObjectMapper mapeador;

    public CatalogoDeFerramentasMcp(ServicoDeConsultasMcp consultas,
            ServicoDeAuditoriaMcp auditoria, ObjectMapper mapeador) {
        this.consultas = consultas;
        this.auditoria = auditoria;
        this.mapeador = mapeador;
    }

    public List<McpStatelessServerFeatures.SyncToolSpecification> ferramentas() {
        return List.of(
                ferramenta("obter_agenda_de_estudos_de_hoje",
                        "Obtem a agenda factual de hoje na ordem do planejamento.",
                        esquemaVazio(), esquemaDosDadosDaAgenda(),
                        "planejamento:ler",
                        (contexto, argumentos) -> consultas.obterAgendaDeHoje(
                                contexto.identidade().identificadorDoUsuario(),
                                contexto.identificadorDaCorrelacao())),
                ferramenta("obter_revisoes_devidas",
                        "Obtem revisoes devidas e futuras em ate 90 dias.",
                        objeto(Map.of("ate", data()), List.of()),
                        esquemaDosDadosDasRevisoes(), "planejamento:ler",
                        (contexto, argumentos) ->
                                consultas.obterRevisoesDevidas(
                                        contexto.identidade().identificadorDoUsuario(),
                                        dataOpcional(argumentos, "ate"),
                                        contexto.identificadorDaCorrelacao())),
                ferramenta("obter_prioridades_atuais",
                        "Obtem o ranking consultivo atual dos topicos oficiais.",
                        objeto(Map.of("identificadorDaMateria", uuid()), List.of()),
                        esquemaDosDadosDasPrioridades(), "prioridades:ler",
                        (contexto, argumentos) ->
                                consultas.obterPrioridades(
                                        contexto.identidade().identificadorDoUsuario(),
                                        uuidOpcional(argumentos,
                                                "identificadorDaMateria"),
                                        contexto.identificadorDaCorrelacao())),
                ferramenta("obter_progresso_do_concurso",
                        "Obtem progresso objetivo, cobertura e lacunas do concurso ativo.",
                        esquemaVazio(), esquemaDosDadosDoProgresso(),
                        "concursos:ler",
                        (contexto, argumentos) -> consultas.obterProgresso(
                                contexto.identidade().identificadorDoUsuario(),
                                contexto.identificadorDaCorrelacao())),
                ferramenta("obter_historico_recente",
                        "Obtem estudos ativos e totais de um periodo recente.",
                        objeto(Map.of(
                                "quantidadeDeDias", inteiro(1, 90),
                                "limite", inteiro(1, 100)), List.of()),
                        esquemaDosDadosDoHistorico(), "estudos:ler",
                        (contexto, argumentos) ->
                                consultas.obterHistorico(
                                        contexto.identidade().identificadorDoUsuario(),
                                        inteiroOpcional(argumentos,
                                                "quantidadeDeDias", 30),
                                        inteiroOpcional(argumentos, "limite", 20),
                                        contexto.identificadorDaCorrelacao())),
                ferramenta("obter_estrutura_do_concurso",
                        "Obtem a estrutura agregada de um concurso do usuario.",
                        objeto(Map.of("identificadorDoConcurso", uuid()), List.of()),
                        esquemaDosDadosDaEstrutura(), "concursos:ler",
                        (contexto, argumentos) ->
                                consultas.obterEstruturaDoConcurso(
                                        contexto.identidade().identificadorDoUsuario(),
                                        uuidOpcional(argumentos,
                                                "identificadorDoConcurso"),
                                        contexto.identificadorDaCorrelacao())),
                ferramenta("explicar_bloco_de_estudo",
                        "Explica um bloco, sua origem, capacidade e justificativas.",
                        objeto(Map.of("identificadorDoBloco", uuid()),
                                List.of("identificadorDoBloco")),
                        esquemaDosDadosDaExplicacao(), "planejamento:ler",
                        (contexto, argumentos) ->
                                consultas.explicarBloco(
                                        contexto.identidade().identificadorDoUsuario(),
                                        uuidObrigatorio(argumentos,
                                                "identificadorDoBloco"),
                                        contexto.identificadorDaCorrelacao())),
                ferramenta("consultar_operacao_assistida",
                        "Consulta estado e recibo de uma operacao assistida.",
                        objeto(Map.of("identificadorDaOperacao", uuid()),
                                List.of("identificadorDaOperacao")),
                        esquemaDosDadosDaOperacao(), "operacoes:ler",
                        (contexto, argumentos) ->
                                consultas.consultarOperacao(
                                        contexto.identidade().identificadorDoUsuario(),
                                        uuidObrigatorio(argumentos,
                                                "identificadorDaOperacao"),
                                        contexto.identificadorDaCorrelacao())));
    }

    private McpStatelessServerFeatures.SyncToolSpecification ferramenta(
            String nome, String descricao, Map<String, Object> esquema,
            Map<String, Object> esquemaDosDados, String escopo,
            Executor executor) {
        McpSchema.Tool ferramenta = McpSchema.Tool.builder(nome, esquema)
                .title(nome.replace('_', ' '))
                .description(descricao)
                .outputSchema(esquemaDaSaida(esquemaDosDados))
                .annotations(McpSchema.ToolAnnotations.builder()
                        .readOnlyHint(true).destructiveHint(false)
                        .idempotentHint(true).openWorldHint(false).build())
                .build();
        BiFunction<McpTransportContext, McpSchema.CallToolRequest,
                McpSchema.CallToolResult> chamada = (transporte, pedido) ->
                        executar(nome, escopo, executor, transporte,
                                pedido.arguments());
        return new McpStatelessServerFeatures.SyncToolSpecification(
                ferramenta, chamada);
    }

    private McpSchema.CallToolResult executar(String ferramenta, String escopo,
            Executor executor, McpTransportContext transporte,
            Map<String, Object> argumentos) {
        ContextoDaChamadaMcp contexto = contexto(transporte);
        String entrada = json(argumentos);
        try {
            exigirEscopo(contexto.identidade(), escopo);
            ResultadoDaConsultaMcp resultado = executor.executar(
                    contexto, argumentos == null ? Map.of() : argumentos);
            Map<String, Object> estruturado = mapeador.convertValue(
                    resultado, TIPO_DE_MAPA);
            String saida = json(estruturado);
            auditoria.registrar(contexto.identidade(), ferramenta, entrada,
                    saida, "SUCESSO", resultado.identificadorDaCorrelacao());
            return McpSchema.CallToolResult.builder()
                    .addTextContent(saida)
                    .structuredContent(estruturado)
                    .isError(false).build();
        } catch (RuntimeException excecao) {
            ErroDaFerramenta erro = erro(excecao,
                    contexto.identificadorDaCorrelacao());
            Map<String, Object> estruturado = mapaDoErro(erro);
            String saida = json(estruturado);
            try {
                auditoria.registrar(contexto.identidade(), ferramenta, entrada,
                        saida, "FALHA", contexto.identificadorDaCorrelacao());
            } catch (RuntimeException falhaDaAuditoria) {
                LOG.error("Falha ao auditar ferramenta MCP. correlacao={}",
                        contexto.identificadorDaCorrelacao(), falhaDaAuditoria);
            }
            if ("ERRO_INTERNO".equals(erro.codigo())) {
                LOG.error("Falha inesperada em ferramenta MCP. correlacao={}",
                        contexto.identificadorDaCorrelacao(), excecao);
            }
            return McpSchema.CallToolResult.builder()
                    .addTextContent(saida)
                    .structuredContent(estruturado)
                    .isError(true).build();
        }
    }

    private ContextoDaChamadaMcp contexto(McpTransportContext transporte) {
        Object valor = transporte.get(CHAVE_DO_CONTEXTO);
        if (valor instanceof ContextoDaChamadaMcp contexto) {
            return contexto;
        }
        throw new AccessDeniedException("Contexto MCP nao autenticado.");
    }

    private void exigirEscopo(IdentidadeDaIntegracaoMcp identidade,
            String escopo) {
        if (!identidade.possuiEscopo(escopo)) {
            throw new AccessDeniedException(
                    "A credencial nao possui o escopo exigido.");
        }
    }

    private ErroDaFerramenta erro(RuntimeException excecao, UUID correlacao) {
        if (excecao instanceof AccessDeniedException) {
            return new ErroDaFerramenta("ESCOPO_INSUFICIENTE",
                    "A integracao nao possui permissao para esta consulta.",
                    false, correlacao, List.of());
        }
        if (excecao instanceof RegraDeDominio regra) {
            return new ErroDaFerramenta(regra.codigo(), regra.getMessage(),
                    true, correlacao, List.of());
        }
        if (excecao instanceof RecursoNaoEncontrado recurso) {
            return new ErroDaFerramenta(recurso.codigo(), recurso.getMessage(),
                    false, correlacao, List.of());
        }
        if (excecao instanceof ConflitoDeDominio conflito) {
            return new ErroDaFerramenta(conflito.codigo(), conflito.getMessage(),
                    true, correlacao, List.of());
        }
        if (excecao instanceof IllegalArgumentException) {
            return new ErroDaFerramenta("ENTRADA_INVALIDA",
                    "Os argumentos da ferramenta possuem formato invalido.",
                    true, correlacao, List.of());
        }
        return new ErroDaFerramenta("ERRO_INTERNO",
                "Nao foi possivel concluir a consulta.", true,
                correlacao, List.of());
    }

    private Map<String, Object> mapaDoErro(ErroDaFerramenta erro) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("versaoDoContrato", "1");
        envelope.put("erro", erro);
        return envelope;
    }

    private String json(Object valor) {
        try {
            return mapeador.writeValueAsString(valor == null ? Map.of() : valor);
        } catch (Exception excecao) {
            throw new IllegalStateException("Falha ao serializar resultado MCP.", excecao);
        }
    }

    private LocalDate dataOpcional(Map<String, Object> argumentos, String chave) {
        Object valor = argumentos.get(chave);
        return valor == null ? null : LocalDate.parse(valor.toString());
    }

    private UUID uuidOpcional(Map<String, Object> argumentos, String chave) {
        Object valor = argumentos.get(chave);
        return valor == null ? null : UUID.fromString(valor.toString());
    }

    private UUID uuidObrigatorio(Map<String, Object> argumentos, String chave) {
        UUID valor = uuidOpcional(argumentos, chave);
        if (valor == null) {
            throw new IllegalArgumentException(chave + " e obrigatorio.");
        }
        return valor;
    }

    private int inteiroOpcional(Map<String, Object> argumentos,
            String chave, int padrao) {
        Object valor = argumentos.get(chave);
        if (valor == null) return padrao;
        if (valor instanceof Number numero) return numero.intValue();
        return Integer.parseInt(valor.toString());
    }

    private Map<String, Object> esquemaVazio() {
        return objeto(Map.of(), List.of());
    }

    private Map<String, Object> objeto(Map<String, Object> propriedades,
            List<String> obrigatorias) {
        Map<String, Object> esquema = new LinkedHashMap<>();
        esquema.put("type", "object");
        esquema.put("properties", propriedades);
        if (!obrigatorias.isEmpty()) esquema.put("required", obrigatorias);
        esquema.put("additionalProperties", false);
        return esquema;
    }

    private Map<String, Object> data() {
        return Map.of("type", "string", "format", "date");
    }

    private Map<String, Object> uuid() {
        return Map.of("type", "string", "format", "uuid");
    }

    private Map<String, Object> inteiro(int minimo, int maximo) {
        return Map.of("type", "integer", "minimum", minimo,
                "maximum", maximo);
    }

    private Map<String, Object> esquemaDaSaida(
            Map<String, Object> esquemaDosDados) {
        Map<String, Object> aviso = objeto(Map.of(
                "codigo", texto(120),
                "mensagem", texto(1_000)),
                List.of("codigo", "mensagem"));
        Map<String, Object> sucesso = objeto(Map.of(
                "versaoDoContrato", constante("1"),
                "identificadorDaCorrelacao", uuid(),
                "geradoEm", dataHora(),
                "dados", esquemaDosDados,
                "avisos", lista(aviso, 100)),
                List.of("versaoDoContrato", "identificadorDaCorrelacao",
                        "geradoEm", "dados", "avisos"));
        Map<String, Object> erro = objeto(Map.of(
                "codigo", texto(120),
                "mensagem", texto(1_000),
                "recuperavel", booleano(),
                "identificadorDaCorrelacao", uuid(),
                "campos", lista(texto(160), 100)),
                List.of("codigo", "mensagem", "recuperavel",
                        "identificadorDaCorrelacao", "campos"));
        Map<String, Object> falha = objeto(Map.of(
                "versaoDoContrato", constante("1"),
                "erro", erro), List.of("versaoDoContrato", "erro"));
        Map<String, Object> saida = new LinkedHashMap<>();
        saida.put("type", "object");
        saida.put("oneOf", List.of(sucesso, falha));
        return saida;
    }

    private Map<String, Object> esquemaDosDadosDaAgenda() {
        return objetoComTodas(Map.of(
                "fusoHorario", constante("America/Sao_Paulo"),
                "data", data(),
                "planejamento", esquemaDoPlanejamentoDeHoje(),
                "execucaoEmAndamento", nuloOu(esquemaDaExecucao()),
                "revisoes", lista(esquemaDaRevisao(), 10_000)));
    }

    private Map<String, Object> esquemaDosDadosDasRevisoes() {
        return objetoComTodas(Map.of(
                "fusoHorario", constante("America/Sao_Paulo"),
                "agenda", esquemaDaAgendaDeRevisoes()));
    }

    private Map<String, Object> esquemaDosDadosDasPrioridades() {
        return objetoComTodas(Map.of(
                "priorizacao", esquemaDaPriorizacao()));
    }

    private Map<String, Object> esquemaDosDadosDoProgresso() {
        return objetoComTodas(Map.of(
                "fusoHorario", constante("America/Sao_Paulo"),
                "dataDeReferencia", data(),
                "progresso", esquemaDoDashboard(),
                "resumoDaPriorizacao", nuloOu(esquemaDoResumoDaPriorizacao())));
    }

    private Map<String, Object> esquemaDosDadosDoHistorico() {
        return objetoComTodas(Map.of(
                "fusoHorario", constante("America/Sao_Paulo"),
                "inicio", data(),
                "fim", data(),
                "totais", objetoComTodas(Map.of(
                        "quantidadeDeEstudos", inteiroNaoNegativo(),
                        "minutos", inteiroNaoNegativo(),
                        "questoes", inteiroNaoNegativo(),
                        "acertos", inteiroNaoNegativo(),
                        "erros", inteiroNaoNegativo())),
                "estudos", lista(esquemaDoEstudoRecente(), 100),
                "execucoesEmAndamento", lista(esquemaDaExecucao(), 10_000)));
    }

    private Map<String, Object> esquemaDosDadosDaEstrutura() {
        return objetoComTodas(Map.of("concurso", esquemaDaEstruturaDoConcurso()));
    }

    private Map<String, Object> esquemaDosDadosDaExplicacao() {
        Map<String, Object> propriedades = new LinkedHashMap<>(
                propriedadesDoBlocoExplicado());
        propriedades.put("priorizacaoAtual", nuloOu(esquemaDoTopicoPriorizado()));
        propriedades.put("revisaoAtual", nuloOu(esquemaDaRevisao()));
        return objetoComTodas(propriedades);
    }

    private Map<String, Object> esquemaDosDadosDaOperacao() {
        return objetoComTodas(Map.of(
                "identificador", uuid(),
                "tipo", texto(100),
                "estado", enumeracao("PREPARADA", "AGUARDANDO_CONFIRMACAO",
                        "CONFIRMADA", "APLICADA", "CANCELADA", "EXPIRADA",
                        "FALHOU"),
                "resumo", texto(500),
                "expiraEm", dataHora(),
                "resultado", nuloOu(texto(20_000)),
                "criadoEm", dataHora(),
                "atualizadoEm", dataHora()));
    }

    private Map<String, Object> esquemaDoPlanejamentoDeHoje() {
        return objetoComTodas(Map.ofEntries(
                Map.entry("estado", enumeracao("SEM_PLANO", "PLANO_EM_RASCUNHO",
                        "PLANO_ENCERRADO", "PLANO_CANCELADO", "DIA_SEM_BLOCOS",
                        "DIA_PLANEJADO")),
                Map.entry("data", data()),
                Map.entry("identificadorDoPlano", nuloOu(uuid())),
                Map.entry("dataInicialDoPlano", nuloOu(data())),
                Map.entry("minutosDisponiveis", inteiroNaoNegativo()),
                Map.entry("minutosPlanejados", inteiroNaoNegativo()),
                Map.entry("proximoBloco", nuloOu(esquemaDoBloco())),
                Map.entry("sequencia", lista(esquemaDoBloco(), 10_000)),
                Map.entry("atrasados", lista(esquemaDoBloco(), 10_000)),
                Map.entry("realizados", lista(esquemaDoBloco(), 10_000))));
    }

    private Map<String, Object> esquemaDoBloco() {
        return objetoComTodas(Map.ofEntries(
                Map.entry("identificador", uuid()),
                Map.entry("identificadorDoPlano", uuid()),
                Map.entry("identificadorDaMateria", nuloOu(uuid())),
                Map.entry("identificadorDoTopico", nuloOu(uuid())),
                Map.entry("titulo", texto(200)),
                Map.entry("tipoDeAtividade", tipoDeAtividade()),
                Map.entry("data", data()),
                Map.entry("duracaoPrevistaEmMinutos", inteiro(1, 1_440)),
                Map.entry("ordem", inteiroPositivo()),
                Map.entry("horarioPrevisto", nuloOu(texto(18))),
                Map.entry("observacao", nuloOu(texto(2_000))),
                Map.entry("origem", enumeracao("MANUAL",
                        "GERADO_DETERMINISTICAMENTE", "GERADO_AJUSTADO_MANUALMENTE",
                        "REPLANEJADO", "REPLANEJADO_AJUSTADO_MANUALMENTE")),
                Map.entry("justificativaDaGeracao", nuloOu(texto(2_000))),
                Map.entry("justificativaDoReplanejamento", nuloOu(texto(2_000))),
                Map.entry("estado", estadoDoBloco()),
                Map.entry("quantidadeDeReagendamentos", inteiroNaoNegativo()),
                Map.entry("reagendadoEm", nuloOu(dataHora())),
                Map.entry("criadoEm", dataHora()),
                Map.entry("atualizadoEm", dataHora()),
                Map.entry("versao", inteiroNaoNegativo())));
    }

    private Map<String, Object> esquemaDaAgendaDeRevisoes() {
        return objetoComTodas(Map.of(
                "dataDeReferencia", data(),
                "ate", data(),
                "revisoes", lista(esquemaDaRevisao(), 10_000)));
    }

    private Map<String, Object> esquemaDaRevisao() {
        return objetoComTodas(Map.ofEntries(
                Map.entry("identificadorDoTopico", uuid()),
                Map.entry("nomeDoTopico", texto(200)),
                Map.entry("identificadorDaMateria", uuid()),
                Map.entry("nomeDaMateria", texto(200)),
                Map.entry("etapa", inteiro(0, 5)),
                Map.entry("intervaloEmDias", inteiro(1, 60)),
                Map.entry("dataDevida", data()),
                Map.entry("diasEmAtraso", inteiroNaoNegativo()),
                Map.entry("ultimaRevisao", nuloOu(data())),
                Map.entry("ultimaRecordacao", nuloOu(inteiro(1, 5))),
                Map.entry("situacao", enumeracao("VENCIDA", "DEVIDA_HOJE",
                        "FUTURA", "JA_PLANEJADA")),
                Map.entry("blocoAberto", nuloOu(objetoComTodas(Map.of(
                        "identificador", uuid(),
                        "identificadorDoPlano", uuid(),
                        "dataInicialDoPlano", data(),
                        "data", data(),
                        "estado", estadoDoBloco()))))));
    }

    private Map<String, Object> esquemaDaPriorizacao() {
        return objetoComTodas(Map.of(
                "contexto", objetoComTodas(Map.of(
                        "concurso", esquemaDaReferenciaOficial(),
                        "cargo", esquemaDaReferenciaOficial(),
                        "edital", esquemaDaReferenciaOficial(),
                        "dataReferencia", data(),
                        "inicioJanelaRecente", data())),
                "resumo", esquemaDoResumoDaPriorizacao(),
                "itensSemMapeamento", lista(objetoComTodas(Map.of(
                        "identificador", uuid(),
                        "descricao", texto(2_000),
                        "identificadorDaMateria", nuloOu(uuid()),
                        "nomeDaMateria", nuloOu(texto(200)),
                        "ordem", inteiroNaoNegativo())), 10_000),
                "materias", lista(esquemaDaMateriaPriorizada(), 10_000)));
    }

    private Map<String, Object> esquemaDaReferenciaOficial() {
        return objetoComTodas(Map.of(
                "identificador", uuid(), "nome", texto(300)));
    }

    private Map<String, Object> esquemaDoResumoDaPriorizacao() {
        return objetoComTodas(Map.of(
                "itensOficiais", inteiroNaoNegativo(),
                "itensSemMapeamento", inteiroNaoNegativo(),
                "topicosExigidos", inteiroNaoNegativo(),
                "lacunas", inteiroNaoNegativo(),
                "fraquezas", inteiroNaoNegativo(),
                "consolidados", inteiroNaoNegativo()));
    }

    private Map<String, Object> esquemaDaMateriaPriorizada() {
        return objetoComTodas(Map.of(
                "identificador", uuid(),
                "nome", texto(200),
                "topicos", lista(esquemaDoTopicoPriorizado(), 10_000)));
    }

    private Map<String, Object> esquemaDoTopicoPriorizado() {
        return objetoComTodas(Map.ofEntries(
                Map.entry("identificador", uuid()),
                Map.entry("nome", texto(200)),
                Map.entry("grupo", enumeracao("LACUNA", "FRAQUEZA",
                        "CONSOLIDADO")),
                Map.entry("faixa", enumeracao("SEM_ESTUDO", "SEM_EVIDENCIA",
                        "EVIDENCIA_DESATUALIZADA", "DADOS_INSUFICIENTES",
                        "PRECISA_REFORCO", "DESEMPENHO_PARCIAL", "CONSOLIDADO")),
                Map.entry("posicaoNoGrupo", inteiroPositivo()),
                Map.entry("acaoSugerida", enumeracao("TEORIA", "QUESTOES")),
                Map.entry("possuiMaterial", booleano()),
                Map.entry("quantidadeDeItensOficiais", inteiroNaoNegativo()),
                Map.entry("indicadores", esquemaDosIndicadores()),
                Map.entry("justificativas", lista(texto(1_000), 100))));
    }

    private Map<String, Object> esquemaDosIndicadores() {
        return objetoComTodas(Map.ofEntries(
                Map.entry("estudos", inteiroNaoNegativo()),
                Map.entry("evidencias", inteiroNaoNegativo()),
                Map.entry("questoesRecentes", inteiroNaoNegativo()),
                Map.entry("acertosRecentes", inteiroNaoNegativo()),
                Map.entry("errosRecentes", inteiroNaoNegativo()),
                Map.entry("percentual", nuloOu(numero(0, 100))),
                Map.entry("ultimaRecordacao", nuloOu(inteiro(1, 5))),
                Map.entry("ultimaDificuldade", nuloOu(inteiro(1, 5))),
                Map.entry("ultimaEvidencia", nuloOu(dataHora())),
                Map.entry("quantidadeDePadroesRepetidos", inteiroNaoNegativo()),
                Map.entry("ultimaOcorrenciaDePadraoRepetido", nuloOu(data()))));
    }

    private Map<String, Object> esquemaDoDashboard() {
        return objetoComTodas(Map.ofEntries(
                Map.entry("concursoAtivo", nuloOu(objetoComTodas(Map.ofEntries(
                        Map.entry("identificador", uuid()),
                        Map.entry("nome", texto(300)),
                        Map.entry("orgao", nuloOu(texto(300))),
                        Map.entry("banca", nuloOu(texto(300))),
                        Map.entry("situacao", texto(100)),
                        Map.entry("identificadorDoCargoSelecionado", nuloOu(uuid())),
                        Map.entry("nomeDoCargoSelecionado", nuloOu(texto(300))))))),
                Map.entry("dataDaProximaProva", nuloOu(data())),
                Map.entry("diasAteAProva", nuloOu(inteiro())),
                Map.entry("tempoEstudadoNaSemanaEmMinutos", inteiroNaoNegativo()),
                Map.entry("quantidadeDeMaterias", inteiroNaoNegativo()),
                Map.entry("quantidadeDeTopicosExigidos", inteiroNaoNegativo()),
                Map.entry("quantidadeDeTopicosComEstudo", inteiroNaoNegativo()),
                Map.entry("quantidadeDeItensMapeados", inteiroNaoNegativo()),
                Map.entry("quantidadeDeItensSemMapeamento", inteiroNaoNegativo()),
                Map.entry("atividadeRecente", lista(objetoComTodas(Map.of(
                        "identificador", uuid(),
                        "identificadorDoTopico", uuid(),
                        "nomeDoTopico", texto(200),
                        "tituloDoMaterial", nuloOu(texto(300)),
                        "dataHora", dataHora(),
                        "duracaoEmMinutos", inteiroPositivo())), 10_000)),
                Map.entry("alertas", lista(objetoComTodas(Map.of(
                        "codigo", texto(120),
                        "titulo", texto(300),
                        "mensagem", texto(1_000),
                        "nivel", texto(50))), 100))));
    }

    private Map<String, Object> esquemaDoEstudoRecente() {
        return objetoComTodas(Map.ofEntries(
                Map.entry("identificador", uuid()),
                Map.entry("dataHora", dataHora()),
                Map.entry("duracaoEmMinutos", inteiroPositivo()),
                Map.entry("tipoDeEstudo", tipoDeAtividade()),
                Map.entry("observacao", nuloOu(texto(5_000))),
                Map.entry("identificadorDaMateria", uuid()),
                Map.entry("materia", texto(200)),
                Map.entry("identificadorDoTopico", uuid()),
                Map.entry("topico", texto(200)),
                Map.entry("material", nuloOu(texto(300))),
                Map.entry("quantidadeDeQuestoes", nuloOu(inteiroNaoNegativo())),
                Map.entry("quantidadeDeAcertos", nuloOu(inteiroNaoNegativo())),
                Map.entry("quantidadeDeErros", nuloOu(inteiroNaoNegativo())),
                Map.entry("nivelDeRecordacao", nuloOu(inteiro(1, 5))),
                Map.entry("dificuldadePercebida", nuloOu(inteiro(1, 5)))));
    }

    private Map<String, Object> esquemaDaExecucao() {
        return objetoComTodas(Map.of(
                "identificador", uuid(),
                "identificadorDoBloco", uuid(),
                "iniciadaEm", dataHora(),
                "titulo", texto(200),
                "tipoDeAtividade", tipoDeAtividade(),
                "data", data(),
                "materia", nuloOu(texto(200)),
                "topico", nuloOu(texto(200))));
    }

    private Map<String, Object> esquemaDaEstruturaDoConcurso() {
        Map<String, Object> materia = objetoComTodas(Map.of(
                "identificadorDaVinculacao", uuid(),
                "identificador", nuloOu(uuid()),
                "nome", nuloOu(texto(200)),
                "itens", inteiroNaoNegativo(),
                "itensMapeados", inteiroNaoNegativo()));
        Map<String, Object> grupo = objetoComTodas(Map.of(
                "identificador", uuid(), "nome", texto(300),
                "materias", lista(materia, 10_000)));
        Map<String, Object> prova = objetoComTodas(Map.of(
                "identificador", uuid(), "nome", texto(300),
                "tipo", texto(100),
                "dataHoraPrevista", nuloOu(dataHora()),
                "grupos", lista(grupo, 10_000)));
        Map<String, Object> cargo = objetoComTodas(Map.of(
                "identificador", uuid(), "nome", texto(300),
                "selecionado", booleano(),
                "provas", lista(prova, 10_000)));
        Map<String, Object> concursoCompleto = objetoComTodas(Map.of(
                "identificador", uuid(),
                "nome", texto(300),
                "orgao", nuloOu(texto(300)),
                "banca", nuloOu(texto(300)),
                "situacao", texto(100),
                "ativo", booleano(),
                "editalPrincipal", nuloOu(objetoComTodas(Map.of(
                        "identificador", uuid(), "titulo", texto(300),
                        "principal", booleano()))),
                "cargos", lista(cargo, 10_000)));
        Map<String, Object> semConcurso = objetoComTodas(Map.of(
                "estado", constante("SEM_CONCURSO_ATIVO")));
        return alternativas(concursoCompleto, semConcurso);
    }

    private Map<String, Object> propriedadesDoBlocoExplicado() {
        return Map.ofEntries(
                Map.entry("identificador", uuid()),
                Map.entry("titulo", texto(200)),
                Map.entry("tipoDeAtividade", tipoDeAtividade()),
                Map.entry("data", data()),
                Map.entry("duracaoPrevistaEmMinutos", inteiro(1, 1_440)),
                Map.entry("ordem", inteiroPositivo()),
                Map.entry("horarioPrevisto", nuloOu(texto(18))),
                Map.entry("observacao", nuloOu(texto(2_000))),
                Map.entry("origem", enumeracao("MANUAL",
                        "GERADO_DETERMINISTICAMENTE", "GERADO_AJUSTADO_MANUALMENTE",
                        "REPLANEJADO", "REPLANEJADO_AJUSTADO_MANUALMENTE")),
                Map.entry("justificativaDaGeracao", nuloOu(texto(2_000))),
                Map.entry("justificativaDoReplanejamento", nuloOu(texto(2_000))),
                Map.entry("estado", estadoDoBloco()),
                Map.entry("quantidadeDeReagendamentos", inteiroNaoNegativo()),
                Map.entry("identificadorDaMateria", nuloOu(uuid())),
                Map.entry("materia", nuloOu(texto(200))),
                Map.entry("identificadorDoTopico", nuloOu(uuid())),
                Map.entry("topico", nuloOu(texto(200))),
                Map.entry("minutosDisponiveis", nuloOu(inteiroNaoNegativo())),
                Map.entry("minutosOcupados", inteiroNaoNegativo()),
                Map.entry("minutosRestantes", nuloOu(inteiroNaoNegativo())));
    }

    private Map<String, Object> objetoComTodas(
            Map<String, Object> propriedades) {
        return objeto(propriedades, List.copyOf(propriedades.keySet()));
    }

    private Map<String, Object> alternativas(Map<String, Object>... esquemas) {
        return Map.of("oneOf", List.of(esquemas));
    }

    private Map<String, Object> nuloOu(Map<String, Object> esquema) {
        return Map.of("anyOf", List.of(esquema, Map.of("type", "null")));
    }

    private Map<String, Object> lista(Map<String, Object> item, int maximo) {
        return Map.of("type", "array", "items", item, "maxItems", maximo);
    }

    private Map<String, Object> texto(int maximo) {
        return Map.of("type", "string", "minLength", 1,
                "maxLength", maximo);
    }

    private Map<String, Object> constante(String valor) {
        return Map.of("type", "string", "const", valor);
    }

    private Map<String, Object> enumeracao(String... valores) {
        return Map.of("type", "string", "enum", List.of(valores));
    }

    private Map<String, Object> booleano() {
        return Map.of("type", "boolean");
    }

    private Map<String, Object> inteiro() {
        return Map.of("type", "integer");
    }

    private Map<String, Object> inteiroNaoNegativo() {
        return Map.of("type", "integer", "minimum", 0);
    }

    private Map<String, Object> inteiroPositivo() {
        return Map.of("type", "integer", "minimum", 1);
    }

    private Map<String, Object> numero(int minimo, int maximo) {
        return Map.of("type", "number", "minimum", minimo,
                "maximum", maximo);
    }

    private Map<String, Object> dataHora() {
        return Map.of("type", "string", "format", "date-time");
    }

    private Map<String, Object> tipoDeAtividade() {
        return enumeracao("TEORIA", "QUESTOES", "REVISAO",
                "CADERNO_DE_ERROS", "SIMULADO", "DISCURSIVA", "OUTRA");
    }

    private Map<String, Object> estadoDoBloco() {
        return enumeracao("PLANEJADO", "EM_ANDAMENTO", "CONCLUIDO",
                "PARCIALMENTE_CONCLUIDO", "CANCELADO");
    }

    @FunctionalInterface
    private interface Executor {
        ResultadoDaConsultaMcp executar(ContextoDaChamadaMcp contexto,
                Map<String, Object> argumentos);
    }

    private record ErroDaFerramenta(String codigo, String mensagem,
            boolean recuperavel, UUID identificadorDaCorrelacao,
            List<String> campos) {
    }
}
