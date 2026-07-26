package br.com.trilhaaprovacao.automacao.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.trilhaaprovacao.automacao.aplicacao.ResultadoDaConsultaMcp;
import br.com.trilhaaprovacao.automacao.aplicacao.ReconciliadorDeHashesDeConfirmacoesReforcadas;
import br.com.trilhaaprovacao.automacao.aplicacao.ReconciliadorDeOperacoesAssistidasExpiradas;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeAplicacaoDeOperacoesAssistidas;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeOperacoesAssistidas;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDePreparacoesMcp;
import br.com.trilhaaprovacao.automacao.infraestrutura.ContextoDaChamadaMcp;
import br.com.trilhaaprovacao.automacao.infraestrutura.FiltroDeCredencialMcp;
import br.com.trilhaaprovacao.automacao.infraestrutura.IdentidadeDaIntegracaoMcp;
import br.com.trilhaaprovacao.automacao.infraestrutura.ServicoDeSegredosDaAutomacao;
import br.com.trilhaaprovacao.automacao.infraestrutura.ValidadorDeAssinaturaDoGateway;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.estudos.aplicacao.ServicoDeMateriaisEEstudos;
import br.com.trilhaaprovacao.estudos.dominio.TipoDeEstudo;
import br.com.trilhaaprovacao.evidencias.aplicacao.DadosDaEvidencia;
import io.micrometer.core.instrument.MeterRegistry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "debug=false",
            "trilha.automacao.habilitada=true",
            "trilha.automacao.identificador-do-bot=123456789",
            "trilha.automacao.segredo-de-hash=segredo-de-hash-exclusivo-dos-testes",
            "trilha.automacao.identificador-da-chave-do-gateway=gateway-openclaw-teste",
            "trilha.automacao.segredo-do-gateway=segredo-do-gateway-exclusivo-dos-testes-123456789",
            "trilha.automacao.limite-do-gateway-por-minuto=500",
            "trilha.automacao.mcp.hosts-permitidos=localhost:*",
            "trilha.automacao.mcp.origens-permitidas=http://localhost"
        })
