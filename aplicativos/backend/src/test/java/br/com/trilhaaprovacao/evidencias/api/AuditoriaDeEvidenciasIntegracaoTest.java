package br.com.trilhaaprovacao.evidencias.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "debug=false")
@AutoConfigureMockMvc
@Testcontainers
class AuditoriaDeEvidenciasIntegracaoTest {
    private static final String FUNCAO_DE_FALHA =
            "falhar_persistencia_de_ocorrencia_na_auditoria";
    private static final String GATILHO_DE_FALHA =
            "tg_falhar_persistencia_de_ocorrencia_na_auditoria";

    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_auditoria_evidencias")
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

    @BeforeEach
    void limparBanco() {
        removerFalhaNaPersistenciaDeOcorrencia();
        banco.execute("TRUNCATE TABLE usuarios CASCADE");
    }

    @AfterEach
    void removerGatilhoDeFalha() {
        removerFalhaNaPersistenciaDeOcorrencia();
    }

    @Test
    void deveValidarLimitesEObrigatoriedadeSemPersistirEntradasInvalidas()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("limites.evidencias@example.com");
        String topico = criarTopico(sessao, "Matematica", "Equacoes");

        registrar(sessao, topico, "QUESTOES", "2026-07-01T10:00:00-03:00",
                evidenciaDeQuestoes(10, 11, List.of()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("EVIDENCIA_INVALIDA"));
        registrar(sessao, topico, "QUESTOES", "2026-07-01T10:01:00-03:00",
                evidenciaDeQuestoes(-1, 0, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ENTRADA_INVALIDA"));
        registrar(sessao, topico, "QUESTOES", "2026-07-01T10:02:00-03:00",
                evidenciaDeQuestoes(0, 0, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ENTRADA_INVALIDA"));
        registrar(sessao, topico, "QUESTOES", "2026-07-01T10:03:00-03:00",
                evidenciaDeQuestoes(10, -1, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ENTRADA_INVALIDA"));
        registrar(sessao, topico, "QUESTOES", "2026-07-01T10:04:00-03:00",
                evidenciaDeQuestoes(10, 9,
                        List.of(padrao("x".repeat(201), 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ENTRADA_INVALIDA"));
        registrar(sessao, topico, "QUESTOES", "2026-07-01T10:05:00-03:00",
                evidenciaDeQuestoes(10, 9,
                        List.of(padrao("Erro sem ocorrência", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ENTRADA_INVALIDA"));
        registrar(sessao, topico, "QUESTOES", "2026-07-01T10:06:00-03:00",
                evidenciaDeQuestoes(10, 9,
                        List.of(padrao("Ocorrências acima dos erros", 2))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("EVIDENCIA_INVALIDA"));

        for (int recordacao : List.of(0, 6)) {
            registrar(sessao, topico, "REVISAO", "2026-07-01T11:00:00-03:00",
                    Map.of("nivelDeRecordacao", recordacao))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("ENTRADA_INVALIDA"));
        }
        for (int dificuldade : List.of(0, 6)) {
            registrar(sessao, topico, "TEORIA", "2026-07-01T12:00:00-03:00",
                    Map.of("dificuldadePercebida", dificuldade))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("ENTRADA_INVALIDA"));
        }

        for (String tipo : List.of("QUESTOES", "SIMULADO", "CADERNO_DE_ERROS")) {
            registrar(sessao, topico, tipo, "2026-07-01T13:00:00-03:00", null)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo")
                            .value("RESULTADO_DE_QUESTOES_OBRIGATORIO"));
        }
        registrar(sessao, topico, "REVISAO", "2026-07-01T14:00:00-03:00", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RECORDACAO_OBRIGATORIA"));

        assertThat(contar("registros_de_estudo")).isZero();
        assertThat(contar("evidencias_de_aprendizagem")).isZero();

        String estudoComum = identificador(registrar(
                sessao, topico, "TEORIA", "2026-07-01T15:00:00-03:00", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.situacao").value("ATIVO"))
                .andReturn().getResponse().getContentAsString());

        assertThat(estudoComum).isNotBlank();
        assertThat(contar("registros_de_estudo")).isEqualTo(1);
        assertThat(contar("evidencias_de_aprendizagem")).isZero();
    }

    @Test
    void deveNormalizarPadroesESomarOcorrenciasSemConfundirRepeticao()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar(
                "normalizacao.evidencias@example.com");
        String topico = criarTopico(sessao, "Portugues", "Acentuacao");

        registrar(sessao, topico, "QUESTOES", "2026-07-01T10:00:00-03:00",
                evidenciaDeQuestoes(10, 5, List.of(
                        padrao("  Erro   de Acentuação  ", 3))))
                .andExpect(status().isCreated());
        registrar(sessao, topico, "QUESTOES", "2026-07-20T10:00:00-03:00",
                evidenciaDeQuestoes(10, 8, List.of(
                        padrao("erro de acentuacao", 2))))
                .andExpect(status().isCreated());

        registrar(sessao, topico, "QUESTOES", "2026-07-21T10:00:00-03:00",
                evidenciaDeQuestoes(10, 8, List.of(
                        padrao("Erro de Acentuação", 1),
                        padrao(" erro   de acentuacao ", 1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("EVIDENCIA_INVALIDA"));

        assertThat(contar("registros_de_estudo")).isEqualTo(2);
        assertThat(contar("padroes_de_erro")).isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT descricao_normalizada FROM padroes_de_erro
                """, String.class)).isEqualTo("erro de acentuacao");
        assertThat(banco.queryForObject("""
                SELECT descricao FROM padroes_de_erro
                """, String.class)).isEqualTo("Erro de Acentuação");
        assertThat(contar("ocorrencias_de_padrao_de_erro")).isEqualTo(2);
        assertThat(banco.queryForObject("""
                SELECT SUM(quantidade_de_ocorrencias)
                FROM ocorrencias_de_padrao_de_erro
                """, Integer.class)).isEqualTo(5);

        api.perform(get("/api/v1/evidencias/diagnostico-de-topicos")
                        .param("dataDeReferencia", "2026-07-24")
                        .session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].quantidadeDeEvidencias").value(2))
                .andExpect(jsonPath("$[0].padroesDeErroRepetidos[0].descricao")
                        .value("Erro de Acentuação"))
                .andExpect(jsonPath(
                        "$[0].padroesDeErroRepetidos[0].quantidadeDeEvidencias")
                        .value(2))
                .andExpect(jsonPath(
                        "$[0].padroesDeErroRepetidos[0].quantidadeDeOcorrencias")
                        .value(5));
    }

    @Test
    void deveIsolarUsuariosComTopicosEPadroesDeMesmaDescricao()
            throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("isolamento.a@example.com");
        String topicoA = criarTopico(sessaoA, "Matematica", "Equacoes");
        registrar(sessaoA, topicoA, "QUESTOES", "2026-07-10T10:00:00-03:00",
                evidenciaDeQuestoes(10, 9, List.of(padrao("Erro de sinal", 1))))
                .andExpect(status().isCreated());

        MockHttpSession sessaoB = criarContaEEntrar("isolamento.b@example.com");
        String topicoB = criarTopico(sessaoB, "Matematica", "Equacoes");
        registrar(sessaoB, topicoB, "QUESTOES", "2026-07-10T10:00:00-03:00",
                evidenciaDeQuestoes(20, 18, List.of(padrao("Erro de sinal", 2))))
                .andExpect(status().isCreated());
        registrar(sessaoB, topicoA, "QUESTOES", "2026-07-10T11:00:00-03:00",
                evidenciaDeQuestoes(30, 27, List.of()))
                .andExpect(status().isNotFound());

        assertThat(contar("padroes_de_erro")).isEqualTo(2);
        assertThat(contar("registros_de_estudo")).isEqualTo(2);
        assertThat(banco.queryForObject("""
                SELECT COUNT(DISTINCT usuario_id) FROM padroes_de_erro
                """, Integer.class)).isEqualTo(2);

        api.perform(get("/api/v1/evidencias/diagnostico-de-topicos")
                        .param("dataDeReferencia", "2026-07-24").session(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totaisHistoricos.questoes").value(10))
                .andExpect(jsonPath("$[0].totaisHistoricos.erros").value(1));
        api.perform(get("/api/v1/evidencias/diagnostico-de-topicos")
                        .param("dataDeReferencia", "2026-07-24").session(sessaoB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totaisHistoricos.questoes").value(20))
                .andExpect(jsonPath("$[0].totaisHistoricos.erros").value(2));

        api.perform(get("/api/v1/evidencias/padroes-de-erro")
                        .param("identificadorDoTopico", topicoA).session(sessaoB))
                .andExpect(status().isNotFound());
        api.perform(get("/api/v1/evidencias/padroes-de-erro")
                        .param("identificadorDoTopico", topicoB).session(sessaoB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value("Erro de sinal"));
    }

    @Test
    void devePreservarHistoricoEExcluirCorrecaoECancelamentoDosCalculosAtuais()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar(
                "correcao.cancelamento@example.com");
        String topico = criarTopico(sessao, "Estatistica", "Probabilidade");

        String original = identificador(registrar(
                sessao, topico, "QUESTOES", "2026-07-01T10:00:00-03:00",
                evidenciaDeQuestoes(100, 0,
                        List.of(padrao("Fórmula incorreta", 5))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String correcao = identificador(corrigir(
                sessao, original, topico, "2026-07-02T10:00:00-03:00",
                evidenciaDeQuestoes(10, 9,
                        List.of(padrao("Fórmula incorreta", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificadorDoRegistroDeOrigem")
                        .value(original))
                .andReturn().getResponse().getContentAsString());

        String cancelado = identificador(registrar(
                sessao, topico, "QUESTOES", "2026-07-03T10:00:00-03:00",
                evidenciaDeQuestoes(200, 0,
                        List.of(padrao("Fórmula incorreta", 2))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        cancelar(sessao, cancelado)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CANCELADO"));

        api.perform(get("/api/v1/evidencias/diagnostico-de-topicos")
                        .param("dataDeReferencia", "2026-07-24").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].quantidadeDeEvidencias").value(1))
                .andExpect(jsonPath("$[0].totaisHistoricos.questoes").value(10))
                .andExpect(jsonPath("$[0].totaisHistoricos.acertos").value(9))
                .andExpect(jsonPath("$[0].padroesDeErroRepetidos").isEmpty());

        cancelar(sessao, correcao)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CANCELADO"));
        api.perform(get("/api/v1/evidencias/diagnostico-de-topicos")
                        .param("dataDeReferencia", "2026-07-24").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].quantidadeDeEvidencias").value(0))
                .andExpect(jsonPath("$[0].totaisHistoricos.questoes").value(0));

        assertThat(contar("registros_de_estudo")).isEqualTo(3);
        assertThat(contar("evidencias_de_aprendizagem")).isEqualTo(3);
        assertThat(banco.queryForObject("""
                SELECT situacao FROM registros_de_estudo
                WHERE identificador = ?::uuid
                """, String.class, original)).isEqualTo("CORRIGIDO");
    }

    @Test
    void deveReverterTodaATransacaoQuandoPersistirOcorrenciaFalha()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("rollback.evidencias@example.com");
        String topico = criarTopico(sessao, "Direito", "Atos administrativos");
        instalarFalhaNaPersistenciaDeOcorrencia();

        registrar(sessao, topico, "QUESTOES", "2026-07-10T10:00:00-03:00",
                evidenciaDeQuestoes(10, 9,
                        List.of(padrao("Competência vinculada", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLITO_DE_DADOS"));

        assertThat(contar("registros_de_estudo")).isZero();
        assertThat(contar("evidencias_de_aprendizagem")).isZero();
        assertThat(contar("padroes_de_erro")).isZero();
        assertThat(contar("ocorrencias_de_padrao_de_erro")).isZero();
    }

    @Test
    void deveManterRestricaoDaV18NoEsquemaAtualizado()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("migration.evidencias@example.com");
        String topico = criarTopico(sessao, "Informatica", "Banco de dados");
        String registro = identificador(registrar(
                sessao, topico, "TEORIA", "2026-07-10T10:00:00-03:00", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(banco.queryForObject("""
                SELECT version FROM flyway_schema_history
                WHERE success = TRUE
                ORDER BY installed_rank DESC
                LIMIT 1
                """, String.class)).isEqualTo("21");
        assertThatThrownBy(() -> inserirEvidenciaDiretamente(
                registro, 10, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserirEvidenciaDiretamente(
                registro, null, 5, 3))
                .isInstanceOf(DataIntegrityViolationException.class);

        inserirEvidenciaDiretamente(registro, 10, 8, null);
        assertThat(contar("evidencias_de_aprendizagem")).isEqualTo(1);
    }

    private ResultActions registrar(MockHttpSession sessao, String topico,
            String tipo, String dataHora, Map<String, Object> evidencia)
            throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("identificadorDoTopico", topico);
        corpo.put("tipoDeEstudo", tipo);
        corpo.put("dataHora", dataHora);
        corpo.put("duracaoEmMinutos", 30);
        if (evidencia != null) {
            corpo.put("evidencia", evidencia);
        }
        return api.perform(post("/api/v1/estudos").session(sessao).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(corpo)));
    }

    private ResultActions corrigir(MockHttpSession sessao, String estudo,
            String topico, String dataHora, Map<String, Object> evidencia)
            throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("identificadorDoTopico", topico);
        corpo.put("tipoDeEstudo", "QUESTOES");
        corpo.put("dataHora", dataHora);
        corpo.put("duracaoEmMinutos", 30);
        corpo.put("evidencia", evidencia);
        return api.perform(put("/api/v1/estudos/{id}/correcao", estudo)
                .session(sessao).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(corpo)));
    }

    private ResultActions cancelar(MockHttpSession sessao, String estudo)
            throws Exception {
        return api.perform(post("/api/v1/estudos/{id}/cancelamento", estudo)
                .session(sessao).with(csrf()));
    }

    private Map<String, Object> evidenciaDeQuestoes(int questoes, int acertos,
            List<Map<String, Object>> padroes) {
        Map<String, Object> evidencia = new LinkedHashMap<>();
        evidencia.put("resultadoDeQuestoes", Map.of(
                "quantidadeDeQuestoes", questoes,
                "quantidadeDeAcertos", acertos));
        evidencia.put("padroesDeErro", padroes);
        return evidencia;
    }

    private Map<String, Object> padrao(String descricao, int ocorrencias) {
        return Map.of("descricao", descricao,
                "quantidadeDeOcorrencias", ocorrencias);
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
                                        {"email":"%s","senha":"senha-segura-123"}
                                        """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private String criarTopico(MockHttpSession sessao, String nomeDaMateria,
            String nomeDoTopico) throws Exception {
        String materia = identificador(api.perform(post("/api/v1/materias")
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("nome", nomeDaMateria))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return identificador(api.perform(post(
                        "/api/v1/materias/{materia}/topicos", materia)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("nome", nomeDoTopico, "ordem", 1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String identificador(String resposta) {
        return json.readTree(resposta).get("identificador").asText();
    }

    private int contar(String tabela) {
        return banco.queryForObject(
                "SELECT COUNT(*) FROM " + tabela, Integer.class);
    }

    private void instalarFalhaNaPersistenciaDeOcorrencia() {
        banco.execute("""
                CREATE FUNCTION %s()
                RETURNS TRIGGER
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    RAISE EXCEPTION 'falha de persistencia injetada pela auditoria'
                        USING ERRCODE = '23514';
                END;
                $$
                """.formatted(FUNCAO_DE_FALHA));
        banco.execute("""
                CREATE TRIGGER %s
                BEFORE INSERT ON ocorrencias_de_padrao_de_erro
                FOR EACH ROW EXECUTE FUNCTION %s()
                """.formatted(GATILHO_DE_FALHA, FUNCAO_DE_FALHA));
    }

    private void removerFalhaNaPersistenciaDeOcorrencia() {
        banco.execute("""
                DROP TRIGGER IF EXISTS %s
                ON ocorrencias_de_padrao_de_erro
                """.formatted(GATILHO_DE_FALHA));
        banco.execute("DROP FUNCTION IF EXISTS " + FUNCAO_DE_FALHA + "()");
    }

    private void inserirEvidenciaDiretamente(String registro, Integer questoes,
            Integer acertos, Integer dificuldade) {
        banco.update("""
                INSERT INTO evidencias_de_aprendizagem (
                    identificador, registro_de_estudo_id,
                    quantidade_de_questoes, quantidade_de_acertos,
                    nivel_de_recordacao, dificuldade_percebida,
                    criado_em, atualizado_em, versao)
                VALUES (?, ?::uuid, ?, ?, NULL, ?, CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, 0)
                """, UUID.randomUUID(), registro, questoes, acertos, dificuldade);
    }
}
