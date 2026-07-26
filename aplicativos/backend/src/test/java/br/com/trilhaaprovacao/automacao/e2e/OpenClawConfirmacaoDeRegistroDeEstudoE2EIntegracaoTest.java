package br.com.trilhaaprovacao.automacao.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.trilhaaprovacao.automacao.infraestrutura.ServicoDeSegredosDaAutomacao;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "debug=false",
            "trilha.automacao.habilitada=true",
            "trilha.automacao.identificador-do-bot=700000777",
            "trilha.automacao.segredo-de-hash="
                    + "segredo-exclusivo-do-e2e-openclaw-123456789",
            "trilha.automacao.identificador-da-chave-do-gateway="
                    + "gateway-openclaw-e2e",
            "trilha.automacao.segredo-do-gateway="
                    + "segredo-do-gateway-e2e-openclaw-123456789",
            "trilha.automacao.limite-do-gateway-por-minuto=500",
            "trilha.automacao.mcp.hosts-permitidos=localhost:*",
            "trilha.automacao.mcp.origens-permitidas=http://localhost"
        })
class OpenClawConfirmacaoDeRegistroDeEstudoE2EIntegracaoTest {
    private static final long IDENTIFICADOR_DO_BOT = 700000777L;
    private static final long IDENTIFICADOR_DO_TELEGRAM = 800000111L;
    private static final long IDENTIFICADOR_DO_CHAT = 900000222L;
    private static final String IDENTIFICADOR_DA_CONTA = "conta-e2e";
    private static final String IDENTIFICADOR_DA_CHAVE =
            "gateway-openclaw-e2e";
    private static final String SEGREDO_DO_GATEWAY =
            "segredo-do-gateway-e2e-openclaw-123456789";
    private static final String OBSERVACAO =
            "Estudei 40 minutos de TLS e fiz 20 questões, 16 acertos.";
    private static final Pattern CODIGO_DE_CONFIRMACAO =
            Pattern.compile("^[23456789A-HJ-NP-Z]{8}$");

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("trilha_openclaw_e2e")
                    .withUsername("teste")
                    .withPassword("teste");

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        propriedades.add("spring.datasource.username", POSTGRESQL::getUsername);
        propriedades.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired JdbcTemplate banco;
    @Autowired ServicoDeSegredosDaAutomacao segredos;
    @Autowired ObjectMapper json;
    @LocalServerPort int portaDoBackend;
    @TempDir Path diretorioTemporario;

    @BeforeEach
    void limparBanco() {
        banco.execute("""
                TRUNCATE TABLE requisicoes_confiaveis_da_automacao,
                    eventos_de_auditoria_da_automacao,
                    operacoes_assistidas, credenciais_de_integracao,
                    vinculos_de_canal, usuarios CASCADE
                """);
    }

    @Test
    @Timeout(120)
    void devePercorrerMcpPluginIntegradorGatewayEHistoricoSemDuplicar()
            throws Exception {
        assertThat(IDENTIFICADOR_DO_CHAT)
                .isNotEqualTo(IDENTIFICADOR_DO_TELEGRAM);
        Cenario cenario = criarCenario();
        ProcessoDoIntegrador integrador = null;
        ProcessoDoBroker broker = null;
        try {
            prepararEstadoLocal(cenario);
            broker = iniciarBroker();
            aguardarServico(broker.processo(), broker.url(),
                    "Broker de credenciais");
            integrador = iniciarIntegrador();
            aguardarServico(integrador.processo(), integrador.url(),
                    "Integrador");

            try (McpSyncClient cliente = clienteMcp(cenario, broker.url())) {
                cliente.initialize();
                Preparacao preparacao = prepararRegistro(cliente, cenario);
                assertThat(banco.queryForObject("""
                        SELECT count(*) FROM registros_de_estudo
                         WHERE topico_id = ?
                        """, Integer.class, cenario.topico())).isZero();

                JsonNode primeira = executarHook(
                        integrador.url(), preparacao.codigo(),
                        "update-openclaw-e2e-1");
                assertReciboDoPlugin(primeira, preparacao.operacao());
                assertPersistenciaExata(cenario, preparacao.operacao());

                JsonNode repetida = executarHook(
                        integrador.url(), preparacao.codigo(),
                        "update-openclaw-e2e-2");
                assertReciboDoPlugin(repetida, preparacao.operacao());
                assertPersistenciaExata(cenario, preparacao.operacao());

                assertHistoricoMcp(cliente, cenario);
            }
        } finally {
            encerrar(integrador);
            encerrar(broker);
            limparTemporarios();
        }
    }