@AutoConfigureMockMvc
@Testcontainers
class ConfirmacaoDeRegistroDeEstudoPeloGatewayIntegracaoTest {
    private static final long BOT = 123456789L;
    private static final String CHAVE = "gateway-openclaw-teste";
    private static final String SEGREDO =
            "segredo-do-gateway-exclusivo-dos-testes-123456789";
    private static final String CAMINHO =
            "/api/v1/integracoes-confiaveis/telegram/operacoes/confirmacao";

    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_confirmacao_gateway")
                    .withUsername("teste")
                    .withPassword("teste");

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        propriedades.add("spring.datasource.username", POSTGRESQL::getUsername);
        propriedades.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired MockMvc api;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate banco;
    @Autowired ServicoDePreparacoesMcp preparacoes;
    @Autowired ServicoDeOperacoesAssistidas operacoes;
    @Autowired ServicoDeAplicacaoDeOperacoesAssistidas aplicacao;
    @Autowired ServicoDeSegredosDaAutomacao segredos;
    @Autowired ReconciliadorDeOperacoesAssistidasExpiradas reconciliador;
    @Autowired ReconciliadorDeHashesDeConfirmacoesReforcadas
            reconciliadorDeHashes;
    @Autowired MeterRegistry metricas;
    @MockitoSpyBean ServicoDeMateriaisEEstudos estudos;
    @LocalServerPort int porta;

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
    void devePrepararConfirmarQuestoesERepetirSemDuplicar() throws Exception {
        double recebidas = contador(
                "trilha.automacao.confirmacoes.recebidas");
        double aplicadas = contador("trilha.automacao.operacoes.aplicadas");
        double idempotentes = contador(
                "trilha.automacao.confirmacoes.idempotentes");
        Cenario cenario = criarCenario("aplicar", 811001L, true);
        Map<String, Object> argumentos = argumentos(cenario);
        Preparada preparada = prepararPeloMcp(
                cenario, argumentos, "evento-aplicar");
        String corpo = corpo(preparada.codigo(), cenario,
                "update-aplicar-1");
        UUID correlacao = UUID.randomUUID();

        String primeiraResposta = api.perform(postConfiavel(
                        CAMINHO, corpo, "confirmar-aplicar", correlacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operacao.identificador")
                        .value(preparada.operacao().toString()))
                .andExpect(jsonPath("$.operacao.tipo")
                        .value("REGISTRO_DE_ESTUDO"))
                .andExpect(jsonPath("$.operacao.estado").value("APLICADA"))
                .andExpect(jsonPath("$.operacao.resultado.tipo")
                        .value("REGISTRO_DE_ESTUDO"))
                .andExpect(jsonPath("$.exigeNovaConfirmacao").value(false))
                .andReturn().getResponse().getContentAsString();
        JsonNode primeira = json.readTree(primeiraResposta);
        UUID recibo = UUID.fromString(primeira.at(
                "/operacao/resultado/dados/identificador").asText());

        String repetida = api.perform(postConfiavel(
                        CAMINHO, corpo, "confirmar-aplicar", correlacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operacao.estado").value("APLICADA"))
                .andReturn().getResponse().getContentAsString();

        assertThat(UUID.fromString(json.readTree(repetida).at(
                "/operacao/resultado/dados/identificador").asText()))
                .isEqualTo(recibo);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM registros_de_estudo
                 WHERE identificador = ? AND topico_id = ? AND material_id = ?
                """, Integer.class, recibo, cenario.topico(),
                cenario.material())).isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT data_hora FROM registros_de_estudo
                 WHERE identificador = ?
                """, OffsetDateTime.class, recibo).toInstant())
                .isEqualTo(Instant.parse("2026-07-26T13:30:00Z"));
        assertThat(banco.queryForMap("""
                SELECT quantidade_de_questoes, quantidade_de_acertos,
                       dificuldade_percebida
                  FROM evidencias_de_aprendizagem
                 WHERE registro_de_estudo_id = ?
                """, recibo))
                .containsEntry("quantidade_de_questoes", 20)
                .containsEntry("quantidade_de_acertos", 16)
                .containsEntry("dificuldade_percebida", 3);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM ocorrencias_de_padrao_de_erro o
                  JOIN evidencias_de_aprendizagem e
                    ON e.identificador = o.evidencia_id
                 WHERE e.registro_de_estudo_id = ?
                """, Integer.class, recibo)).isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM eventos_de_auditoria_da_automacao
                 WHERE operacao_assistida_id = ?
                   AND acao = 'CONFIRMACAO_IDEMPOTENTE'
                """, Integer.class, preparada.operacao())).isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM eventos_de_auditoria_da_automacao
                 WHERE operacao_assistida_id = ?
                   AND metadados::text LIKE ?
                """, Integer.class, preparada.operacao(),
                "%" + preparada.codigo() + "%")).isZero();
        ResultadoDaConsultaMcp preparoRepetido = preparacoes.preparar(
                "REGISTRO_DE_ESTUDO",
                contexto(cenario, "evento-aplicar"), argumentos);
        assertThat(preparoRepetido.dados())
                .containsEntry("estado", "APLICADA")
                .containsEntry("codigoDeConfirmacao", null)
                .containsEntry("fraseDeConfirmacao", null);
        assertThat(preparoRepetido.dados().get("resultado"))
                .isInstanceOf(Map.class);
        assertThat(contador("trilha.automacao.confirmacoes.recebidas"))
                .isEqualTo(recebidas + 2);
        assertThat(contador("trilha.automacao.operacoes.aplicadas"))
                .isEqualTo(aplicadas + 1);
        assertThat(contador(
                "trilha.automacao.confirmacoes.idempotentes"))
                .isEqualTo(idempotentes + 1);
        assertThat(porta).isPositive();
    }

    @Test
    void deveSerializarConfirmacoesConcorrentesSemDuplicarEstudo()
            throws Exception {
        Cenario cenario = criarCenario("concorrente", 811002L, true);
        Preparada preparada = preparar(cenario, argumentos(cenario),
                "evento-concorrente");
        CountDownLatch largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(() -> confirmarConcorrentemente(
                    preparada, cenario, "update-concorrente-1", largada));
            var segunda = executor.submit(() -> confirmarConcorrentemente(
                    preparada, cenario, "update-concorrente-2", largada));
            largada.countDown();
            List<Integer> codigos = List.of(primeira.get(), segunda.get());
            assertThat(codigos).contains(200);
            assertThat(codigos).allMatch(codigo ->
                    codigo == 200 || codigo == 409);
        }

        api.perform(postConfiavel(CAMINHO,
                        corpo(preparada.codigo(), cenario,
                                "update-concorrente-retry"),
                        "confirmar-concorrente-retry", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operacao.estado").value("APLICADA"));
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM registros_de_estudo
                 WHERE topico_id = ?
                """, Integer.class, cenario.topico())).isEqualTo(1);
    }

    @Test
    void deveRejeitarContextoErradoEPersistirExpiracao() throws Exception {
        double recebidas = contador(
                "trilha.automacao.confirmacoes.recebidas");
        double rejeitadas = contador(
                "trilha.automacao.confirmacoes.rejeitadas");
        double expiradas = contador(
                "trilha.automacao.confirmacoes.expiradas");
        Cenario cenario = criarCenario("contexto", 811003L, true);
        Map<String, Object> argumentos = argumentos(cenario);
        Preparada preparada = preparar(cenario, argumentos(cenario),
                "evento-contexto");

        api.perform(postConfiavel(CAMINHO,
                        corpo(preparada.codigo(), cenario,
                                "update-sessao-errada", "sessao-errada",
                                cenario.chat()),
                        "confirmar-sessao-errada", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo")
                        .value("OPERACAO_ASSISTIDA_NAO_ENCONTRADA"));
        api.perform(postConfiavel(CAMINHO,
                        corpo(preparada.codigo(), cenario,
                                "update-chat-errado", cenario.sessao(),
                                cenario.chat() + 1),
                        "confirmar-chat-errado", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo")
                        .value("OPERACAO_ASSISTIDA_NAO_ENCONTRADA"));
        api.perform(postConfiavel(CAMINHO,
                        corpo(preparada.codigo(), BOT + 1,
                                cenario.telegram(), cenario.chat(),
                                cenario.sessao(), "update-bot-errado"),
                        "confirmar-bot-errado", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo")
                        .value("OPERACAO_ASSISTIDA_NAO_ENCONTRADA"));
        api.perform(postConfiavel(CAMINHO,
                        corpo(preparada.codigo(), BOT,
                                cenario.telegram() + 1, cenario.chat(),
                                cenario.sessao(), "update-telegram-errado"),
                        "confirmar-telegram-errado", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo")
                        .value("OPERACAO_ASSISTIDA_NAO_ENCONTRADA"));

        banco.update("""
                UPDATE vinculos_de_canal
                   SET estado = 'REVOGADO',
                       revogado_em = CURRENT_TIMESTAMP,
                       atualizado_em = CURRENT_TIMESTAMP,
                       versao = versao + 1
                 WHERE identificador = ?
                """, cenario.vinculo());
        api.perform(postConfiavel(CAMINHO,
                        corpo(preparada.codigo(), cenario,
                                "update-vinculo-inativo"),
                        "confirmar-vinculo-inativo", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo")
                        .value("OPERACAO_ASSISTIDA_NAO_ENCONTRADA"));
        banco.update("""
                UPDATE vinculos_de_canal
                   SET estado = 'ATIVO', revogado_em = NULL,
                       atualizado_em = CURRENT_TIMESTAMP,
                       versao = versao + 1
                 WHERE identificador = ?
                """, cenario.vinculo());

        banco.update("""
                UPDATE operacoes_assistidas SET vinculo_id = NULL
                 WHERE identificador = ?
                """, preparada.operacao());
        api.perform(postConfiavel(
                        "/api/v1/integracoes-confiaveis/telegram/operacoes/"
                                + preparada.operacao() + "/confirmacao",
                        corpo(preparada.codigo(), cenario,
                                "update-vinculo-ausente"),
                        "confirmar-vinculo-ausente", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo")
                        .value("OPERACAO_ASSISTIDA_NAO_ENCONTRADA"));
        banco.update("""
                UPDATE operacoes_assistidas SET vinculo_id = ?
                 WHERE identificador = ?
                """, cenario.vinculo(), preparada.operacao());

        assertThat(estado(preparada.operacao()))
                .isEqualTo("AGUARDANDO_CONFIRMACAO");
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM eventos_de_auditoria_da_automacao
                 WHERE operacao_assistida_id = ?
                   AND acao = 'CONFIRMACAO_REJEITADA'
                """, Integer.class, preparada.operacao())).isEqualTo(6);
        assertThat(banco.queryForList("""
                SELECT metadados ->> 'codigoDoResultado'
                  FROM eventos_de_auditoria_da_automacao
                 WHERE operacao_assistida_id = ?
                   AND acao = 'CONFIRMACAO_REJEITADA'
                 ORDER BY metadados ->> 'codigoDoResultado'
                """, String.class, preparada.operacao())).containsExactly(
                        "BOT_DIVERGENTE", "CHAT_DIVERGENTE",
                        "SESSAO_DIVERGENTE", "TELEGRAM_DIVERGENTE",
                        "VINCULO_AUSENTE", "VINCULO_INATIVO");

        banco.update("""
                UPDATE operacoes_assistidas
                   SET criado_em = CURRENT_TIMESTAMP - INTERVAL '1 hour',
                       atualizado_em = CURRENT_TIMESTAMP - INTERVAL '1 minute',
                       expira_em = CURRENT_TIMESTAMP - INTERVAL '1 minute',
                       confirmacao_expira_em =
                           CURRENT_TIMESTAMP - INTERVAL '1 minute'
                 WHERE identificador = ?
                """, preparada.operacao());
        api.perform(postConfiavel(CAMINHO,
                        corpo(preparada.codigo(), cenario, "update-expirada"),
                        "confirmar-expirada", UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo")
                        .value("CONFIRMACAO_EXPIRADA"));

        assertThat(estado(preparada.operacao())).isEqualTo("EXPIRADA");
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM eventos_de_auditoria_da_automacao
                 WHERE operacao_assistida_id = ?
                   AND acao = 'CONFIRMACAO_EXPIRADA'
                """, Integer.class, preparada.operacao())).isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM registros_de_estudo WHERE topico_id = ?
                """, Integer.class, cenario.topico())).isZero();
        ResultadoDaConsultaMcp preparoRepetido = preparacoes.preparar(
                "REGISTRO_DE_ESTUDO",
                contexto(cenario, "evento-contexto"), argumentos);
        assertThat(preparoRepetido.dados())
                .containsEntry("estado", "EXPIRADA")
                .containsEntry("codigoDeConfirmacao", null)
                .containsEntry("fraseDeConfirmacao", null)
                .containsEntry("resultado", null);

        Preparada antiga = preparar(cenario, argumentos,
                "evento-reconciliacao");
        tornarVencida(antiga.operacao());
        reconciliador.run(null);
        assertThat(estado(antiga.operacao())).isEqualTo("EXPIRADA");
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM eventos_de_auditoria_da_automacao
                 WHERE operacao_assistida_id = ?
                   AND acao = 'CONFIRMACAO_EXPIRADA'
                   AND fonte = 'RECONCILIACAO'
                """, Integer.class, antiga.operacao())).isEqualTo(1);
        assertThat(contador("trilha.automacao.confirmacoes.recebidas"))
                .isEqualTo(recebidas + 7);
        assertThat(contador("trilha.automacao.confirmacoes.rejeitadas"))
                .isEqualTo(rejeitadas + 6);
        assertThat(contador("trilha.automacao.confirmacoes.expiradas"))
                .isEqualTo(expiradas + 2);
    }

    @Test
    void deveValidarDonoCoberturaEMudancaConcorrente() throws Exception {
        double divergencias = contador(
                "trilha.automacao.confirmacoes.divergencias");
        Cenario semCobertura = criarCenario("sem-cobertura", 811004L, false);
        assertThatThrownBy(() -> preparar(semCobertura,
                argumentos(semCobertura), "evento-sem-cobertura"))
                .isInstanceOfSatisfying(RegraDeDominio.class,
                        excecao -> assertThat(excecao.codigo())
                                .isEqualTo("MATERIAL_NAO_COBRE_TOPICO"));
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM operacoes_assistidas
                 WHERE usuario_id = ?
                """, Integer.class, semCobertura.usuario())).isZero();

        UUID outroUsuario = inserirUsuario("outro");
        UUID materialAlheio = inserirMaterial(outroUsuario, "alheio");
        Map<String, Object> alheio = new LinkedHashMap<>(
                argumentos(semCobertura));
        alheio.put("identificadorDoMaterial", materialAlheio.toString());
        assertThatThrownBy(() -> preparar(semCobertura, alheio,
                "evento-material-alheio"))
                .isInstanceOfSatisfying(RecursoNaoEncontrado.class,
                        excecao -> assertThat(excecao.codigo())
                                .isEqualTo("MATERIAL_NAO_ENCONTRADO"));

        inserirCobertura(semCobertura.material(), semCobertura.topico());
        Preparada preparada = preparar(semCobertura,
                argumentos(semCobertura), "evento-versao-cobertura");
        banco.update("""
                DELETE FROM coberturas_de_topicos_por_material
                 WHERE material_id = ? AND topico_id = ?
                """, semCobertura.material(), semCobertura.topico());

        api.perform(postConfiavel(CAMINHO,
                        corpo(preparada.codigo(), semCobertura,
                                "update-versao-cobertura"),
                        "confirmar-versao-cobertura", UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo")
                        .value("PREVIA_DE_AUTOMACAO_DESATUALIZADA"));
        assertThat(estado(preparada.operacao()))
                .isEqualTo("AGUARDANDO_CONFIRMACAO");
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM registros_de_estudo WHERE topico_id = ?
                """, Integer.class, semCobertura.topico())).isZero();
        assertThat(contador("trilha.automacao.confirmacoes.divergencias"))
                .isEqualTo(divergencias + 1);
    }

    @Test
    void deveRecuperarSegundaEtapaSemPersistirCodigoPuro() throws Exception {
        Cenario cenario = criarCenario("reforcada", 811005L, true);
        Map<String, Object> proposta = argumentos(cenario);
        String versoes = json.writeValueAsString(preparacoes.versoesAtuais(
                "REGISTRO_DE_ESTUDO", cenario.usuario(), proposta));
        var preparada = operacoes.prepararParaConfirmacaoReforcada(
                cenario.usuario(), cenario.vinculo(),
                "REGISTRO_DE_ESTUDO", "Registrar estudo reforcado.",
                json.writeValueAsString(proposta), versoes,
                "reforcada:" + UUID.randomUUID());
        String primeiroCodigo = preparada.codigoDeConfirmacao();
        String primeiroCorpo = corpo(primeiroCodigo, cenario,
                "update-reforcada-1");

        String primeiraResposta = api.perform(postConfiavel(CAMINHO,
                        primeiroCorpo, "confirmar-reforcada-1",
                        UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exigeNovaConfirmacao").value(true))
                .andReturn().getResponse().getContentAsString();
        String segundoCodigo = json.readTree(primeiraResposta)
                .get("proximoCodigo").asText();

        api.perform(postConfiavel(CAMINHO, primeiroCorpo,
                        "confirmar-reforcada-1", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exigeNovaConfirmacao").value(true))
                .andExpect(jsonPath("$.proximoCodigo")
                        .value(segundoCodigo));
        api.perform(postConfiavel(CAMINHO,
                        corpo(segundoCodigo, cenario, "update-reforcada-2"),
                        "confirmar-reforcada-2", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operacao.estado").value("APLICADA"));
        api.perform(postConfiavel(CAMINHO, primeiroCorpo,
                        "confirmar-reforcada-1", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operacao.estado").value("APLICADA"))
                .andExpect(jsonPath("$.exigeNovaConfirmacao").value(false));

        Map<String, Object> confirmacao = banco.queryForMap("""
                SELECT codigo_de_confirmacao_hash,
                       codigo_de_confirmacao_prefixo,
                       nivel_de_confirmacao, etapa_da_confirmacao
                  FROM operacoes_assistidas WHERE identificador = ?
                """, preparada.operacao().identificador());
        assertThat(confirmacao.get("codigo_de_confirmacao_hash"))
                .isNotEqualTo(primeiroCodigo)
                .isNotEqualTo(segundoCodigo);
        assertThat(confirmacao)
                .containsEntry("nivel_de_confirmacao", "REFORCADA")
                .containsEntry("etapa_da_confirmacao", 1);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM registros_de_estudo WHERE topico_id = ?
                """, Integer.class, cenario.topico())).isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM eventos_de_auditoria_da_automacao
                 WHERE operacao_assistida_id = ?
                   AND acao = 'CONFIRMACAO_REFORCADA_REPETIDA'
                """, Integer.class,
                preparada.operacao().identificador())).isEqualTo(1);
    }

    @Test
    void deveReconciliarMaisDeCemHashesLegadosERepetirCodigoAposAplicacao()
            throws Exception {
        Cenario cenario = criarCenario("reforcada-escala", 811007L, true);
        Map<String, Object> propostaAlvo = new LinkedHashMap<>(
                argumentos(cenario));
        propostaAlvo.put("observacao", "Operacao reforcada alvo.");
        String versoes = json.writeValueAsString(preparacoes.versoesAtuais(
                "REGISTRO_DE_ESTUDO", cenario.usuario(), propostaAlvo));
        var alvo = operacoes.prepararParaConfirmacaoReforcada(
                cenario.usuario(), cenario.vinculo(),
                "REGISTRO_DE_ESTUDO", "Registrar estudo reforcado alvo.",
                json.writeValueAsString(propostaAlvo), versoes,
                "reforcada-escala-alvo:" + UUID.randomUUID());
        String primeiroCodigo = alvo.codigoDeConfirmacao();
        String segundoCodigo = aplicacao.confirmarComResultado(
                primeiroCodigo, "TEXTO", BOT, cenario.telegram(),
                cenario.chat(), cenario.sessao(), "update-escala-alvo-1")
                .proximoCodigo();

        for (int indice = 0; indice < 101; indice++) {
            Map<String, Object> proposta = new LinkedHashMap<>(
                    argumentos(cenario));
            proposta.put("observacao", "Operacao candidata " + indice + ".");
            var candidata = operacoes.prepararParaConfirmacaoReforcada(
                    cenario.usuario(), cenario.vinculo(),
                    "REGISTRO_DE_ESTUDO",
                    "Registrar estudo reforcado candidato " + indice + ".",
                    json.writeValueAsString(proposta), versoes,
                    "reforcada-escala-" + indice + ":" + UUID.randomUUID());
            aplicacao.confirmarComResultado(candidata.codigoDeConfirmacao(),
                    "TEXTO", BOT, cenario.telegram(), cenario.chat(),
                    cenario.sessao(), "update-escala-" + indice);
        }

        assertThat(banco.update("""
                UPDATE operacoes_assistidas
                   SET codigo_de_confirmacao_anterior_hash = NULL
                 WHERE vinculo_id = ?
                   AND nivel_de_confirmacao = 'REFORCADA'
                   AND etapa_da_confirmacao = 1
                """, cenario.vinculo())).isEqualTo(102);
        reconciliadorDeHashes.run(null);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM operacoes_assistidas
                 WHERE vinculo_id = ?
                   AND nivel_de_confirmacao = 'REFORCADA'
                   AND etapa_da_confirmacao = 1
                   AND codigo_de_confirmacao_anterior_hash IS NULL
                """, Integer.class, cenario.vinculo())).isZero();
        assertThat(banco.queryForObject("""
                SELECT codigo_de_confirmacao_anterior_hash
                  FROM operacoes_assistidas WHERE identificador = ?
                """, String.class, alvo.operacao().identificador()))
                .isEqualTo(segredos.hash(primeiroCodigo))
                .isNotEqualTo(primeiroCodigo);

        api.perform(postConfiavel(CAMINHO,
                        corpo(primeiroCodigo, cenario,
                                "update-escala-alvo-retry"),
                        "confirmar-escala-alvo-retry", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exigeNovaConfirmacao").value(true))
                .andExpect(jsonPath("$.proximoCodigo").value(segundoCodigo));
        api.perform(postConfiavel(CAMINHO,
                        corpo(segundoCodigo, cenario,
                                "update-escala-alvo-2"),
                        "confirmar-escala-alvo-2", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operacao.estado").value("APLICADA"));
        api.perform(postConfiavel(CAMINHO,
                        corpo(primeiroCodigo, cenario,
                                "update-escala-alvo-aplicada"),
                        "confirmar-escala-alvo-aplicada", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operacao.estado").value("APLICADA"))
                .andExpect(jsonPath("$.exigeNovaConfirmacao").value(false));

        assertThat(banco.queryForObject("""
                SELECT count(*) FROM pg_indexes
                 WHERE schemaname = 'public'
                   AND indexname IN (
                       'idx_operacoes_assistidas_codigo_confirmacao',
                       'idx_operacoes_assistidas_codigo_confirmacao_anterior')
                """, Integer.class)).isEqualTo(2);
    }

    @Test
    void deveReverterAplicacaoFalhaEPersistirAuditoriaDaFalha()
            throws Exception {
        double falhas = contador("trilha.automacao.confirmacoes.falhas");
        Cenario cenario = criarCenario("rollback", 811006L, true);
        Preparada preparada = preparar(cenario, argumentos(cenario),
                "evento-rollback");
        doThrow(new IllegalStateException("falha controlada"))
                .when(estudos).registrarEstudo(
                        eq(cenario.usuario()), eq(cenario.topico()),
                        eq(cenario.material()), eq(TipoDeEstudo.QUESTOES),
                        any(OffsetDateTime.class), eq(45),
                        eq("Simulado direcionado."),
                        any(DadosDaEvidencia.class), eq(true));

        api.perform(postConfiavel(CAMINHO,
                        corpo(preparada.codigo(), cenario,
                                "update-rollback"),
                        "confirmar-rollback", UUID.randomUUID()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"));

        assertThat(estado(preparada.operacao()))
                .isEqualTo("AGUARDANDO_CONFIRMACAO");
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM registros_de_estudo WHERE topico_id = ?
                """, Integer.class, cenario.topico())).isZero();
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM eventos_de_auditoria_da_automacao
                 WHERE operacao_assistida_id = ?
                   AND acao = 'APLICACAO_DA_OPERACAO_FALHOU'
                   AND resultado = 'FALHA'
                """, Integer.class, preparada.operacao())).isEqualTo(1);
        assertThat(contador("trilha.automacao.confirmacoes.falhas"))
                .isEqualTo(falhas + 1);
    }

    private int confirmarConcorrentemente(Preparada preparada,
            Cenario cenario, String update, CountDownLatch largada)
            throws Exception {
        largada.await();
        return api.perform(postConfiavel(CAMINHO,
                        corpo(preparada.codigo(), cenario, update),
                        "confirmar-" + update, UUID.randomUUID()))
                .andReturn().getResponse().getStatus();
    }

    private Preparada preparar(Cenario cenario,
            Map<String, Object> argumentos, String evento) {
        ResultadoDaConsultaMcp resultado = preparacoes.preparar(
                "REGISTRO_DE_ESTUDO", contexto(cenario, evento), argumentos);
        return new Preparada(
                UUID.fromString(resultado.dados()
                        .get("identificadorDaOperacao").toString()),
                resultado.dados().get("codigoDeConfirmacao").toString());
    }

    private Preparada prepararPeloMcp(Cenario cenario,
            Map<String, Object> argumentos, String evento) {
        try (McpSyncClient cliente = clienteMcp(cenario, evento)) {
            cliente.initialize();
            McpSchema.CallToolResult resultado = cliente.callTool(
                    McpSchema.CallToolRequest.builder()
                            .name("preparar_registro_de_estudo")
                            .arguments(argumentos).build());
            assertThat(resultado.isError()).as("resultado MCP: %s", resultado)
                    .isFalse();
            Map<String, Object> resposta = mapa(
                    resultado.structuredContent());
            Map<String, Object> dados = mapa(resposta.get("dados"));
            return new Preparada(
                    UUID.fromString(dados.get(
                            "identificadorDaOperacao").toString()),
                    dados.get("codigoDeConfirmacao").toString());
        }
    }

    private McpSyncClient clienteMcp(Cenario cenario, String evento) {
        var transporte = HttpClientStreamableHttpTransport.builder(
                        "http://localhost:" + porta)
                .openConnectionOnStartup(false)
                .httpRequestCustomizer((pedido, metodo, destino, corpo,
                        contextoDoTransporte) -> pedido
                        .header("Authorization", "Bearer " + cenario.token())
                        .header(FiltroDeCredencialMcp.CABECALHO_DO_AGENTE,
                                cenario.agente())
                        .header(FiltroDeCredencialMcp.CABECALHO_DA_SESSAO,
                                cenario.sessao())
                        .header("X-Identificador-Do-Update", evento))
                .build();
        return McpClient.sync(transporte)
                .initializationTimeout(Duration.ofSeconds(10))
                .requestTimeout(Duration.ofSeconds(10))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapa(Object valor) {
        assertThat(valor).isInstanceOf(Map.class);
        return (Map<String, Object>) valor;
    }

    private ContextoDaChamadaMcp contexto(Cenario cenario, String evento) {
        var identidade = new IdentidadeDaIntegracaoMcp(
                cenario.usuario(), cenario.vinculo(), cenario.credencial(),
                BOT, cenario.telegram(), cenario.agente(), cenario.sessao(),
                0, Set.of("operacoes:preparar"));
        return new ContextoDaChamadaMcp(identidade,
                UUID.randomUUID(), evento);
    }

    private Map<String, Object> argumentos(Cenario cenario) {
        Map<String, Object> argumentos = new LinkedHashMap<>();
        argumentos.put("identificadorDoTopico",
                cenario.topico().toString());
        argumentos.put("identificadorDoMaterial",
                cenario.material().toString());
        argumentos.put("tipoDeEstudo", "QUESTOES");
        argumentos.put("dataHora", "2026-07-26T10:30:00-03:00");
        argumentos.put("duracaoEmMinutos", 45);
        argumentos.put("observacao", "Simulado direcionado.");
        argumentos.put("evidencia", Map.of(
                "quantidadeDeQuestoes", 20,
                "quantidadeDeAcertos", 16,
                "dificuldadePercebida", 3,
                "padroesDeErro", List.of(Map.of(
                        "descricao", "Confusao entre conceitos proximos",
                        "quantidadeDeOcorrencias", 2))));
        return argumentos;
    }

    private String corpo(String codigo, Cenario cenario, String update) {
        return corpo(codigo, cenario, update, cenario.sessao(),
                cenario.chat());
    }

    private String corpo(String codigo, Cenario cenario, String update,
            String sessao, long chat) {
        return corpo(codigo, BOT, cenario.telegram(), chat, sessao, update);
    }

    private String corpo(String codigo, long bot, long telegram, long chat,
            String sessao, String update) {
        return """
                {"codigo":"%s","metodo":"TEXTO","identificadorDoBot":%d,
                 "identificadorDoTelegram":%d,"identificadorDoChat":%d,
                 "identificadorDaSessao":"%s",
                 "identificadorDoUpdate":"%s"}
                """.formatted(codigo, bot, telegram, chat, sessao, update);
    }

    private MockHttpServletRequestBuilder postConfiavel(String caminho,
            String corpo, String idempotencia, UUID correlacao) {
        long instante = Instant.now().getEpochSecond();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String canonico = "TRILHA-HMAC-V1\n" + CHAVE + "\n" + instante
                + "\n" + nonce + "\nPOST\n" + caminho + "\n"
                + sha256(corpo) + "\n" + idempotencia;
        return post(caminho)
                .header(ValidadorDeAssinaturaDoGateway.CABECALHO_DA_CHAVE,
                        CHAVE)
                .header(ValidadorDeAssinaturaDoGateway.CABECALHO_DO_INSTANTE,
                        String.valueOf(instante))
                .header(ValidadorDeAssinaturaDoGateway.CABECALHO_DO_NONCE,
                        nonce)
                .header(ValidadorDeAssinaturaDoGateway.CABECALHO_DA_ASSINATURA,
                        hmac(canonico))
                .header(ValidadorDeAssinaturaDoGateway.CABECALHO_DA_IDEMPOTENCIA,
                        idempotencia)
                .header("X-Identificador-De-Correlacao",
                        correlacao.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo);
    }

    private Cenario criarCenario(String nome, long telegram,
            boolean comCobertura) {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        UUID usuario = inserirUsuario(nome);
        UUID vinculo = UUID.randomUUID();
        UUID credencial = UUID.randomUUID();
        UUID materia = UUID.randomUUID();
        UUID topico = UUID.randomUUID();
        UUID material = UUID.randomUUID();
        String agente = "agente-" + nome;
        String sessao = "sessao-" + nome;
        String token = "mcp_" + UUID.randomUUID().toString().replace("-", "");
        banco.update("""
                INSERT INTO vinculos_de_canal (
                    identificador, usuario_id, canal, bot,
                    identificador_externo, identificador_do_chat, estado,
                    codigo_de_vinculo_hash, codigo_expira_em,
                    codigo_consumido_em, identificador_do_agente,
                    identificador_da_sessao, provisionado_em, criado_em,
                    atualizado_em, versao)
                VALUES (?, ?, 'TELEGRAM', ?, ?, ?, 'ATIVO', ?, ?, ?, ?,
                    ?, ?, ?, ?, 0)
                """, vinculo, usuario, BOT, telegram, telegram,
                segredos.hash("codigo-" + vinculo), agora.plusHours(1),
                agora.minusMinutes(30), agente, sessao,
                agora.minusMinutes(20), agora.minusHours(1), agora);
        banco.update("""
                INSERT INTO credenciais_de_integracao (
                    identificador, vinculo_id, token_hash, prefixo, escopos,
                    expira_em, criado_em, versao)
                VALUES (?, ?, ?, ?, 'operacoes:preparar', ?, ?, 0)
                """, credencial, vinculo, segredos.hash(token),
                token.substring(0, 16), agora.plusDays(30), agora);
        banco.update("""
                INSERT INTO materias (identificador, usuario_id, nome,
                    nome_normalizado, arquivada, criado_em, atualizado_em,
                    versao)
                VALUES (?, ?, ?, ?, FALSE, ?, ?, 0)
                """, materia, usuario, "Materia " + nome,
                "materia " + nome, agora, agora);
        banco.update("""
                INSERT INTO topicos_da_materia (identificador, materia_id,
                    nome, nome_normalizado, ordem, arquivado, criado_em,
                    atualizado_em, versao)
                VALUES (?, ?, ?, ?, 1, FALSE, ?, ?, 0)
                """, topico, materia, "Topico " + nome,
                "topico " + nome, agora, agora);
        banco.update("""
                INSERT INTO materiais_de_estudo (identificador, usuario_id,
                    titulo, tipo, arquivado, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, 'PDF', FALSE, ?, ?, 0)
                """, material, usuario, "Material " + nome, agora, agora);
        if (comCobertura) inserirCobertura(material, topico);
        return new Cenario(usuario, vinculo, credencial, topico, material,
                telegram, telegram, agente, sessao, token);
    }

    private UUID inserirUsuario(String nome) {
        UUID usuario = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        banco.update("""
                INSERT INTO usuarios (
                    identificador, nome, email, senha_hash, situacao,
                    criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, 'hash-de-senha-do-teste', 'ATIVO', ?, ?, 0)
                """, usuario, "Usuario " + nome,
                nome + "-" + usuario + "@example.com", agora, agora);
        return usuario;
    }

    private UUID inserirMaterial(UUID usuario, String nome) {
        UUID material = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        banco.update("""
                INSERT INTO materiais_de_estudo (identificador, usuario_id,
                    titulo, tipo, arquivado, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, 'PDF', FALSE, ?, ?, 0)
                """, material, usuario, "Material " + nome, agora, agora);
        return material;
    }

    private void inserirCobertura(UUID material, UUID topico) {
        banco.update("""
                INSERT INTO coberturas_de_topicos_por_material (
                    identificador, material_id, topico_id, criado_em)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), material, topico,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private String estado(UUID operacao) {
        return banco.queryForObject("""
                SELECT estado FROM operacoes_assistidas
                 WHERE identificador = ?
                """, String.class, operacao);
    }

    private void tornarVencida(UUID operacao) {
        banco.update("""
                UPDATE operacoes_assistidas
                   SET criado_em = CURRENT_TIMESTAMP - INTERVAL '1 hour',
                       atualizado_em = CURRENT_TIMESTAMP - INTERVAL '1 minute',
                       expira_em = CURRENT_TIMESTAMP - INTERVAL '1 minute',
                       confirmacao_expira_em =
                           CURRENT_TIMESTAMP - INTERVAL '1 minute'
                 WHERE identificador = ?
                """, operacao);
    }

    private double contador(String nome) {
        return metricas.get(nome).counter().count();
    }

    private String sha256(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException excecao) {
            throw new IllegalStateException(excecao);
        }
    }

    private String hmac(String valor) {
        try {
            Mac autenticador = Mac.getInstance("HmacSHA256");
            autenticador.init(new SecretKeySpec(
                    SEGREDO.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return HexFormat.of().formatHex(autenticador.doFinal(
                    valor.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException excecao) {
            throw new IllegalStateException(excecao);
        }
    }

    private record Preparada(UUID operacao, String codigo) {
    }

    private record Cenario(
            UUID usuario,
            UUID vinculo,
            UUID credencial,
            UUID topico,
            UUID material,
            long telegram,
            long chat,
            String agente,
            String sessao,
            String token) {
    }
}
