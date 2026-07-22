package br.com.trilhaaprovacao.automacao.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeOperacoesAssistidas;
import br.com.trilhaaprovacao.automacao.infraestrutura.FiltroDeCredencialMcp;
import br.com.trilhaaprovacao.automacao.infraestrutura.ServicoDeSegredosDaAutomacao;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.McpHttpClientTransportAuthorizationException;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "trilha.automacao.habilitada=true",
            "trilha.automacao.segredo-de-hash="
                    + "segredo-exclusivo-dos-testes-mcp-123456789",
            "trilha.automacao.segredo-do-gateway="
                    + "segredo-do-gateway-dos-testes-mcp-123456789",
            "trilha.automacao.mcp.hosts-permitidos=localhost:*",
            "trilha.automacao.mcp.origens-permitidas=http://localhost"
        })
class McpIntegracaoTest {
    private static final String TODOS_OS_ESCOPOS = String.join(" ",
            "planejamento:ler", "prioridades:ler", "concursos:ler",
            "estudos:ler", "operacoes:ler");

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("trilha_aprovacao_mcp")
                    .withUsername("teste")
                    .withPassword("teste");

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        propriedades.add("spring.datasource.username", POSTGRESQL::getUsername);
        propriedades.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @LocalServerPort int porta;
    @Autowired JdbcTemplate banco;
    @Autowired ServicoDeSegredosDaAutomacao segredos;
    @Autowired ServicoDeOperacoesAssistidas operacoes;

    @BeforeEach
    void limparBanco() {
        banco.execute("""
                TRUNCATE TABLE eventos_de_auditoria_da_automacao,
                    operacoes_assistidas, credenciais_de_integracao,
                    vinculos_de_canal, usuarios CASCADE
                """);
    }