    private Preparacao prepararRegistro(McpSyncClient cliente,
            Cenario cenario) {
        OffsetDateTime instante = OffsetDateTime.now(ZoneOffset.UTC)
                .minusMinutes(2).withNano(0);
        Map<String, Object> evidencia = new LinkedHashMap<>();
        evidencia.put("quantidadeDeQuestoes", 20);
        evidencia.put("quantidadeDeAcertos", 16);
        evidencia.put("dificuldadePercebida", 3);
        Map<String, Object> argumentos = new LinkedHashMap<>();
        argumentos.put("identificadorDoTopico", cenario.topico().toString());
        argumentos.put("identificadorDoMaterial",
                cenario.material().toString());
        argumentos.put("dataHora", instante.toString());
        argumentos.put("duracaoEmMinutos", 40);
        argumentos.put("observacao", OBSERVACAO);
        argumentos.put("tipoDeEstudo", "QUESTOES");
        argumentos.put("evidencia", evidencia);

        McpSchema.CallToolResult resultado = cliente.callTool(
                McpSchema.CallToolRequest.builder()
                        .name("preparar_registro_de_estudo")
                        .arguments(argumentos).build());
        assertThat(resultado.isError()).isFalse();
        Map<String, Object> dados = mapa(
                mapa(resultado.structuredContent()).get("dados"));
        String codigo = String.valueOf(dados.get("codigoDeConfirmacao"));
        if (!CODIGO_DE_CONFIRMACAO.matcher(codigo).matches()) {
            throw new AssertionError(
                    "Preparacao MCP nao gerou codigo de confirmacao valido.");
        }
        UUID operacao = UUID.fromString(
                String.valueOf(dados.get("identificadorDaOperacao")));
        assertThat(dados.get("tipo")).isEqualTo("REGISTRO_DE_ESTUDO");
        assertThat(dados.get("estado"))
                .isEqualTo("AGUARDANDO_CONFIRMACAO");
        return new Preparacao(operacao, codigo);
    }

    private void assertHistoricoMcp(McpSyncClient cliente, Cenario cenario) {
        McpSchema.CallToolResult resultado = cliente.callTool(
                McpSchema.CallToolRequest.builder()
                        .name("obter_historico_recente")
                        .arguments(Map.of(
                                "quantidadeDeDias", 30,
                                "limite", 20))
                        .build());
        assertThat(resultado.isError()).isFalse();
        Map<String, Object> dados = mapa(
                mapa(resultado.structuredContent()).get("dados"));
        List<Map<String, Object>> estudos = listaDeMapas(dados.get("estudos"));
        assertThat(estudos).hasSize(1);
        Map<String, Object> estudo = estudos.getFirst();
        assertThat(estudo.get("identificadorDoTopico"))
                .isEqualTo(cenario.topico().toString());
        assertThat(estudo.get("topico")).isEqualTo("TLS");
        assertThat(estudo.get("material")).isEqualTo("PDF TLS");
        assertThat(estudo.get("duracaoEmMinutos")).isEqualTo(40);
        assertThat(estudo.get("tipoDeEstudo")).isEqualTo("QUESTOES");
        assertThat(estudo.get("observacao")).isEqualTo(OBSERVACAO);
        assertThat(estudo.get("quantidadeDeQuestoes")).isEqualTo(20);
        assertThat(estudo.get("quantidadeDeAcertos")).isEqualTo(16);
        Map<String, Object> totais = mapa(dados.get("totais"));
        assertThat(numero(totais.get("quantidadeDeEstudos"))).isEqualTo(1);
        assertThat(numero(totais.get("minutos"))).isEqualTo(40);
        assertThat(numero(totais.get("questoes"))).isEqualTo(20);
        assertThat(numero(totais.get("acertos"))).isEqualTo(16);
    }

