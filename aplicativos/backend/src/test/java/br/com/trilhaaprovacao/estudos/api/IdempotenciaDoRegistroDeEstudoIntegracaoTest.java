package br.com.trilhaaprovacao.estudos.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "debug=false")
@AutoConfigureMockMvc
@Testcontainers
class IdempotenciaDoRegistroDeEstudoIntegracaoTest {
    private static final String CABECALHO = "X-Chave-De-Idempotencia";

    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_idempotencia_estudos")
                    .withUsername("teste")
                    .withPassword("teste");

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        propriedades.add("spring.datasource.username", POSTGRESQL::getUsername);
        propriedades.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired
    MockMvc api;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate banco;

    @BeforeEach
    void limparBanco() {
        banco.execute("""
                DROP TRIGGER IF EXISTS
                    falhar_recibo_idempotente_na_auditoria
                    ON requisicoes_idempotentes_de_estudo
                """);
        banco.execute("""
                DROP FUNCTION IF EXISTS
                    falhar_recibo_idempotente_na_auditoria()
                """);
        banco.execute("""
                TRUNCATE TABLE requisicoes_idempotentes_de_estudo,
                    registros_de_estudo, topicos_da_materia, materias, usuarios
                CASCADE
                """);
    }

    @Test
    void deveManterRestricoesDaV19NoEsquemaAtualizado()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("migration@example.com");
        String topico = criarTopico(sessao);
        registrar(sessao, "migration-v19", corpoDeTeoria(
                topico, "2026-07-18T10:00:00-03:00", 30,
                "TEORIA", null));