    @Test
    void deveNegociarStreamableHttpEExporCatalogoSomenteLeitura()
            throws Exception {
        IntegracaoCriada integracao = criarIntegracao(
                "catalogo", 71001L, TODOS_OS_ESCOPOS);

        try (McpSyncClient cliente = cliente(integracao)) {
            McpSchema.InitializeResult inicializacao = cliente.initialize();
            assertThat(inicializacao.serverInfo().name())
                    .isEqualTo("trilha-aprovacao");
            assertThat(cliente.isInitialized()).isTrue();

            List<McpSchema.Tool> ferramentas = cliente.listTools().tools();
            assertThat(ferramentas).extracting(McpSchema.Tool::name)
                    .containsExactly(
                            "obter_agenda_de_estudos_de_hoje",
                            "obter_revisoes_devidas",
                            "obter_prioridades_atuais",
                            "obter_progresso_do_concurso",
                            "obter_historico_recente",
                            "obter_estrutura_do_concurso",
                            "explicar_bloco_de_estudo",
                            "consultar_operacao_assistida");
            assertThat(ferramentas).allSatisfy(ferramenta -> {
                assertThat(ferramenta.inputSchema())
                        .containsEntry("type", "object")
                        .containsEntry("additionalProperties", false);
                assertThat(ferramenta.outputSchema())
                        .containsEntry("type", "object")
                        .containsKey("oneOf");
                assertThat(ramosDaSaida(ferramenta)).hasSize(2)
                        .allSatisfy(ramo -> assertThat(ramo)
                                .containsEntry("type", "object")
                                .containsEntry("additionalProperties", false));
                assertThat(esquemaDosDados(ferramenta))
                        .containsEntry("type", "object")
                        .containsEntry("additionalProperties", false);
                assertThat(ferramenta.annotations().readOnlyHint()).isTrue();
                assertThat(ferramenta.annotations().destructiveHint()).isFalse();
                assertThat(ferramenta.annotations().idempotentHint()).isTrue();
                assertThat(ferramenta.annotations().openWorldHint()).isFalse();
            });
            assertThat(propriedades(esquemaDosDados(ferramentas.getFirst())))
                    .containsKeys("fusoHorario", "data", "planejamento",
                            "execucaoEmAndamento", "revisoes");
            assertThat(propriedades(esquemaDosDados(ferramentas.get(4))))
                    .containsKeys("inicio", "fim", "totais", "estudos",
                            "execucoesEmAndamento");

            McpSchema.CallToolResult resultado = cliente.callTool(
                    chamada("obter_agenda_de_estudos_de_hoje", Map.of()));
            assertThat(resultado.isError()).as("resultado MCP: %s", resultado)
                    .isFalse();
            Map<String, Object> resposta = conteudoEstruturado(resultado);
            assertThat(resposta).containsEntry("versaoDoContrato", "1")
                    .containsKeys("identificadorDaCorrelacao", "geradoEm",
                            "dados", "avisos");
            assertThat(resposta.get("dados")).isInstanceOf(Map.class);
        }

        assertThat(banco.queryForObject("""
                SELECT ultimo_uso_em IS NOT NULL
                FROM credenciais_de_integracao
                WHERE identificador = ?
                """, Boolean.class, integracao.identificadorDaCredencial()))
                .isTrue();
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM eventos_de_auditoria_da_automacao
                WHERE usuario_id = ? AND vinculo_id = ?
                  AND ator = 'IA_TELEGRAM' AND fonte = 'MCP'
                  AND ferramenta = 'obter_agenda_de_estudos_de_hoje'
                  AND acao = 'FERRAMENTA_MCP_CONSULTADA'
                  AND resultado = 'SUCESSO'
                  AND hash_da_entrada IS NOT NULL
                  AND hash_da_saida IS NOT NULL
                """, Integer.class, integracao.identificadorDoUsuario(),
                integracao.identificadorDoVinculo())).isEqualTo(1);
    }

    @Test
    void deveAplicarEscoposEIsolarOperacaoDeOutroUsuario() throws Exception {
        IntegracaoCriada integracaoA = criarIntegracao(
                "usuario-a", 72001L, TODOS_OS_ESCOPOS);
        IntegracaoCriada integracaoB = criarIntegracao(
                "usuario-b", 72002L, TODOS_OS_ESCOPOS);
        var operacaoB = operacoes.preparar(
                integracaoB.identificadorDoUsuario(),
                integracaoB.identificadorDoVinculo(), "REGISTRAR_ESTUDO",
                "Registrar estudo", "{\"minutos\":30}", "{\"plano\":1}",
                "operacao-exclusiva-do-usuario-b");

        try (McpSyncClient cliente = cliente(integracaoA)) {
            cliente.initialize();
            McpSchema.CallToolResult isolada = cliente.callTool(chamada(
                    "consultar_operacao_assistida", Map.of(
                            "identificadorDaOperacao",
                            operacaoB.identificador().toString())));
            assertThat(isolada.isError()).isTrue();
            assertThat(conteudoEstruturado(isolada))
                    .containsOnlyKeys("versaoDoContrato", "erro")
                    .containsEntry("versaoDoContrato", "1");
            assertThat(erro(isolada))
                    .containsOnlyKeys("codigo", "mensagem", "recuperavel",
                            "identificadorDaCorrelacao", "campos")
                    .containsEntry("codigo",
                            "OPERACAO_ASSISTIDA_NAO_ENCONTRADA")
                    .containsEntry("recuperavel", false);

            banco.update("""
                    UPDATE credenciais_de_integracao
                    SET escopos = 'planejamento:ler', versao = versao + 1
                    WHERE identificador = ?
                    """, integracaoA.identificadorDaCredencial());
            McpSchema.CallToolResult semEscopo = cliente.callTool(
                    chamada("obter_prioridades_atuais", Map.of()));
            assertThat(semEscopo.isError()).isTrue();
            assertThat(erro(semEscopo))
                    .containsEntry("codigo", "ESCOPO_INSUFICIENTE")
                    .containsEntry("recuperavel", false);
        }

        assertThat(banco.queryForObject("""
                SELECT count(*) FROM eventos_de_auditoria_da_automacao
                WHERE usuario_id = ? AND resultado = 'FALHA'
                  AND ferramenta IN (
                    'consultar_operacao_assistida',
                    'obter_prioridades_atuais')
                """, Integer.class, integracaoA.identificadorDoUsuario()))
                .isEqualTo(2);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM eventos_de_auditoria_da_automacao
                WHERE usuario_id = ? AND ferramenta IS NOT NULL
                """, Integer.class, integracaoB.identificadorDoUsuario()))
                .isZero();
    }

    @Test
    void deveIsolarOsDadosDoUsuarioBNasOitoFerramentas() throws Exception {
        IntegracaoCriada integracaoA = criarIntegracao(
                "isolamento-a", 72501L, TODOS_OS_ESCOPOS);
        IntegracaoCriada integracaoB = criarIntegracao(
                "isolamento-b", 72502L, TODOS_OS_ESCOPOS);
        CenarioDoUsuario cenarioA = criarCenarioDoUsuario(
                integracaoA, "exclusivo-a");
        CenarioDoUsuario cenarioB = criarCenarioDoUsuario(
                integracaoB, "exclusivo-b");
        var operacaoB = operacoes.preparar(
                integracaoB.identificadorDoUsuario(),
                integracaoB.identificadorDoVinculo(), "REGISTRAR_ESTUDO",
                "Operacao exclusivo-b", "{\"minutos\":45}",
                "{\"plano\":2}", "operacao-isolamento-exclusivo-b");

        try (McpSyncClient cliente = cliente(integracaoA)) {
            cliente.initialize();

            McpSchema.CallToolResult agenda = cliente.callTool(
                    chamada("obter_agenda_de_estudos_de_hoje", Map.of()));
            assertResultadoDoUsuarioA(agenda, "Bloco exclusivo-a", cenarioB,
                    operacaoB.identificador());

            McpSchema.CallToolResult revisoes = cliente.callTool(
                    chamada("obter_revisoes_devidas", Map.of()));
            assertResultadoDoUsuarioA(revisoes, "Topico exclusivo-a", cenarioB,
                    operacaoB.identificador());

            McpSchema.CallToolResult prioridades = cliente.callTool(
                    chamada("obter_prioridades_atuais", Map.of()));
            assertResultadoDoUsuarioA(prioridades, "Materia exclusivo-a", cenarioB,
                    operacaoB.identificador());

            McpSchema.CallToolResult progresso = cliente.callTool(
                    chamada("obter_progresso_do_concurso", Map.of()));
            assertResultadoDoUsuarioA(progresso, "Concurso exclusivo-a", cenarioB,
                    operacaoB.identificador());

            McpSchema.CallToolResult historico = cliente.callTool(chamada(
                    "obter_historico_recente", Map.of(
                            "quantidadeDeDias", 30, "limite", 20)));
            assertResultadoDoUsuarioA(historico, "Estudo exclusivo-a", cenarioB,
                    operacaoB.identificador());

            McpSchema.CallToolResult concursoAlheio = cliente.callTool(chamada(
                    "obter_estrutura_do_concurso", Map.of(
                            "identificadorDoConcurso",
                            cenarioB.identificadorDoConcurso().toString())));
            assertRecursoAlheioNaoEncontrado(concursoAlheio,
                    "CONCURSO_NAO_ENCONTRADO", cenarioB,
                    operacaoB.identificador());

            McpSchema.CallToolResult blocoAlheio = cliente.callTool(chamada(
                    "explicar_bloco_de_estudo", Map.of(
                            "identificadorDoBloco",
                            cenarioB.identificadorDoBloco().toString())));
            assertRecursoAlheioNaoEncontrado(blocoAlheio,
                    "BLOCO_DE_ESTUDO_NAO_ENCONTRADO", cenarioB,
                    operacaoB.identificador());

            McpSchema.CallToolResult operacaoAlheia = cliente.callTool(chamada(
                    "consultar_operacao_assistida", Map.of(
                            "identificadorDaOperacao",
                            operacaoB.identificador().toString())));
            assertRecursoAlheioNaoEncontrado(operacaoAlheia,
                    "OPERACAO_ASSISTIDA_NAO_ENCONTRADA", cenarioB,
                    operacaoB.identificador());
        }

        assertThat(banco.queryForObject("""
                SELECT count(*) FROM eventos_de_auditoria_da_automacao
                WHERE usuario_id = ? AND fonte = 'MCP'
                  AND ferramenta IS NOT NULL
                """, Integer.class, integracaoA.identificadorDoUsuario()))
                .isEqualTo(8);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM eventos_de_auditoria_da_automacao
                WHERE usuario_id = ? AND fonte = 'MCP'
                  AND ferramenta IS NOT NULL
                """, Integer.class, integracaoB.identificadorDoUsuario()))
                .isZero();
        assertThat(cenarioA.identificadorDoConcurso()).isNotNull();
    }

    @Test
    void deveRecusarTokenComAgenteOuSessaoDivergente() {
        IntegracaoCriada integracao = criarIntegracao(
                "identidade", 73001L, TODOS_OS_ESCOPOS);
        IntegracaoCriada divergente = new IntegracaoCriada(
                integracao.identificadorDoUsuario(),
                integracao.identificadorDoVinculo(),
                integracao.identificadorDaCredencial(), integracao.token(),
                "outro-agente", integracao.identificadorDaSessao());

        assertThatThrownBy(() -> {
            try (McpSyncClient cliente = cliente(divergente)) {
                cliente.initialize();
            }
        }).hasRootCauseInstanceOf(
                McpHttpClientTransportAuthorizationException.class);

        banco.update("""
                UPDATE credenciais_de_integracao
                SET revogado_em = now(), versao = versao + 1
                WHERE identificador = ?
                """, integracao.identificadorDaCredencial());
        assertThatThrownBy(() -> {
            try (McpSyncClient cliente = cliente(integracao)) {
                cliente.initialize();
            }
        }).hasRootCauseInstanceOf(
                McpHttpClientTransportAuthorizationException.class);

        assertThat(banco.queryForObject("""
                SELECT ultimo_uso_em IS NULL
                FROM credenciais_de_integracao
                WHERE identificador = ?
                """, Boolean.class, integracao.identificadorDaCredencial()))
                .isTrue();
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM eventos_de_auditoria_da_automacao
                WHERE usuario_id = ? AND fonte = 'MCP'
                """, Integer.class, integracao.identificadorDoUsuario()))
                .isZero();
    }

    private McpSyncClient cliente(IntegracaoCriada integracao) {
        var transporte = HttpClientStreamableHttpTransport.builder(
                        "http://localhost:" + porta)
                .openConnectionOnStartup(false)
                .httpRequestCustomizer((pedido, metodo, destino, corpo,
                        contexto) -> pedido
                        .header("Authorization", "Bearer " + integracao.token())
                        .header(FiltroDeCredencialMcp.CABECALHO_DO_AGENTE,
                                integracao.identificadorDoAgente())
                        .header(FiltroDeCredencialMcp.CABECALHO_DA_SESSAO,
                                integracao.identificadorDaSessao()))
                .build();
        return McpClient.sync(transporte)
                .initializationTimeout(Duration.ofSeconds(10))
                .requestTimeout(Duration.ofSeconds(10))
                .build();
    }

    private McpSchema.CallToolRequest chamada(String nome,
            Map<String, Object> argumentos) {
        return McpSchema.CallToolRequest.builder()
                .name(nome).arguments(argumentos).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> conteudoEstruturado(
            McpSchema.CallToolResult resultado) {
        assertThat(resultado.structuredContent()).isInstanceOf(Map.class);
        return (Map<String, Object>) resultado.structuredContent();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> erro(McpSchema.CallToolResult resultado) {
        Object valor = conteudoEstruturado(resultado).get("erro");
        assertThat(valor).isInstanceOf(Map.class);
        return (Map<String, Object>) valor;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> ramosDaSaida(McpSchema.Tool ferramenta) {
        return (List<Map<String, Object>>) ferramenta.outputSchema().get("oneOf");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> esquemaDosDados(McpSchema.Tool ferramenta) {
        Map<String, Object> sucesso = ramosDaSaida(ferramenta).getFirst();
        Map<String, Object> propriedades =
                (Map<String, Object>) sucesso.get("properties");
        return (Map<String, Object>) propriedades.get("dados");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> propriedades(Map<String, Object> esquema) {
        return (Map<String, Object>) esquema.get("properties");
    }

    private void assertResultadoDoUsuarioA(McpSchema.CallToolResult resultado,
            String dadoEsperado, CenarioDoUsuario cenarioB, UUID operacaoB) {
        assertThat(resultado.isError()).as("resultado MCP: %s", resultado)
                .isFalse();
        String conteudo = conteudoEstruturado(resultado).toString();
        assertThat(conteudo).contains(dadoEsperado);
        assertSemDadosDoUsuarioB(conteudo, cenarioB, operacaoB);
    }

    private void assertRecursoAlheioNaoEncontrado(
            McpSchema.CallToolResult resultado, String codigo,
            CenarioDoUsuario cenarioB, UUID operacaoB) {
        assertThat(resultado.isError()).isTrue();
        assertThat(erro(resultado)).containsEntry("codigo", codigo);
        assertSemDadosDoUsuarioB(conteudoEstruturado(resultado).toString(),
                cenarioB, operacaoB);
    }

    private void assertSemDadosDoUsuarioB(String conteudo,
            CenarioDoUsuario cenarioB, UUID operacaoB) {
        assertThat(conteudo)
                .doesNotContain("exclusivo-b")
                .doesNotContain(cenarioB.identificadorDoConcurso().toString())
                .doesNotContain(cenarioB.identificadorDaMateria().toString())
                .doesNotContain(cenarioB.identificadorDoTopico().toString())
                .doesNotContain(cenarioB.identificadorDoBloco().toString())
                .doesNotContain(operacaoB.toString());
    }

    private CenarioDoUsuario criarCenarioDoUsuario(
            IntegracaoCriada integracao, String marcador) {
        OffsetDateTime agora = OffsetDateTime.now(
                ZoneId.of("America/Sao_Paulo"));
        LocalDate hoje = agora.toLocalDate();
        UUID materia = UUID.randomUUID();
        UUID topico = UUID.randomUUID();
        UUID concurso = UUID.randomUUID();
        UUID cargo = UUID.randomUUID();
        UUID prova = UUID.randomUUID();
        UUID grupo = UUID.randomUUID();
        UUID materiaDaProva = UUID.randomUUID();
        UUID edital = UUID.randomUUID();
        UUID itemDoEdital = UUID.randomUUID();
        UUID registro = UUID.randomUUID();
        UUID plano = UUID.randomUUID();
        UUID bloco = UUID.randomUUID();

        banco.update("""
                INSERT INTO materias (identificador, usuario_id, nome,
                    nome_normalizado, arquivada, criado_em, atualizado_em,
                    versao)
                VALUES (?, ?, ?, ?, FALSE, ?, ?, 0)
                """, materia, integracao.identificadorDoUsuario(),
                "Materia " + marcador, "materia " + marcador, agora, agora);
        banco.update("""
                INSERT INTO topicos_da_materia (identificador, materia_id,
                    nome, nome_normalizado, ordem, arquivado, criado_em,
                    atualizado_em, versao)
                VALUES (?, ?, ?, ?, 1, FALSE, ?, ?, 0)
                """, topico, materia, "Topico " + marcador,
                "topico " + marcador, agora, agora);
        banco.update("""
                INSERT INTO concursos (identificador, usuario_id, nome,
                    nome_normalizado, situacao, ativo, criado_em,
                    atualizado_em, versao)
                VALUES (?, ?, ?, ?, 'EM_ANDAMENTO', TRUE, ?, ?, 0)
                """, concurso, integracao.identificadorDoUsuario(),
                "Concurso " + marcador, "concurso " + marcador, agora, agora);
        banco.update("""
                INSERT INTO cargos_do_concurso (identificador, concurso_id,
                    nome, nome_normalizado, nivel_de_escolaridade, selecionado,
                    ordem, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, 'SUPERIOR', TRUE, 1, ?, ?, 0)
                """, cargo, concurso, "Cargo " + marcador,
                "cargo " + marcador, agora, agora);
        banco.update("""
                INSERT INTO provas (identificador, cargo_id, nome,
                    nome_normalizado, tipo, carater, ordem, criado_em,
                    atualizado_em, versao)
                VALUES (?, ?, ?, ?, 'OBJETIVA', 'CLASSIFICATORIO', 1,
                    ?, ?, 0)
                """, prova, cargo, "Prova " + marcador,
                "prova " + marcador, agora, agora);
        banco.update("""
                INSERT INTO grupos_de_conteudo (identificador, prova_id,
                    nome, nome_normalizado, ordem, criado_em, atualizado_em,
                    versao)
                VALUES (?, ?, ?, ?, 1, ?, ?, 0)
                """, grupo, prova, "Grupo " + marcador,
                "grupo " + marcador, agora, agora);
        banco.update("""
                INSERT INTO materias_da_prova (identificador,
                    grupo_de_conteudo_id, materia_id, ordem, criado_em,
                    atualizado_em, versao)
                VALUES (?, ?, ?, 1, ?, ?, 0)
                """, materiaDaProva, grupo, materia, agora, agora);
        banco.update("""
                INSERT INTO editais (identificador, concurso_id, titulo,
                    principal, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, TRUE, ?, ?, 0)
                """, edital, concurso, "Edital " + marcador, agora, agora);
        banco.update("""
                INSERT INTO itens_do_edital (identificador, edital_id,
                    materia_da_prova_id, descricao_original, ordem, criado_em,
                    atualizado_em, versao)
                VALUES (?, ?, ?, ?, 1, ?, ?, 0)
                """, itemDoEdital, edital, materiaDaProva,
                "Item " + marcador, agora, agora);
        banco.update("""
                INSERT INTO mapeamentos_de_itens_do_edital (identificador,
                    item_do_edital_id, topico_da_materia_id, confirmado,
                    criado_em)
                VALUES (?, ?, ?, TRUE, ?)
                """, UUID.randomUUID(), itemDoEdital, topico, agora);
        banco.update("""
                INSERT INTO registros_de_estudo (identificador, topico_id,
                    data_hora, duracao_em_minutos, observacao, situacao,
                    tipo_de_estudo, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, 45, ?, 'ATIVO', 'REVISAO', ?, ?, 0)
                """, registro, topico, agora.minusHours(1),
                "Estudo " + marcador, agora.minusHours(1), agora.minusHours(1));
        banco.update("""
                INSERT INTO evidencias_de_aprendizagem (identificador,
                    registro_de_estudo_id, nivel_de_recordacao,
                    dificuldade_percebida, criado_em, atualizado_em, versao)
                VALUES (?, ?, 4, 2, ?, ?, 0)
                """, UUID.randomUUID(), registro, agora.minusHours(1),
                agora.minusHours(1));

        LocalDate inicioDaSemana = hoje.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        banco.update("""
                INSERT INTO planos_semanais (identificador, usuario_id,
                    data_inicial, estado, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, 'ATIVO', ?, ?, 0)
                """, plano, integracao.identificadorDoUsuario(),
                inicioDaSemana, agora, agora);
        banco.update("""
                INSERT INTO disponibilidades_do_dia (identificador, plano_id,
                    data, minutos_disponiveis, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, 120, ?, ?, 0)
                """, UUID.randomUUID(), plano, hoje, agora, agora);
        banco.update("""
                INSERT INTO blocos_de_estudo (identificador, plano_id,
                    materia_id, topico_id, titulo, tipo_de_atividade, data,
                    duracao_prevista_em_minutos, ordem, estado, criado_em,
                    atualizado_em, versao)
                VALUES (?, ?, ?, ?, ?, 'QUESTOES', ?, 45, 1, 'PLANEJADO',
                    ?, ?, 0)
                """, bloco, plano, materia, topico, "Bloco " + marcador,
                hoje, agora, agora);
        return new CenarioDoUsuario(concurso, materia, topico, bloco);
    }

    private IntegracaoCriada criarIntegracao(String nome, long telegram,
            String escopos) {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        UUID usuario = UUID.randomUUID();
        UUID vinculo = UUID.randomUUID();
        UUID credencial = UUID.randomUUID();
        String token = "mcp_" + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        String agente = "agente-openclaw-" + nome;
        String sessao = "sessao-openclaw-" + nome;

        banco.update("""
                INSERT INTO usuarios (
                    identificador, nome, email, senha_hash, situacao,
                    criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, 'ATIVO', ?, ?, 0)
                """, usuario, "Usuario " + nome, nome + "@example.com",
                "hash-de-senha-do-teste", agora.minusHours(2),
                agora.minusHours(2));
        banco.update("""
                INSERT INTO vinculos_de_canal (
                    identificador, usuario_id, canal, bot,
                    identificador_externo, identificador_do_chat, estado,
                    codigo_de_vinculo_hash, codigo_expira_em,
                    codigo_consumido_em, identificador_do_agente,
                    identificador_da_sessao, provisionado_em, criado_em,
                    atualizado_em, revogado_em, versao)
                VALUES (?, ?, 'TELEGRAM', 70001, ?, ?, 'ATIVO', ?, ?, ?,
                    ?, ?, ?, ?, ?, NULL, 0)
                """, vinculo, usuario, telegram, telegram,
                segredos.hash("codigo-" + vinculo), agora.plusHours(1),
                agora.minusHours(1), agente, sessao, agora.minusMinutes(30),
                agora.minusHours(2), agora);
        banco.update("""
                INSERT INTO credenciais_de_integracao (
                    identificador, vinculo_id, token_hash, prefixo, escopos,
                    expira_em, ultimo_uso_em, revogado_em, criado_em, versao)
                VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, ?, 0)
                """, credencial, vinculo, segredos.hash(token),
                token.substring(0, 16), escopos, agora.plusDays(30),
                agora.minusMinutes(30));
        return new IntegracaoCriada(usuario, vinculo, credencial, token,
                agente, sessao);
    }

    private record IntegracaoCriada(UUID identificadorDoUsuario,
            UUID identificadorDoVinculo, UUID identificadorDaCredencial,
            String token, String identificadorDoAgente,
            String identificadorDaSessao) {
    }

    private record CenarioDoUsuario(UUID identificadorDoConcurso,
            UUID identificadorDaMateria, UUID identificadorDoTopico,
            UUID identificadorDoBloco) {
    }
}