    private void assertPersistenciaExata(Cenario cenario, UUID operacao) {
        assertThat(banco.queryForObject("""
                SELECT estado FROM operacoes_assistidas
                 WHERE identificador = ?
                """, String.class, operacao)).isEqualTo("APLICADA");
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM registros_de_estudo
                 WHERE topico_id = ? AND material_id = ?
                   AND duracao_em_minutos = 40
                   AND tipo_de_estudo = 'QUESTOES'
                   AND observacao = ?
                """, Integer.class, cenario.topico(), cenario.material(),
                OBSERVACAO)).isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT count(*)
                  FROM evidencias_de_aprendizagem e
                  JOIN registros_de_estudo r
                    ON r.identificador = e.registro_de_estudo_id
                 WHERE r.topico_id = ?
                   AND e.quantidade_de_questoes = 20
                   AND e.quantidade_de_acertos = 16
                """, Integer.class, cenario.topico())).isEqualTo(1);
    }

    private JsonNode executarHook(String urlDoIntegrador, String codigo,
            String identificadorDoUpdate) throws Exception {
        Path harness = diretorioDoOpenClaw().resolve(
                "testes/executar-confirmacao-plugin-e2e.mjs");
        Process processo = new ProcessBuilder("node", harness.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        Map<String, Object> entrada = new LinkedHashMap<>();
        entrada.put("urlDoIntegrador", urlDoIntegrador);
        entrada.put("identificadorDaContaDoBot", IDENTIFICADOR_DA_CONTA);
        entrada.put("texto", "CONFIRMAR " + codigo);
        entrada.put("identificadorDoTelegram",
                String.valueOf(IDENTIFICADOR_DO_TELEGRAM));
        entrada.put("identificadorDoChat",
                String.valueOf(IDENTIFICADOR_DO_CHAT));
        entrada.put("identificadorDoUpdate", identificadorDoUpdate);
        try (var destino = processo.getOutputStream()) {
            json.writeValue(destino, entrada);
        }
        boolean terminou = processo.waitFor(20, TimeUnit.SECONDS);
        if (!terminou) {
            processo.destroyForcibly();
            throw new AssertionError(
                    "Harness E2E do plugin excedeu tempo limite.");
        }
        byte[] saida = processo.getInputStream().readAllBytes();
        if (processo.exitValue() != 0) {
            throw new AssertionError("Harness E2E do plugin falhou.");
        }
        return json.readTree(saida);
    }

    private void assertReciboDoPlugin(JsonNode resposta, UUID operacao) {
        assertThat(resposta.get("handled").asBoolean()).isTrue();
        assertThat(resposta.get("text").asText()).isEqualTo(
                "Operacao confirmada e aplicada na Trilha. Recibo: "
                        + operacao);
    }

    private void prepararEstadoLocal(Cenario cenario) throws Exception {
        executarComando(List.of("node", "--version"), Duration.ofSeconds(10));
        permissao(diretorioTemporario, "rwx------");
        Path estado = diretorioTemporario.resolve("estado");
        Path credenciais = diretorioTemporario.resolve("credenciais-mcp");
        Path scripts = diretorioDoOpenClaw().resolve("scripts");
        executarComando(List.of(
                "bash", scripts.resolve("inicializar-estado.sh").toString(),
                "--diretorio-estado", estado.toString(),
                "--diretorio-credenciais-mcp", credenciais.toString()),
                Duration.ofSeconds(30));

        Path bot = criarArquivoSecreto(
                "identificador-bot", String.valueOf(IDENTIFICADOR_DO_BOT));
        Path segredo = criarArquivoSecreto(
                "segredo-gateway", SEGREDO_DO_GATEWAY);
        Path token = criarArquivoSecreto("token-mcp", cenario.token());
        executarComando(List.of(
                "bash", scripts.resolve("provisionar-vinculo.sh").toString(),
                "--diretorio-estado", estado.toString(),
                "--diretorio-credenciais-mcp", credenciais.toString(),
                "--identificador-vinculo", cenario.vinculo().toString(),
                "--identificador-bot", String.valueOf(IDENTIFICADOR_DO_BOT),
                "--identificador-conta-bot", IDENTIFICADOR_DA_CONTA,
                "--identificador-telegram",
                String.valueOf(IDENTIFICADOR_DO_TELEGRAM),
                "--identificador-chat",
                String.valueOf(IDENTIFICADOR_DO_CHAT),
                "--identificador-agente", cenario.agente(),
                "--identificador-sessao", cenario.sessao(),
                "--token-mcp-arquivo", token.toString(),
                "--url-mcp", "http://localhost:" + portaDoBackend + "/mcp"),
                Duration.ofSeconds(60));
        String nomeDoPlugin = "trilha-mcp-"
                + cenario.vinculo().toString().replace("-", "");
        Path diretorioDoPlugin = estado.resolve("workspaces")
                .resolve(cenario.agente()).resolve(".openclaw/extensions")
                .resolve(nomeDoPlugin);
        Path arquivoMcp = diretorioDoPlugin.resolve(".mcp.json");
        String conteudoMcp = Files.readString(arquivoMcp);
        JsonNode mcp = json.readTree(conteudoMcp);
        assertThat(mcp.at("/mcpServers/trilha/command").asText())
                .isEqualTo("node");
        assertThat(mcp.at("/mcpServers/trilha/args/0").asText())
                .isEqualTo("./proxy-mcp-http-stdio.mjs");
        assertThat(mcp.at("/mcpServers/trilha/args/1").asText())
                .isEqualTo("http://broker-credenciais:18890/mcp/"
                        + cenario.vinculo());
        JsonNode allowlist =
                mcp.at("/mcpServers/trilha/toolFilter/include");
        assertThat(allowlist.isArray()).isTrue();
        List<String> ferramentas = new ArrayList<>();
        allowlist.forEach(item -> ferramentas.add(item.asText()));
        assertThat(ferramentas).hasSize(25).doesNotHaveDuplicates()
                .contains("preparar_registro_de_estudo",
                        "consultar_operacao_assistida",
                        "preparar_importacao_completa_do_edital")
                .doesNotContain("executar_shell", "filesystem");
        assertThat(mcp.at("/mcpServers/trilha/url").isMissingNode()).isTrue();
        assertThat(mcp.at("/mcpServers/trilha/headers").isMissingNode())
                .isTrue();
        assertThat(conteudoMcp).doesNotContain(
                cenario.token(), "Authorization", "Bearer ");

        Path proxy = diretorioDoPlugin.resolve(
                "proxy-mcp-http-stdio.mjs");
        assertThat(Files.isRegularFile(proxy)).isTrue();
        assertThat(Files.isSymbolicLink(proxy)).isFalse();
        assertThat(PosixFilePermissions.toString(
                Files.getPosixFilePermissions(proxy)))
                .isEqualTo("r-x------");
        assertThat(PosixFilePermissions.toString(
                Files.getPosixFilePermissions(arquivoMcp)))
                .isEqualTo("rw-------");

        String conteudoDaConfiguracao = Files.readString(
                estado.resolve("openclaw.json"));
        JsonNode configuracao = json.readTree(conteudoDaConfiguracao);
        List<String> pluginsPermitidos = new ArrayList<>();
        configuracao.at("/plugins/allow")
                .forEach(item -> pluginsPermitidos.add(item.asText()));
        assertThat(pluginsPermitidos)
                .contains(nomeDoPlugin, "codex", "trilha-aprovacao");
        assertThat(configuracao.at("/mcp/servers").isObject()).isTrue();
        assertThat(configuracao.at("/mcp/servers").isEmpty()).isTrue();
        assertThat(conteudoDaConfiguracao).doesNotContain(cenario.token());
        assertThat(diretorioContem(estado, cenario.token())).isFalse();
        executarComando(List.of(
                "bash",
                scripts.resolve("registrar-provisionamento.sh").toString(),
                "--diretorio-estado", estado.toString(),
                "--identificador-vinculo", cenario.vinculo().toString(),
                "--url-backend", "http://127.0.0.1:" + portaDoBackend,
                "--identificador-chave", IDENTIFICADOR_DA_CHAVE,
                "--segredo-gateway-arquivo", segredo.toString()),
                Duration.ofSeconds(30));
        assertThat(Files.isRegularFile(bot)).isTrue();
    }

    private boolean diretorioContem(Path diretorio, String conteudo)
            throws IOException {
        try (var caminhos = Files.walk(diretorio)) {
            for (Path arquivo : caminhos
                    .filter(Files::isRegularFile).toList()) {
                if (Files.readString(arquivo).contains(conteudo)) {
                    return true;
                }
            }
        }
        return false;
    }

    private ProcessoDoIntegrador iniciarIntegrador() throws Exception {
        int porta = portaLivre();
        Path scripts = diretorioDoOpenClaw().resolve("scripts");
        Path estado = diretorioTemporario.resolve("estado");
        Path credenciais = diretorioTemporario.resolve("credenciais-mcp");
        ProcessBuilder construtor = new ProcessBuilder(
                "node",
                scripts.resolve("integrador-de-vinculos.mjs").toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        Map<String, String> ambiente = construtor.environment();
        ambiente.put("OPENCLAW_DIRETORIO_ESTADO", estado.toString());
        ambiente.put("OPENCLAW_DIRETORIO_CREDENCIAIS_MCP",
                credenciais.toString());
        ambiente.put("OPENCLAW_ARQUIVO_IDENTIFICADOR_BOT",
                diretorioTemporario.resolve("identificador-bot").toString());
        ambiente.put("OPENCLAW_ARQUIVO_SEGREDO_GATEWAY",
                diretorioTemporario.resolve("segredo-gateway").toString());
        ambiente.put("OPENCLAW_DIRETORIO_SCRIPTS", scripts.toString());
        ambiente.put("URL_DO_BACKEND_DA_TRILHA",
                "http://127.0.0.1:" + portaDoBackend);
        ambiente.put("URL_MCP_DA_TRILHA",
                "http://localhost:" + portaDoBackend + "/mcp");
        ambiente.put("IDENTIFICADOR_DA_CHAVE_DO_GATEWAY_OPENCLAW",
                IDENTIFICADOR_DA_CHAVE);
        ambiente.put("IDENTIFICADOR_DA_CONTA_DO_BOT_OPENCLAW",
                IDENTIFICADOR_DA_CONTA);
        ambiente.put("MODELO_OPENAI_DO_ASSISTENTE", "openai/gpt-5.5");
        ambiente.put("PORTA_DO_INTEGRADOR", String.valueOf(porta));
        ambiente.put("TEMPO_LIMITE_DO_BACKEND_EM_MS", "10000");
        ambiente.put("TEMPO_LIMITE_DOS_SCRIPTS_EM_MS", "30000");
        Process processo = construtor.start();
        return new ProcessoDoIntegrador(
                processo, "http://127.0.0.1:" + porta);
    }

    private ProcessoDoBroker iniciarBroker() throws IOException {
        int porta = portaLivre();
        Path script = diretorioDoOpenClaw().resolve(
                "scripts/broker-de-credenciais-mcp.mjs");
        Process processo = new ProcessBuilder(
                "node", script.toString(),
                diretorioTemporario.resolve("credenciais-mcp").toString(),
                String.valueOf(porta))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        return new ProcessoDoBroker(
                processo, "http://127.0.0.1:" + porta);
    }

    private void aguardarServico(Process processo, String url, String nome)
            throws Exception {
        HttpClient cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1)).build();
        URI destino = URI.create(url + "/healthz");
        long limite = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < limite) {
            if (!processo.isAlive()) {
                throw new AssertionError(
                        nome + " encerrou durante inicializacao.");
            }
            try {
                HttpResponse<Void> resposta = cliente.send(
                        HttpRequest.newBuilder(destino)
                                .timeout(Duration.ofSeconds(1)).GET().build(),
                        HttpResponse.BodyHandlers.discarding());
                if (resposta.statusCode() == 200) {
                    return;
                }
            } catch (IOException excecao) {
                // Servidor ainda inicializando.
            }
            Thread.sleep(100);
        }
        throw new AssertionError(nome + " nao ficou pronto.");
    }

    private McpSyncClient clienteMcp(Cenario cenario, String urlDoBroker) {
        var transporte = HttpClientStreamableHttpTransport.builder(
                        urlDoBroker)
                .endpoint("/mcp/" + cenario.vinculo())
                .openConnectionOnStartup(false)
                .build();
        return McpClient.sync(transporte)
                .initializationTimeout(Duration.ofSeconds(10))
                .requestTimeout(Duration.ofSeconds(10))
                .build();
    }

    private Cenario criarCenario() {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC)
                .withNano(0);
        UUID usuario = UUID.randomUUID();
        UUID vinculo = UUID.randomUUID();
        UUID credencial = UUID.randomUUID();
        UUID materia = UUID.randomUUID();
        UUID topico = UUID.randomUUID();
        UUID material = UUID.randomUUID();
        String token = gerarTokenMcp();
        String agente = "agente-openclaw-e2e";
        String sessao = "sessao-openclaw-e2e";

        banco.update("""
                INSERT INTO usuarios (
                    identificador, nome, email, senha_hash, situacao,
                    criado_em, atualizado_em, versao)
                VALUES (?, 'Usuario E2E', ?, 'hash-do-teste', 'ATIVO',
                    ?, ?, 0)
                """, usuario, "openclaw-e2e-" + usuario + "@example.com",
                agora.minusHours(2), agora.minusHours(2));
        banco.update("""
                INSERT INTO vinculos_de_canal (
                    identificador, usuario_id, canal, bot,
                    identificador_externo, identificador_do_chat, estado,
                    codigo_de_vinculo_hash, codigo_expira_em,
                    codigo_consumido_em, identificador_do_agente,
                    identificador_da_sessao, provisionado_em, criado_em,
                    atualizado_em, revogado_em, versao)
                VALUES (?, ?, 'TELEGRAM', ?, ?, ?, 'ATIVO', ?, ?,
                    ?, NULL, NULL, NULL, ?, ?, NULL, 0)
                """, vinculo, usuario, IDENTIFICADOR_DO_BOT,
                IDENTIFICADOR_DO_TELEGRAM, IDENTIFICADOR_DO_CHAT,
                segredos.hash("codigo-openclaw-e2e"),
                agora.plusHours(1), agora.minusHours(1),
                agora.minusHours(2), agora.minusHours(1));
        banco.update("""
                INSERT INTO credenciais_de_integracao (
                    identificador, vinculo_id, token_hash, prefixo, escopos,
                    expira_em, ultimo_uso_em, revogado_em, criado_em, versao)
                VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, ?, 0)
                """, credencial, vinculo, segredos.hash(token),
                token.substring(0, 16),
                "planejamento:ler prioridades:ler concursos:ler estudos:ler "
                        + "operacoes:ler operacoes:preparar",
                agora.plusDays(30), agora.minusHours(1));
        banco.update("""
                INSERT INTO materias (
                    identificador, usuario_id, nome, nome_normalizado,
                    descricao, cor, arquivada, criado_em, atualizado_em,
                    versao)
                VALUES (?, ?, 'Seguranca de Redes', 'seguranca de redes',
                    NULL, NULL, FALSE, ?, ?, 0)
                """, materia, usuario, agora.minusHours(1),
                agora.minusHours(1));
        banco.update("""
                INSERT INTO topicos_da_materia (
                    identificador, materia_id, topico_pai_id, nome,
                    nome_normalizado, descricao, ordem, arquivado,
                    criado_em, atualizado_em, versao)
                VALUES (?, ?, NULL, 'TLS', 'tls', NULL, 1, FALSE, ?, ?, 0)
                """, topico, materia, agora.minusHours(1),
                agora.minusHours(1));
        banco.update("""
                INSERT INTO materiais_de_estudo (
                    identificador, usuario_id, titulo, tipo, descricao,
                    fonte, endereco, duracao_estimada_em_minutos, arquivado,
                    criado_em, atualizado_em, versao)
                VALUES (?, ?, 'PDF TLS', 'PDF', NULL, NULL, NULL, 60, FALSE,
                    ?, ?, 0)
                """, material, usuario, agora.minusHours(1),
                agora.minusHours(1));
        banco.update("""
                INSERT INTO coberturas_de_topicos_por_material (
                    identificador, material_id, topico_id, criado_em)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), material, topico,
                agora.minusHours(1));
        return new Cenario(usuario, vinculo, credencial, token, topico,
                material, agente, sessao);
    }

    private Path criarArquivoSecreto(String nome, String conteudo)
            throws IOException {
        Path arquivo = diretorioTemporario.resolve(nome);
        Files.writeString(arquivo, conteudo + "\n", StandardCharsets.UTF_8);
        permissao(arquivo, "rw-------");
        return arquivo;
    }

    private String gerarTokenMcp() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return "mcp_" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bytes);
    }

    private void executarComando(List<String> comando, Duration limite)
            throws Exception {
        Process processo = new ProcessBuilder(new ArrayList<>(comando))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        boolean terminou = processo.waitFor(
                limite.toMillis(), TimeUnit.MILLISECONDS);
        if (!terminou) {
            processo.destroyForcibly();
            throw new AssertionError("Subprocesso E2E excedeu tempo limite.");
        }
        if (processo.exitValue() != 0) {
            throw new AssertionError("Subprocesso E2E falhou.");
        }
    }

    private Path diretorioDoOpenClaw() {
        return Path.of("..", "..", "infraestrutura", "openclaw")
                .toAbsolutePath().normalize();
    }

    private int portaLivre() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private void permissao(Path caminho, String permissoes)
            throws IOException {
        Files.setPosixFilePermissions(
                caminho, PosixFilePermissions.fromString(permissoes));
    }

    private void encerrar(ProcessoDoIntegrador integrador)
            throws InterruptedException {
        encerrar(integrador == null ? null : integrador.processo());
    }

    private void encerrar(ProcessoDoBroker broker)
            throws InterruptedException {
        encerrar(broker == null ? null : broker.processo());
    }

    private void encerrar(Process processo) throws InterruptedException {
        if (processo == null || !processo.isAlive()) {
            return;
        }
        processo.destroy();
        if (!processo.waitFor(5, TimeUnit.SECONDS)) {
            processo.destroyForcibly();
            processo.waitFor(5, TimeUnit.SECONDS);
        }
    }

    private void limparTemporarios() {
        if (!Files.exists(diretorioTemporario)) {
            return;
        }
        try (var caminhos = Files.walk(diretorioTemporario)) {
            caminhos.sorted(Comparator.reverseOrder()).forEach(caminho -> {
                try {
                    Files.deleteIfExists(caminho);
                } catch (IOException excecao) {
                    // JUnit faz segunda tentativa de limpeza do @TempDir.
                }
            });
        } catch (IOException excecao) {
            // JUnit faz segunda tentativa de limpeza do @TempDir.
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapa(Object valor) {
        assertThat(valor).isInstanceOf(Map.class);
        return (Map<String, Object>) valor;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listaDeMapas(Object valor) {
        assertThat(valor).isInstanceOf(List.class);
        return (List<Map<String, Object>>) valor;
    }

    private int numero(Object valor) {
        assertThat(valor).isInstanceOf(Number.class);
        return ((Number) valor).intValue();
    }

    private record Cenario(
            UUID usuario,
            UUID vinculo,
            UUID credencial,
            String token,
            UUID topico,
            UUID material,
            String agente,
            String sessao) {
    }

    private record Preparacao(UUID operacao, String codigo) {
    }

    private record ProcessoDoIntegrador(Process processo, String url) {
    }

    private record ProcessoDoBroker(Process processo, String url) {
    }
}