        assertThat(banco.queryForObject("""
                SELECT version
                FROM flyway_schema_history
                WHERE success = TRUE
                ORDER BY installed_rank DESC
                LIMIT 1
                """, String.class)).isEqualTo("21");
        assertThat(banco.queryForObject("""
                SELECT count(*)
                FROM pg_constraint
                WHERE conname IN (
                    'uk_requisicoes_idempotentes_estudo_usuario_chave',
                    'ck_requisicoes_idempotentes_estudo_chave',
                    'ck_requisicoes_idempotentes_estudo_hash',
                    'fk_requisicoes_idempotentes_estudo_usuario',
                    'fk_requisicoes_idempotentes_estudo_registro'
                )
                """, Integer.class)).isEqualTo(5);
        assertThatThrownBy(() -> banco.update("""
                UPDATE requisicoes_idempotentes_de_estudo
                SET hash_da_requisicao = 'invalido'
                """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> banco.update("""
                UPDATE requisicoes_idempotentes_de_estudo
                SET chave_de_idempotencia = 'possui barra/'
                """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(contar("requisicoes_idempotentes_de_estudo")).isEqualTo(1);
    }

    @Test
    void deveRepetirMesmaOperacaoCanonicaComRespostaELocationIdenticos()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("repeticao@example.com");
        String topico = criarTopico(sessao);
        String chave = UUID.randomUUID().toString();
        String primeiroCorpo = corpoDeTeoria(
                topico, "2026-07-18T10:00:00-03:00", 30,
                null, "  Sessao de estudo  ");
        String corpoSemanticamenteIgual = corpoDeTeoria(
                topico, "2026-07-18T13:00:00Z", 30,
                "OUTRA", "Sessao de estudo");

        MvcResult primeira = registrar(sessao, chave, primeiroCorpo);
        MvcResult repeticao = registrar(
                sessao, chave, corpoSemanticamenteIgual);

        assertThat(primeira.getResponse().getStatus()).isEqualTo(201);
        assertThat(repeticao.getResponse().getStatus()).isEqualTo(201);
        assertThat(repeticao.getResponse().getHeader("Location"))
                .isEqualTo(primeira.getResponse().getHeader("Location"));
        assertThat(repeticao.getResponse().getContentAsString())
                .isEqualTo(primeira.getResponse().getContentAsString());
        assertThat(contar("registros_de_estudo")).isEqualTo(1);
        assertThat(contar("requisicoes_idempotentes_de_estudo")).isEqualTo(1);
    }

    @Test
    void deveRejeitarReusoDaChaveComOutroPayloadSemAlterarEstado()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("conflito@example.com");
        String topico = criarTopico(sessao);
        String chave = "registro-estudo:conflito_1";

        registrar(sessao, chave, corpoDeTeoria(
                topico, "2026-07-18T10:00:00-03:00", 30,
                "TEORIA", null));
        api.perform(post("/api/v1/estudos").session(sessao).with(csrf())
                        .header(CABECALHO, chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeTeoria(
                                topico, "2026-07-18T10:00:00-03:00", 31,
                                "TEORIA", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo")
                        .value("CHAVE_DE_IDEMPOTENCIA_REUTILIZADA"));

        assertThat(contar("registros_de_estudo")).isEqualTo(1);
        assertThat(contar("requisicoes_idempotentes_de_estudo")).isEqualTo(1);
    }

    @Test
    void deveManterChaveOpcionalValidarFormatoEIsolarPorUsuario()
            throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("escopo.a@example.com");
        MockHttpSession sessaoB = criarContaEEntrar("escopo.b@example.com");
        String topicoA = criarTopico(sessaoA);
        String topicoB = criarTopico(sessaoB);
        String corpoA = corpoDeTeoria(
                topicoA, "2026-07-18T10:00:00-03:00", 30,
                "TEORIA", null);

        api.perform(post("/api/v1/estudos").session(sessaoA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoA))
                .andExpect(status().isCreated());
        api.perform(post("/api/v1/estudos").session(sessaoA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoA))
                .andExpect(status().isCreated());

        String chaveCompartilhada = "mesma-chave";
        registrar(sessaoA, chaveCompartilhada, corpoA);
        registrar(sessaoB, chaveCompartilhada, corpoDeTeoria(
                topicoB, "2026-07-18T10:00:00-03:00", 30,
                "TEORIA", null));

        assertThat(contar("registros_de_estudo")).isEqualTo(4);
        assertThat(contar("requisicoes_idempotentes_de_estudo")).isEqualTo(2);

        for (String invalida : Set.of(" ", "possui espaço", "possui/barra",
                "a".repeat(161))) {
            api.perform(post("/api/v1/estudos").session(sessaoA).with(csrf())
                            .header(CABECALHO, invalida)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoA))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo")
                            .value("CHAVE_DE_IDEMPOTENCIA_INVALIDA"));
        }
        assertThat(contar("registros_de_estudo")).isEqualTo(4);
    }

    @Test
    void deveLiberarChaveEReverterRegistroQuandoEvidenciaFalha()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("rollback@example.com");
        String topico = criarTopico(sessao);
        String chave = "rollback-e-retry";

        api.perform(post("/api/v1/estudos").session(sessao).with(csrf())
                        .header(CABECALHO, chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeQuestoes(topico, false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo")
                        .value("RESULTADO_DE_QUESTOES_OBRIGATORIO"));
        assertThat(contar("registros_de_estudo")).isZero();
        assertThat(contar("evidencias_de_aprendizagem")).isZero();
        assertThat(contar("requisicoes_idempotentes_de_estudo")).isZero();

        registrar(sessao, chave, corpoDeQuestoes(topico, true));
        assertThat(contar("registros_de_estudo")).isEqualTo(1);
        assertThat(contar("evidencias_de_aprendizagem")).isEqualTo(1);
        assertThat(contar("requisicoes_idempotentes_de_estudo")).isEqualTo(1);
    }

    @Test
    void deveReverterRegistroEEvidenciaQuandoPersistenciaDoReciboFalha()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("rollback.recibo@example.com");
        String topico = criarTopico(sessao);
        String chave = "rollback-do-recibo";
        banco.execute("""
                CREATE FUNCTION falhar_recibo_idempotente_na_auditoria()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    RAISE EXCEPTION 'falha de recibo injetada pela auditoria'
                        USING ERRCODE = '23514';
                END;
                $$
                """);
        banco.execute("""
                CREATE TRIGGER falhar_recibo_idempotente_na_auditoria
                BEFORE INSERT ON requisicoes_idempotentes_de_estudo
                FOR EACH ROW
                EXECUTE FUNCTION falhar_recibo_idempotente_na_auditoria()
                """);

        api.perform(post("/api/v1/estudos").session(sessao).with(csrf())
                        .header(CABECALHO, chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeQuestoes(topico, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLITO_DE_DADOS"));
        assertThat(contar("registros_de_estudo")).isZero();
        assertThat(contar("evidencias_de_aprendizagem")).isZero();
        assertThat(contar("requisicoes_idempotentes_de_estudo")).isZero();

        banco.execute("""
                DROP TRIGGER falhar_recibo_idempotente_na_auditoria
                    ON requisicoes_idempotentes_de_estudo
                """);
        registrar(sessao, chave, corpoDeQuestoes(topico, true));
        assertThat(contar("registros_de_estudo")).isEqualTo(1);
        assertThat(contar("evidencias_de_aprendizagem")).isEqualTo(1);
        assertThat(contar("requisicoes_idempotentes_de_estudo")).isEqualTo(1);
    }

    @Test
    void deveSerializarRetriesConcorrentesSemDuplicarRegistroOuEvidencia()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("concorrencia@example.com");
        String topico = criarTopico(sessao);
        String chave = "retry-concorrente";
        String corpo = corpoDeQuestoes(topico, true);

        var resultados = executarConcorrentemente(
                () -> registrar(sessao, chave, corpo),
                () -> registrar(sessao, chave, corpo));

        assertThat(resultados.primeiro().getResponse().getStatus()).isEqualTo(201);
        assertThat(resultados.segundo().getResponse().getStatus()).isEqualTo(201);
        assertThat(resultados.primeiro().getResponse().getHeader("Location"))
                .isEqualTo(resultados.segundo().getResponse().getHeader("Location"));
        assertThat(resultados.primeiro().getResponse().getContentAsString())
                .isEqualTo(resultados.segundo().getResponse().getContentAsString());
        assertThat(contar("registros_de_estudo")).isEqualTo(1);
        assertThat(contar("evidencias_de_aprendizagem")).isEqualTo(1);
        assertThat(contar("requisicoes_idempotentes_de_estudo")).isEqualTo(1);
    }

    @Test
    void deveAceitarUmaUnicaVersaoQuandoPayloadsConflitamEmConcorrencia()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar(
                "concorrencia.conflito@example.com");
        String topico = criarTopico(sessao);
        String chave = "payloads-concorrentes";

        var resultados = executarConcorrentemente(
                () -> registrar(sessao, chave, corpoDeTeoria(
                        topico, "2026-07-18T10:00:00-03:00", 30,
                        "TEORIA", null)),
                () -> registrar(sessao, chave, corpoDeTeoria(
                        topico, "2026-07-18T10:00:00-03:00", 31,
                        "TEORIA", null)));

        assertThat(Set.of(
                resultados.primeiro().getResponse().getStatus(),
                resultados.segundo().getResponse().getStatus()))
                .containsExactlyInAnyOrder(201, 409);
        MvcResult conflito = resultados.primeiro().getResponse().getStatus() == 409
                ? resultados.primeiro() : resultados.segundo();
        assertThat(json.readTree(conflito.getResponse().getContentAsString())
                .get("codigo").asText())
                .isEqualTo("CHAVE_DE_IDEMPOTENCIA_REUTILIZADA");
        assertThat(contar("registros_de_estudo")).isEqualTo(1);
        assertThat(contar("requisicoes_idempotentes_de_estudo")).isEqualTo(1);
    }

    private ResultadosConcorrentes executarConcorrentemente(
            AcaoHttp primeira, AcaoHttp segunda) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        try {
            Future<MvcResult> futuroA = executor.submit(
                    () -> aguardarEExecutar(prontas, iniciar, primeira));
            Future<MvcResult> futuroB = executor.submit(
                    () -> aguardarEExecutar(prontas, iniciar, segunda));
            assertThat(prontas.await(10, TimeUnit.SECONDS)).isTrue();
            iniciar.countDown();
            return new ResultadosConcorrentes(
                    futuroA.get(20, TimeUnit.SECONDS),
                    futuroB.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private MvcResult aguardarEExecutar(
            CountDownLatch prontas, CountDownLatch iniciar, AcaoHttp acao)
            throws Exception {
        prontas.countDown();
        if (!iniciar.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Barreira concorrente nao liberada.");
        }
        return acao.executar();
    }

    private MvcResult registrar(
            MockHttpSession sessao, String chave, String corpo) throws Exception {
        return api.perform(post("/api/v1/estudos").session(sessao).with(csrf())
                        .header(CABECALHO, chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andReturn();
    }

    private MockHttpSession criarContaEEntrar(String email) throws Exception {
        api.perform(post("/api/v1/autenticacao/cadastro").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Pessoa","email":"%s",
                                 "senha":"senha-segura-123"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return (MockHttpSession) api.perform(
                        post("/api/v1/autenticacao/login").with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":"%s",
                                         "senha":"senha-segura-123"}
                                        """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private String criarTopico(MockHttpSession sessao) throws Exception {
        String materia = identificador(api.perform(
                        post("/api/v1/materias").session(sessao).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nome\":\"Direito\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return identificador(api.perform(
                        post("/api/v1/materias/{id}/topicos", materia)
                                .session(sessao).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nome\":\"Constitucional\",\"ordem\":1}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String corpoDeTeoria(
            String topico, String dataHora, int duracao,
            String tipo, String observacao) {
        String campoTipo = tipo == null
                ? "" : "\"tipoDeEstudo\":\"" + tipo + "\",";
        String campoObservacao = observacao == null
                ? "" : "\"observacao\":\"" + observacao + "\",";
        return """
                {"identificadorDoTopico":"%s",%s%s
                 "dataHora":"%s","duracaoEmMinutos":%d}
                """.formatted(topico, campoTipo, campoObservacao,
                dataHora, duracao);
    }

    private String corpoDeQuestoes(String topico, boolean incluirEvidencia) {
        String evidencia = incluirEvidencia
                ? """
                  ,"evidencia":{"resultadoDeQuestoes":{
                    "quantidadeDeQuestoes":10,"quantidadeDeAcertos":8},
                    "dificuldadePercebida":3}
                  """
                : "";
        return """
                {"identificadorDoTopico":"%s","tipoDeEstudo":"QUESTOES",
                 "dataHora":"2026-07-18T10:00:00-03:00",
                 "duracaoEmMinutos":30%s}
                """.formatted(topico, evidencia);
    }

    private String identificador(String corpo) {
        return json.readTree(corpo).get("identificador").asText();
    }

    private int contar(String tabela) {
        return banco.queryForObject(
                "SELECT count(*) FROM " + tabela, Integer.class);
    }

    @FunctionalInterface
    private interface AcaoHttp {
        MvcResult executar() throws Exception;
    }

    private record ResultadosConcorrentes(
            MvcResult primeiro, MvcResult segundo) {
    }
}
