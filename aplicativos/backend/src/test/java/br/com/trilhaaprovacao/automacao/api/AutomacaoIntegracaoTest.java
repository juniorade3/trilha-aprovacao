package br.com.trilhaaprovacao.automacao.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.trilhaaprovacao.automacao.aplicacao.CodigoDeVinculoExpirado;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeOperacoesAssistidas;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDePreparacoesMcp;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeAplicacaoDeOperacoesAssistidas;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeVinculosDoTelegram;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeOperacoesCriticasMcp;
import br.com.trilhaaprovacao.automacao.infraestrutura.ContextoDaChamadaMcp;
import br.com.trilhaaprovacao.automacao.infraestrutura.IdentidadeDaIntegracaoMcp;
import br.com.trilhaaprovacao.automacao.infraestrutura.ServicoDeSegredosDaAutomacao;
import br.com.trilhaaprovacao.automacao.infraestrutura.ValidadorDeAssinaturaDoGateway;
import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.concursos.aplicacao.ServicoDaEstruturaDeConcursos;
import br.com.trilhaaprovacao.concursos.dominio.SituacaoDoConcurso;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "debug=false",
        "trilha.automacao.habilitada=true",
        "trilha.automacao.identificador-do-bot=123456789",
        "trilha.automacao.segredo-de-hash=segredo-de-hash-exclusivo-dos-testes",
        "trilha.automacao.identificador-da-chave-do-gateway=gateway-openclaw-teste",
        "trilha.automacao.segredo-do-gateway=segredo-do-gateway-exclusivo-dos-testes-123456789",
        "trilha.automacao.limite-do-gateway-por-minuto=500",
        "trilha.automacao.validade-do-codigo=PT10M",
        "trilha.automacao.validade-da-credencial=P90D"
})
@AutoConfigureMockMvc
@Testcontainers
class AutomacaoIntegracaoTest {
    private static final long IDENTIFICADOR_DO_BOT = 123456789L;
    private static final String IDENTIFICADOR_DA_CHAVE_DO_GATEWAY =
            "gateway-openclaw-teste";
    private static final String SEGREDO_DO_GATEWAY =
            "segredo-do-gateway-exclusivo-dos-testes-123456789";

    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_automacao")
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
    @Autowired ServicoDeVinculosDoTelegram vinculos;
    @Autowired ServicoDeOperacoesAssistidas operacoes;
    @Autowired ServicoDePreparacoesMcp preparacoes;
    @Autowired ServicoDeAplicacaoDeOperacoesAssistidas aplicacao;
    @Autowired ServicoDeSegredosDaAutomacao segredos;
    @Autowired ServicoDeOperacoesCriticasMcp operacoesCriticas;
    @Autowired ServicoDaEstruturaDeConcursos estruturaDeConcursos;

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
    void deveAplicarMigracaoV17Ponto1EmPostgresqlComRestricoesEAppendOnly()
            throws Exception {
        assertThat(banco.queryForObject("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE success = TRUE AND version = '17.1'
                """, Integer.class)).isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                    'vinculos_de_canal', 'credenciais_de_integracao',
                    'operacoes_assistidas', 'eventos_de_auditoria_da_automacao',
                    'requisicoes_confiaveis_da_automacao')
                """, Integer.class)).isEqualTo(5);
        assertThat(banco.queryForObject("""
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname IN (
                    'uk_vinculos_de_canal_usuario_ativo_ou_pendente',
                    'uk_vinculos_de_canal_externo_ativo',
                    'uk_vinculos_de_canal_sessao_ativa',
                    'uk_credenciais_de_integracao_ativa_por_vinculo',
                    'uk_operacoes_assistidas_update_confirmacao',
                    'idx_eventos_de_auditoria_correlacao',
                    'idx_requisicoes_confiaveis_idempotencia')
                """, Integer.class)).isEqualTo(7);
        assertThat(banco.queryForObject("""
                SELECT count(*)
                FROM information_schema.referential_constraints
                WHERE constraint_schema = 'public'
                  AND constraint_name IN (
                    'fk_vinculos_de_canal_usuario',
                    'fk_credenciais_de_integracao_vinculo',
                    'fk_operacoes_assistidas_usuario',
                    'fk_operacoes_assistidas_vinculo_usuario',
                    'fk_eventos_de_auditoria_usuario',
                    'fk_eventos_de_auditoria_vinculo_usuario',
                    'fk_eventos_de_auditoria_operacao_usuario')
                  AND delete_rule = 'NO ACTION'
                """, Integer.class)).isEqualTo(7);
        assertThat(banco.queryForObject("""
                SELECT count(*)
                FROM pg_trigger
                WHERE tgrelid = 'eventos_de_auditoria_da_automacao'::regclass
                  AND tgname = 'trg_eventos_de_auditoria_append_only'
                  AND NOT tgisinternal
                """, Integer.class)).isEqualTo(1);

        MockHttpSession sessao = criarContaEEntrar("auditoria@example.com");
        gerarCodigo(sessao);
        UUID evento = banco.queryForObject("""
                SELECT identificador
                FROM eventos_de_auditoria_da_automacao
                LIMIT 1
                """, UUID.class);

        assertThatThrownBy(() -> banco.update("""
                UPDATE eventos_de_auditoria_da_automacao
                SET resultado = 'ALTERADO'
                WHERE identificador = ?
                """, evento)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> banco.update("""
                DELETE FROM eventos_de_auditoria_da_automacao
                WHERE identificador = ?
                """, evento)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void deveExigirDuasConfirmacoesParaAtivarConcurso() throws Exception {
        MockHttpSession sessaoWeb = criarContaEEntrar(
                "confirmacao.reforcada@example.com");
        UUID usuario = identificarUsuario("confirmacao.reforcada@example.com");
        VinculoCriado vinculo = vincular(sessaoWeb, 676767L, 676767L);
        var concurso = estruturaDeConcursos.criarConcurso(usuario,
                "Concurso reforcado", null, null, null,
                SituacaoDoConcurso.PLANEJADO, null);
        Map<String, Object> proposta = Map.of(
                "identificadorDoConcurso", concurso.identificador().toString(),
                "impacto", "Ativar concurso");
        String versoes = json.writeValueAsString(operacoesCriticas.versoesAtuais(
                usuario, concurso.identificador()));
        var preparada = operacoes.prepararParaConfirmacaoReforcada(usuario,
                vinculo.identificadorDoVinculo(), "ATIVACAO_DO_CONCURSO",
                "Ativar concurso", json.writeValueAsString(proposta), versoes,
                "teste-confirmacao-reforcada");

        var primeira = aplicacao.confirmarComResultado(
                preparada.operacao().identificador(),
                preparada.codigoDeConfirmacao(), "TEXTO", IDENTIFICADOR_DO_BOT,
                676767L, 676767L, vinculo.identificadorDaSessao(), "update-1");
        assertThat(primeira.exigeNovaConfirmacao()).isTrue();
        assertThat(estruturaDeConcursos.obterConcurso(usuario,
                concurso.identificador()).ativo()).isFalse();

        var segunda = aplicacao.confirmarComResultado(
                preparada.operacao().identificador(), primeira.proximoCodigo(),
                "TEXTO", IDENTIFICADOR_DO_BOT, 676767L, 676767L,
                vinculo.identificadorDaSessao(), "update-2");
        assertThat(segunda.exigeNovaConfirmacao()).isFalse();
        assertThat(segunda.operacao().estado().name()).isEqualTo("APLICADA");
        assertThat(estruturaDeConcursos.obterConcurso(usuario,
                concurso.identificador()).ativo()).isTrue();
    }

    @Test
    void deveLimitarEValidarContextoDaSegundaConfirmacaoReforcada()
            throws Exception {
        MockHttpSession sessaoWeb = criarContaEEntrar(
                "contexto.reforcado@example.com");
        UUID usuario = identificarUsuario("contexto.reforcado@example.com");
        VinculoCriado vinculo = vincular(sessaoWeb, 686868L, 686868L);
        var concurso = estruturaDeConcursos.criarConcurso(usuario,
                "Concurso com contexto reforcado", null, null, null,
                SituacaoDoConcurso.PLANEJADO, null);
        Map<String, Object> proposta = Map.of(
                "identificadorDoConcurso", concurso.identificador().toString(),
                "impacto", "Ativar concurso");
        String versoes = json.writeValueAsString(operacoesCriticas.versoesAtuais(
                usuario, concurso.identificador()));
        var preparada = operacoes.prepararParaConfirmacaoReforcada(usuario,
                vinculo.identificadorDoVinculo(), "ATIVACAO_DO_CONCURSO",
                "Ativar concurso", json.writeValueAsString(proposta), versoes,
                "teste-contexto-confirmacao-reforcada");
        assertThatThrownBy(() -> operacoes.validarAtualidade(usuario,
                preparada.operacao().identificador(),
                preparada.operacao().assinatura(), versoes))
                .isInstanceOf(ConflitoDeDominio.class)
                .satisfies(excecao -> assertThat(
                        ((ConflitoDeDominio) excecao).codigo())
                        .isEqualTo("PREVIA_DE_AUTOMACAO_DESATUALIZADA"));
        assertThat(operacoes.validarAtualidade(usuario,
                preparada.operacao().identificador(),
                preparada.operacao().assinatura(), versoes, "REFORCADA")
                .identificador()).isEqualTo(
                        preparada.operacao().identificador());
        OffsetDateTime inicio = OffsetDateTime.now(ZoneOffset.UTC);

        var primeira = aplicacao.confirmarComResultado(
                preparada.operacao().identificador(),
                preparada.codigoDeConfirmacao(), "TEXTO", IDENTIFICADOR_DO_BOT,
                686868L, 686868L, vinculo.identificadorDaSessao(),
                "update-contexto-1");
        OffsetDateTime expiracaoDaSegundaEtapa = banco.queryForObject("""
                SELECT confirmacao_expira_em
                  FROM operacoes_assistidas WHERE identificador = ?
                """, OffsetDateTime.class,
                preparada.operacao().identificador());
        assertThat(expiracaoDaSegundaEtapa)
                .isAfter(inicio.plusMinutes(4))
                .isBeforeOrEqualTo(inicio.plusMinutes(5).plusSeconds(2));

        assertThatThrownBy(() -> aplicacao.confirmarComResultado(
                preparada.operacao().identificador(), primeira.proximoCodigo(),
                "VOZ", IDENTIFICADOR_DO_BOT, 686868L, 686868L,
                vinculo.identificadorDaSessao(), "update-contexto-2"))
                .isInstanceOf(RecursoNaoEncontrado.class);
        assertThatThrownBy(() -> aplicacao.confirmarComResultado(
                preparada.operacao().identificador(), primeira.proximoCodigo(),
                "TEXTO", IDENTIFICADOR_DO_BOT, 686868L, 686868L,
                vinculo.identificadorDaSessao(), "update-contexto-1"))
                .isInstanceOf(RecursoNaoEncontrado.class);

        var segunda = aplicacao.confirmarComResultado(
                preparada.operacao().identificador(), primeira.proximoCodigo(),
                "TEXTO", IDENTIFICADOR_DO_BOT, 686868L, 686868L,
                vinculo.identificadorDaSessao(), "update-contexto-2");
        assertThat(segunda.operacao().estado().name()).isEqualTo("APLICADA");
    }

    @Test
    void deveGerarTrocarEConsumirCodigoSemPersistirSegredosPuros()
            throws Exception {
        api.perform(post("/api/v1/integracoes/telegram/codigos-de-vinculo")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        MockHttpSession sessao = criarContaEEntrar("vinculo@example.com");
        api.perform(post("/api/v1/integracoes/telegram/codigos-de-vinculo")
                        .session(sessao))
                .andExpect(status().isForbidden());

        CodigoGerado gerado = gerarCodigo(sessao);
        assertThat(gerado.codigo()).matches("[23456789A-HJ-NP-Z]{10}");
        assertThat(banco.queryForObject("""
                SELECT codigo_de_vinculo_hash
                FROM vinculos_de_canal
                WHERE identificador = ?
                """, String.class, gerado.identificadorDoVinculo()))
                .hasSize(64)
                .isNotEqualTo(gerado.codigo());

        api.perform(get("/api/v1/integracoes/telegram/vinculo")
                        .session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDENTE"))
                .andExpect(jsonPath("$.identificadorExterno").value(nullValue()));

        String corpoValido = corpoDaTroca(
                gerado.codigo(), 998877L, 887766L);
        api.perform(postConfiavel(
                        "/api/v1/integracoes-confiaveis/telegram/vinculos",
                        corpoValido, "chave-incorreta", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo")
                        .value("ASSINATURA_DO_GATEWAY_INVALIDA"));

        String idempotenciaDaTroca = UUID.randomUUID().toString();
        String resposta = api.perform(postConfiavel(
                        "/api/v1/integracoes-confiaveis/telegram/vinculos",
                        corpoValido, IDENTIFICADOR_DA_CHAVE_DO_GATEWAY,
                        idempotenciaDaTroca))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.vinculo.estado").value("ATIVO"))
                .andExpect(jsonPath("$.vinculo.identificadorExterno").value(998877L))
                .andExpect(jsonPath("$.vinculo.identificadorDoChat").value(887766L))
                .andExpect(jsonPath("$.vinculo.provisionado").value(false))
                .andExpect(jsonPath("$.token", not(nullValue())))
                .andExpect(jsonPath("$.prefixo", not(nullValue())))
                .andExpect(jsonPath("$.escopos", hasSize(6)))
                .andExpect(jsonPath("$.escopos[0]").value("planejamento:ler"))
                .andExpect(jsonPath("$.escopos[1]").value("prioridades:ler"))
                .andExpect(jsonPath("$.escopos[2]").value("concursos:ler"))
                .andExpect(jsonPath("$.escopos[3]").value("estudos:ler"))
                .andExpect(jsonPath("$.escopos[4]").value("operacoes:ler"))
                .andExpect(jsonPath("$.escopos[5]").value("operacoes:preparar"))
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(resposta).get("token").asText();
        assertThat(token).startsWith("mcp_");
        assertThat(banco.queryForObject("""
                SELECT token_hash
                FROM credenciais_de_integracao
                WHERE vinculo_id = ?
                """, String.class, gerado.identificadorDoVinculo()))
                .hasSize(64)
                .isNotEqualTo(token);

        String repetida = api.perform(postConfiavel(
                        "/api/v1/integracoes-confiaveis/telegram/vinculos",
                        corpoValido, IDENTIFICADOR_DA_CHAVE_DO_GATEWAY,
                        idempotenciaDaTroca))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(repetida).get("token").asText()).isEqualTo(token);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM credenciais_de_integracao
                WHERE vinculo_id = ?
                """, Integer.class, gerado.identificadorDoVinculo())).isEqualTo(1);

        String agente = "agente-openclaw-vinculo";
        String sessaoDoAgente = "sessao-openclaw-vinculo";
        api.perform(postConfiavel(
                        "/api/v1/integracoes-confiaveis/telegram/vinculos/"
                                + gerado.identificadorDoVinculo()
                                + "/provisionamento",
                        corpoDoProvisionamento(998877L, 887766L, agente,
                                sessaoDoAgente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provisionado").value(true))
                .andExpect(jsonPath("$.identificadorDoAgente").value(agente));
        assertThat(banco.queryForObject("""
                SELECT identificador_da_sessao
                FROM vinculos_de_canal WHERE identificador = ?
                """, String.class, gerado.identificadorDoVinculo()))
                .isEqualTo(sessaoDoAgente);
    }

    @Test
    void deveValidarHmacReplayEIdempotenciaDoGateway() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("gateway@example.com");
        CodigoGerado codigo = gerarCodigo(sessao);
        String caminho = "/api/v1/integracoes-confiaveis/telegram/vinculos";
        String corpo = corpoDaTroca(codigo.codigo(), 212121L, 212121L);
        String idempotencia = "troca-gateway-212121";
        String nonce = UUID.randomUUID().toString().replace("-", "");
        long instante = Instant.now().getEpochSecond();

        String primeira = api.perform(postConfiavel(caminho, corpo,
                        IDENTIFICADOR_DA_CHAVE_DO_GATEWAY, idempotencia,
                        nonce, instante))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        api.perform(postConfiavel(caminho, corpo,
                        IDENTIFICADOR_DA_CHAVE_DO_GATEWAY, idempotencia,
                        nonce, instante))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo")
                        .value("REQUISICAO_DO_GATEWAY_REPETIDA"));

        String repetida = api.perform(postConfiavel(caminho, corpo,
                        IDENTIFICADOR_DA_CHAVE_DO_GATEWAY, idempotencia))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(repetida).get("token").asText()).isEqualTo(
                json.readTree(primeira).get("token").asText());

        api.perform(postConfiavel(caminho,
                        corpoDaTroca(codigo.codigo(), 212121L, 313131L),
                        IDENTIFICADOR_DA_CHAVE_DO_GATEWAY, idempotencia))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo")
                        .value("CHAVE_DE_IDEMPOTENCIA_REUTILIZADA"));
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM credenciais_de_integracao
                WHERE vinculo_id = ?
                """, Integer.class, codigo.identificadorDoVinculo()))
                .isEqualTo(1);
    }

    @Test
    void deveExpirarCodigoSemCriarCredencial() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("expiracao@example.com");
        UUID usuario = identificarUsuario("expiracao@example.com");
        UUID vinculo = UUID.randomUUID();
        String codigo = "ABC2345678";
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        banco.update("""
                INSERT INTO vinculos_de_canal (
                    identificador, usuario_id, canal, bot, estado,
                    codigo_de_vinculo_hash, codigo_expira_em,
                    criado_em, atualizado_em, versao)
                VALUES (?, ?, 'TELEGRAM', ?, 'PENDENTE', ?, ?, ?, ?, 0)
                """, vinculo, usuario, IDENTIFICADOR_DO_BOT,
                segredos.hash(codigo), agora.minusMinutes(10),
                agora.minusMinutes(20), agora.minusMinutes(20));

        assertThatThrownBy(() -> vinculos.trocarCodigo(
                codigo, IDENTIFICADOR_DO_BOT, 111222L, 111222L))
                .isInstanceOf(CodigoDeVinculoExpirado.class)
                .satisfies(excecao -> assertThat(
                        ((CodigoDeVinculoExpirado) excecao).codigo())
                        .isEqualTo("CODIGO_DE_VINCULO_EXPIRADO"));
        assertThat(banco.queryForObject("""
                SELECT estado FROM vinculos_de_canal WHERE identificador = ?
                """, String.class, vinculo)).isEqualTo("EXPIRADO");
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM credenciais_de_integracao
                WHERE vinculo_id = ?
                """, Integer.class, vinculo)).isZero();

        api.perform(get("/api/v1/integracoes/telegram/vinculo").session(sessao))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRevogarVinculoECredencialSemExcluirHistorico()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("revogacao@example.com");
        VinculoCriado criado = vincular(sessao, 444555L, 444555L);

        api.perform(delete("/api/v1/integracoes/telegram/vinculo")
                        .session(sessao).with(csrf()))
                .andExpect(status().isNoContent());
        api.perform(get("/api/v1/integracoes/telegram/vinculo").session(sessao))
                .andExpect(status().isNotFound());
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM credenciais_de_integracao
                WHERE vinculo_id = ? AND revogado_em IS NULL
                """, Integer.class, criado.identificadorDoVinculo())).isZero();
        assertThat(banco.queryForList("""
                SELECT acao FROM eventos_de_auditoria_da_automacao
                WHERE vinculo_id = ? ORDER BY ocorrido_em
                """, String.class, criado.identificadorDoVinculo()))
                .contains("CODIGO_DE_VINCULO_GERADO", "VINCULO_ATIVADO",
                        "VINCULO_REVOGADO");
    }

    @Test
    void deveProvisionarMesmoAgenteIdempotentementeERecusarOutraSessao()
            throws Exception {
        MockHttpSession sessaoWeb = criarContaEEntrar("provisionamento@example.com");
        VinculoCriado criado = vincular(sessaoWeb, 515151L, 515151L);
        String caminho = "/api/v1/integracoes-confiaveis/telegram/vinculos/"
                + criado.identificadorDoVinculo() + "/provisionamento";
        String mesmoCorpo = corpoDoProvisionamento(515151L, 515151L,
                criado.identificadorDoAgente(), criado.identificadorDaSessao());

        api.perform(postConfiavel(caminho, mesmoCorpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provisionado").value(true));
        api.perform(postConfiavel(caminho,
                        corpoDoProvisionamento(515151L, 515151L,
                                criado.identificadorDoAgente(), "outra-sessao")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo")
                        .value("VINCULO_JA_PROVISIONADO"));

        api.perform(get("/api/v1/integracoes/telegram/vinculo")
                        .session(sessaoWeb))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provisionado").value(true))
                .andExpect(jsonPath("$.identificadorDoAgente")
                        .value(criado.identificadorDoAgente()));
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM eventos_de_auditoria_da_automacao
                WHERE vinculo_id = ?
                  AND acao = 'AGENTE_OPENCLAW_PROVISIONADO'
                """, Integer.class, criado.identificadorDoVinculo()))
                .isEqualTo(1);
    }

    @Test
    void devePrepararRotacaoRevogandoAcessoAnteriorEExigirNovaConexao()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("rotacao@example.com");
        VinculoCriado anterior = vincular(sessao, 444555L, 444555L);

        String resposta = api.perform(post(
                        "/api/v1/integracoes/telegram/vinculo/rotacoes")
                        .session(sessao).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/v1/integracoes/telegram/vinculo"))
                .andExpect(jsonPath("$.codigo", not(nullValue())))
                .andExpect(jsonPath("$.vinculo.estado").value("PENDENTE"))
                .andReturn().getResponse().getContentAsString();
        var rotacao = json.readTree(resposta);
        String codigo = rotacao.get("codigo").asText();
        UUID novoVinculo = UUID.fromString(
                rotacao.get("vinculo").get("identificador").asText());

        assertThat(novoVinculo).isNotEqualTo(anterior.identificadorDoVinculo());
        assertThat(banco.queryForObject("""
                SELECT estado FROM vinculos_de_canal WHERE identificador = ?
                """, String.class, anterior.identificadorDoVinculo()))
                .isEqualTo("REVOGADO");
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM credenciais_de_integracao
                WHERE vinculo_id = ? AND revogado_em IS NULL
                """, Integer.class, anterior.identificadorDoVinculo())).isZero();

        String novaResposta = api.perform(postConfiavel(
                        "/api/v1/integracoes-confiaveis/telegram/vinculos",
                        corpoDaTroca(codigo, 444555L, 444555L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vinculo.identificador").value(
                        novoVinculo.toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(novaResposta).get("token").asText())
                .isNotEqualTo(anterior.token());
        assertThat(banco.queryForList("""
                SELECT fonte FROM eventos_de_auditoria_da_automacao
                WHERE acao = 'VINCULO_ATIVADO' ORDER BY ocorrido_em
                """, String.class)).containsOnly("TELEGRAM");
        assertThat(banco.queryForList("""
                SELECT acao FROM eventos_de_auditoria_da_automacao
                WHERE usuario_id = ? ORDER BY ocorrido_em
                """, String.class, identificarUsuario("rotacao@example.com")))
                .contains("CREDENCIAL_ANTERIOR_REVOGADA_PARA_ROTACAO",
                        "ROTACAO_DE_CREDENCIAL_PREPARADA");
    }

    @Test
    void deveIsolarVinculosEOperacoesEntreUsuariosAEB() throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("pessoa.a.automacao@example.com");
        MockHttpSession sessaoB = criarContaEEntrar("pessoa.b.automacao@example.com");
        UUID usuarioA = identificarUsuario("pessoa.a.automacao@example.com");
        UUID usuarioB = identificarUsuario("pessoa.b.automacao@example.com");
        VinculoCriado vinculoA = vincular(sessaoA, 101010L, 101010L);

        api.perform(get("/api/v1/integracoes/telegram/vinculo").session(sessaoB))
                .andExpect(status().isNotFound());

        var operacaoA = prepararOperacao(usuarioA,
                vinculoA.identificadorDoVinculo(), "chave-a", "{\"minutos\":30}");
        var operacaoB = prepararOperacao(usuarioB, null,
                "chave-b", "{\"minutos\":45}");

        api.perform(get("/api/v1/operacoes-assistidas").session(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeItens").value(1))
                .andExpect(jsonPath("$.itens[0].identificador")
                        .value(operacaoA.identificador().toString()));
        api.perform(get("/api/v1/operacoes-assistidas").session(sessaoB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeItens").value(1))
                .andExpect(jsonPath("$.itens[0].identificador")
                        .value(operacaoB.identificador().toString()));

        api.perform(get("/api/v1/operacoes-assistidas/{id}",
                        operacaoA.identificador()).session(sessaoB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo")
                        .value("OPERACAO_ASSISTIDA_NAO_ENCONTRADA"));
        assertThatThrownBy(() -> operacoes.preparar(usuarioB,
                vinculoA.identificadorDoVinculo(), "REGISTRAR_ESTUDO", "Resumo",
                "{}", "{}", "chave-cruzada"))
                .isInstanceOf(RecursoNaoEncontrado.class)
                .satisfies(excecao -> assertThat(((RecursoNaoEncontrado) excecao).codigo())
                        .isEqualTo("VINCULO_DO_TELEGRAM_NAO_ENCONTRADO"));
    }

    @Test
    void deveTratarIdempotenciaEAtualidadeDaOperacaoAssistida() throws Exception {
        criarContaEEntrar("idempotencia@example.com");
        UUID usuario = identificarUsuario("idempotencia@example.com");

        var primeira = operacoes.preparar(usuario, null, "REGISTRAR_ESTUDO",
                "Registrar estudo", "{\"minutos\":30}", "{\"plano\":1}",
                "mesma-chave");
        var repetida = operacoes.preparar(usuario, null, "REGISTRAR_ESTUDO",
                "Registrar estudo", "{\"minutos\":30}", "{\"plano\":1}",
                "mesma-chave");

        assertThat(repetida.identificador()).isEqualTo(primeira.identificador());
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM operacoes_assistidas
                WHERE usuario_id = ? AND chave_de_idempotencia = 'mesma-chave'
                """, Integer.class, usuario)).isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM eventos_de_auditoria_da_automacao
                WHERE operacao_assistida_id = ? AND acao = 'OPERACAO_PREPARADA'
                """, Integer.class, primeira.identificador())).isEqualTo(1);

        assertThatThrownBy(() -> operacoes.preparar(usuario, null,
                "REGISTRAR_ESTUDO", "Registrar estudo", "{\"minutos\":60}",
                "{\"plano\":1}", "mesma-chave"))
                .isInstanceOf(ConflitoDeDominio.class)
                .satisfies(excecao -> assertThat(((ConflitoDeDominio) excecao).codigo())
                        .isEqualTo("CHAVE_DE_IDEMPOTENCIA_REUTILIZADA"));

        assertThat(operacoes.validarAtualidade(usuario, primeira.identificador(),
                primeira.assinatura(), "{\"plano\":1}"))
                .extracting("identificador")
                .isEqualTo(primeira.identificador());
        assertThatThrownBy(() -> operacoes.validarAtualidade(usuario,
                primeira.identificador(), primeira.assinatura(), "{\"plano\":2}"))
                .isInstanceOf(ConflitoDeDominio.class)
                .satisfies(excecao -> assertThat(((ConflitoDeDominio) excecao).codigo())
                        .isEqualTo("PREVIA_DE_AUTOMACAO_DESATUALIZADA"));
        assertThatThrownBy(() -> operacoes.obter(
                UUID.randomUUID(), primeira.identificador()))
                .isInstanceOf(RecursoNaoEncontrado.class)
                .satisfies(excecao -> assertThat(((RecursoNaoEncontrado) excecao).codigo())
                        .isEqualTo("OPERACAO_ASSISTIDA_NAO_ENCONTRADA"));

        var preparada = operacoes.prepararParaConfirmacao(usuario, null,
                "REGISTRO_DE_ESTUDO", "Registrar estudo", "{\"minutos\":30}",
                "{\"topico\":1}", "confirmacao-idempotente");
        var repeticao = operacoes.prepararParaConfirmacao(usuario, null,
                "REGISTRO_DE_ESTUDO", "Registrar estudo", "{\"minutos\":30}",
                "{\"topico\":1}", "confirmacao-idempotente");
        assertThat(preparada.codigoDeConfirmacao()).hasSize(8)
                .isEqualTo(repeticao.codigoDeConfirmacao());
        assertThat(operacoes.obter(usuario,
                preparada.operacao().identificador()).estado().name())
                .isEqualTo("AGUARDANDO_CONFIRMACAO");
        assertThat(banco.queryForObject("""
                SELECT codigo_de_confirmacao_hash
                  FROM operacoes_assistidas WHERE identificador = ?
                """, String.class, preparada.operacao().identificador()))
                .hasSize(64).isNotEqualTo(preparada.codigoDeConfirmacao());
    }

    @Test
    void deveConfirmarEAplicarRegistroSemDuplicar() throws Exception {
        criarContaEEntrar("aplicacao@example.com");
        UUID usuario = identificarUsuario("aplicacao@example.com");
        var codigo = vinculos.gerarCodigo(usuario);
        var troca = vinculos.trocarCodigo(codigo.codigo(), IDENTIFICADOR_DO_BOT,
                998877L, 998877L);
        UUID vinculo = troca.vinculo().identificador();
        vinculos.registrarProvisionamento(vinculo, IDENTIFICADOR_DO_BOT,
                998877L, 998877L, "agente-aplicacao", "sessao-aplicacao");
        UUID materia = UUID.randomUUID();
        UUID topico = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        banco.update("""
                INSERT INTO materias (identificador, usuario_id, nome,
                  nome_normalizado, arquivada, criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Direito', 'direito', false, ?, ?, 0)
                """, materia, usuario, agora, agora);
        banco.update("""
                INSERT INTO topicos_da_materia (identificador, materia_id, nome,
                  nome_normalizado, ordem, arquivado, criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Atos', 'atos', 1, false, ?, ?, 0)
                """, topico, materia, agora, agora);
        Map<String, Object> proposta = Map.of(
                "identificadorDoTopico", topico.toString(),
                "dataHora", agora.toString(), "duracaoEmMinutos", 30,
                "tipoDeEstudo", "TEORIA");
        String propostaJson = json.writeValueAsString(proposta);
        String versoes = json.writeValueAsString(preparacoes.versoesAtuais(
                "REGISTRO_DE_ESTUDO", usuario, proposta));
        var preparada = operacoes.prepararParaConfirmacao(usuario, vinculo,
                "REGISTRO_DE_ESTUDO", "Registrar estudo de 30 minutos.",
                propostaJson, versoes, "aplicar-registro");

        var aplicada = aplicacao.confirmarEAplicar(
                preparada.codigoDeConfirmacao(), "TEXTO",
                IDENTIFICADOR_DO_BOT, 998877L, 998877L,
                "sessao-aplicacao", "update-aplicacao-1");
        assertThat(aplicada.estado().name()).isEqualTo("APLICADA");
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM registros_de_estudo WHERE topico_id = ?
                """, Integer.class, topico)).isEqualTo(1);
        assertThat(aplicacao.confirmarEAplicar(
                preparada.codigoDeConfirmacao(), "TEXTO",
                IDENTIFICADOR_DO_BOT, 998877L, 998877L,
                "sessao-aplicacao", "update-aplicacao-1").estado().name())
                .isEqualTo("APLICADA");
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM registros_de_estudo WHERE topico_id = ?
                """, Integer.class, topico)).isEqualTo(1);
    }

    @Test
    void devePermitirConfirmarECancelarOperacoesPelaWeb() throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("web.operacoes.a@example.com");
        MockHttpSession sessaoB = criarContaEEntrar("web.operacoes.b@example.com");
        UUID usuarioA = identificarUsuario("web.operacoes.a@example.com");
        UUID materia = UUID.randomUUID();
        UUID topico = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        banco.update("""
                INSERT INTO materias (identificador, usuario_id, nome,
                  nome_normalizado, arquivada, criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Direito', 'direito', false, ?, ?, 0)
                """, materia, usuarioA, agora, agora);
        banco.update("""
                INSERT INTO topicos_da_materia (identificador, materia_id, nome,
                  nome_normalizado, ordem, arquivado, criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Atos', 'atos', 1, false, ?, ?, 0)
                """, topico, materia, agora, agora);
        Map<String, Object> proposta = Map.of(
                "identificadorDoTopico", topico.toString(),
                "dataHora", agora.toString(), "duracaoEmMinutos", 30,
                "tipoDeEstudo", "TEORIA");
        String propostaJson = json.writeValueAsString(proposta);
        String versoes = json.writeValueAsString(preparacoes.versoesAtuais(
                "REGISTRO_DE_ESTUDO", usuarioA, proposta));
        var confirmavel = operacoes.prepararParaConfirmacao(usuarioA, null,
                "REGISTRO_DE_ESTUDO", "Registrar estudo de 30 minutos.",
                propostaJson, versoes, "confirmacao-web");

        api.perform(post("/api/v1/operacoes-assistidas/{id}/confirmacao-web",
                        confirmavel.operacao().identificador()).session(sessaoA))
                .andExpect(status().isForbidden());
        api.perform(post("/api/v1/operacoes-assistidas/{id}/confirmacao-web",
                        confirmavel.operacao().identificador()).session(sessaoB)
                        .with(csrf()))
                .andExpect(status().isNotFound());
        api.perform(post("/api/v1/operacoes-assistidas/{id}/confirmacao-web",
                        confirmavel.operacao().identificador()).session(sessaoA)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APLICADA"));
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM registros_de_estudo WHERE topico_id = ?
                """, Integer.class, topico)).isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM eventos_de_auditoria_da_automacao
                 WHERE operacao_assistida_id = ?
                   AND ator = 'USUARIO_WEB' AND fonte = 'WEB'
                   AND acao = 'OPERACAO_ASSISTIDA_APLICADA'
                """, Integer.class, confirmavel.operacao().identificador()))
                .isEqualTo(1);

        String versoesParaCancelamento = json.writeValueAsString(
                preparacoes.versoesAtuais("REGISTRO_DE_ESTUDO", usuarioA,
                        proposta));
        var cancelavel = operacoes.prepararParaConfirmacao(usuarioA, null,
                "REGISTRO_DE_ESTUDO", "Registrar estudo de 30 minutos.",
                propostaJson, versoesParaCancelamento, "cancelamento-web");
        api.perform(post("/api/v1/operacoes-assistidas/{id}/cancelamento",
                        cancelavel.operacao().identificador()).session(sessaoA)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM eventos_de_auditoria_da_automacao
                 WHERE operacao_assistida_id = ?
                   AND ator = 'USUARIO_WEB' AND fonte = 'WEB'
                   AND acao = 'OPERACAO_ASSISTIDA_CANCELADA'
                """, Integer.class, cancelavel.operacao().identificador()))
                .isEqualTo(1);

        var reforcada = operacoes.prepararParaConfirmacaoReforcada(usuarioA,
                null, "ATIVACAO_DO_CONCURSO", "Ativar concurso.", "{}", "{}",
                "confirmacao-web-reforcada");
        api.perform(post("/api/v1/operacoes-assistidas/{id}/confirmacao-web",
                        reforcada.operacao().identificador()).session(sessaoA)
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo")
                        .value("CONFIRMACAO_REFORCADA_EXIGE_TELEGRAM"));
    }

    @Test
    void deveRecusarPreviaDeConclusaoSemExecucaoDoBloco() throws Exception {
        criarContaEEntrar("previa.bloco.sem.execucao@example.com");
        UUID usuario = identificarUsuario("previa.bloco.sem.execucao@example.com");
        UUID plano = UUID.randomUUID();
        UUID bloco = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        banco.update("""
                INSERT INTO planos_semanais (identificador, usuario_id, data_inicial,
                  estado, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, 'ATIVO', ?, ?, 0)
                """, plano, usuario, LocalDate.of(2026, 7, 20), agora, agora);
        banco.update("""
                INSERT INTO blocos_de_estudo (identificador, plano_id, titulo,
                  tipo_de_atividade, data, duracao_prevista_em_minutos, ordem,
                  estado, criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Revisar lei', 'REVISAO', ?, 20, 1, 'PLANEJADO',
                  ?, ?, 0)
                """, bloco, plano, LocalDate.of(2026, 7, 20), agora, agora);
        var identidade = new IdentidadeDaIntegracaoMcp(usuario, null,
                UUID.randomUUID(), IDENTIFICADOR_DO_BOT, 998879L,
                "agente-sem-execucao", "sessao-sem-execucao", 0,
                Set.of("operacoes:preparar"));
        var contexto = new ContextoDaChamadaMcp(identidade, UUID.randomUUID(),
                "update-sem-execucao");

        assertThatThrownBy(() -> preparacoes.preparar("CONCLUSAO_DO_BLOCO",
                contexto, Map.of("identificadorDoBloco", bloco.toString(),
                        "duracaoExecutadaEmMinutos", 20,
                        "observacao", "Revisao concluida.")))
                .isInstanceOf(RecursoNaoEncontrado.class)
                .satisfies(excecao -> assertThat(
                        ((RecursoNaoEncontrado) excecao).codigo())
                        .isEqualTo("EXECUCAO_DO_BLOCO_NAO_ENCONTRADA"));
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM operacoes_assistidas WHERE usuario_id = ?
                """, Integer.class, usuario)).isZero();
    }

    @Test
    void deveRejeitarEvidenciaIncoerenteAntesDePrepararOperacao()
            throws Exception {
        criarContaEEntrar("evidencia-previa@example.com");
        UUID usuario = identificarUsuario("evidencia-previa@example.com");
        var codigo = vinculos.gerarCodigo(usuario);
        var troca = vinculos.trocarCodigo(codigo.codigo(), IDENTIFICADOR_DO_BOT,
                998878L, 998878L);
        UUID vinculo = troca.vinculo().identificador();
        vinculos.registrarProvisionamento(vinculo, IDENTIFICADOR_DO_BOT,
                998878L, 998878L, "agente-evidencia", "sessao-evidencia");
        UUID materia = UUID.randomUUID();
        UUID topico = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        banco.update("""
                INSERT INTO materias (identificador, usuario_id, nome,
                  nome_normalizado, arquivada, criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Direito', 'direito', false, ?, ?, 0)
                """, materia, usuario, agora, agora);
        banco.update("""
                INSERT INTO topicos_da_materia (identificador, materia_id, nome,
                  nome_normalizado, ordem, arquivado, criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Autarquias', 'autarquias', 1, false, ?, ?, 0)
                """, topico, materia, agora, agora);
        var identidade = new IdentidadeDaIntegracaoMcp(usuario, vinculo,
                UUID.randomUUID(), IDENTIFICADOR_DO_BOT, 998878L,
                "agente-evidencia", "sessao-evidencia", 0,
                Set.of("estudos:escrever"));
        var contexto = new ContextoDaChamadaMcp(
                identidade, UUID.randomUUID(), "update-evidencia");
        Map<String, Object> argumentos = Map.of(
                "identificadorDoTopico", topico.toString(),
                "dataHora", agora.toString(),
                "duracaoEmMinutos", 25,
                "tipoDeEstudo", "QUESTOES",
                "evidencia", Map.of(
                        "quantidadeDeQuestoes", 7,
                        "quantidadeDeAcertos", 6,
                        "padroesDeErro", List.of(
                                Map.of("descricao", "Regime das autarquias",
                                        "quantidadeDeOcorrencias", 1),
                                Map.of("descricao", "Tipos de descentralizacao",
                                        "quantidadeDeOcorrencias", 1))));

        assertThatThrownBy(() -> preparacoes.preparar(
                "REGISTRO_DE_ESTUDO", contexto, argumentos))
                .isInstanceOf(RegraDeDominio.class)
                .hasMessageContaining(
                        "A soma dos padroes nao pode superar a quantidade de erros");
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM operacoes_assistidas
                 WHERE usuario_id = ?
                """, Integer.class, usuario)).isZero();
    }

    @Test
    void deveSerializarPreparacoesConcorrentesComMesmaChave() throws Exception {
        criarContaEEntrar("concorrencia@example.com");
        UUID usuario = identificarUsuario("concorrencia@example.com");
        CountDownLatch inicio = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var tarefa = (java.util.concurrent.Callable<UUID>) () -> {
                inicio.await();
                return operacoes.preparar(usuario, null, "REGISTRAR_ESTUDO",
                        "Registrar estudo", "{\"minutos\":30}",
                        "{\"plano\":1}", "chave-concorrente").identificador();
            };
            var primeira = executor.submit(tarefa);
            var segunda = executor.submit(tarefa);
            inicio.countDown();

            assertThat(primeira.get()).isEqualTo(segunda.get());
        }
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM operacoes_assistidas
                WHERE usuario_id = ?
                  AND chave_de_idempotencia = 'chave-concorrente'
                """, Integer.class, usuario)).isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM eventos_de_auditoria_da_automacao
                WHERE usuario_id = ? AND acao = 'OPERACAO_PREPARADA'
                """, Integer.class, usuario)).isEqualTo(1);
    }

    @Test
    void deveRejeitarRestricoesDeUnicidadeJsonEProvisionamentoDaV17()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("restricoes@example.com");
        UUID usuario = identificarUsuario("restricoes@example.com");
        CodigoGerado primeiro = gerarCodigo(sessao);

        assertThatThrownBy(() -> inserirVinculoPendente(
                UUID.randomUUID(), usuario, "outro-hash"))
                .isInstanceOf(DataAccessException.class);

        var operacao = prepararOperacao(usuario, null,
                "chave-unica", "{\"valor\":1}");
        assertThatThrownBy(() -> banco.update("""
                UPDATE operacoes_assistidas
                SET proposta_canonica = '[]'::jsonb
                WHERE identificador = ?
                """, operacao.identificador()))
                .isInstanceOf(DataAccessException.class);

        banco.update("""
                UPDATE vinculos_de_canal
                SET estado = 'REVOGADO', revogado_em = now()
                WHERE identificador = ?
                """, primeiro.identificadorDoVinculo());
        UUID vinculoComChatDistinto = UUID.randomUUID();
        assertThat(banco.update("""
                INSERT INTO vinculos_de_canal (
                    identificador, usuario_id, canal, bot,
                    identificador_externo, identificador_do_chat, estado,
                    codigo_de_vinculo_hash, codigo_expira_em,
                    codigo_consumido_em, criado_em, atualizado_em, versao)
                VALUES (?, ?, 'TELEGRAM', ?, 123, 456, 'ATIVO', ?,
                    now() + interval '10 minutes', now(), now(), now(), 0)
                """, vinculoComChatDistinto, usuario, IDENTIFICADOR_DO_BOT,
                "hash-conversa-privada")).isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT identificador_do_chat
                FROM vinculos_de_canal
                WHERE identificador = ?
                """, Long.class, vinculoComChatDistinto)).isEqualTo(456L);
        assertThat(banco.queryForObject("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'vinculos_de_canal'
                  AND column_name = 'bot'
                """, String.class)).isEqualTo("NO");

        assertThatThrownBy(() -> banco.update("""
                UPDATE vinculos_de_canal
                SET identificador_do_agente = 'agente-sem-sessao'
                WHERE identificador = ?
                """, primeiro.identificadorDoVinculo()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void deveDocumentarContratosWebDaAutomacaoNoOpenApi() throws Exception {
        String corpo = api.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode documento = json.readTree(corpo);

        validarOperacaoOpenApi(documento.at(
                        "/paths/~1api~1v1~1integracoes~1telegram~1"
                                + "codigos-de-vinculo/post"),
                true, "201", "RespostaDeCodigoDeVinculo",
                List.of("400", "401", "403", "409", "422"));
        validarOperacaoOpenApi(documento.at(
                        "/paths/~1api~1v1~1integracoes~1telegram~1vinculo/get"),
                false, "200", "RespostaDeVinculoDoTelegram",
                List.of("401", "403", "404"));
        validarOperacaoOpenApi(documento.at(
                        "/paths/~1api~1v1~1integracoes~1telegram~1vinculo/delete"),
                true, "204", null, List.of("400", "401", "403", "404"));
        validarOperacaoOpenApi(documento.at(
                        "/paths/~1api~1v1~1integracoes~1telegram~1vinculo~1rotacoes/post"),
                true, "201", "RespostaDeCodigoDeVinculo",
                List.of("400", "401", "403", "404", "422"));
        validarOperacaoOpenApi(documento.at(
                        "/paths/~1api~1v1~1operacoes-assistidas/get"),
                false, "200", "RespostaPaginada",
                List.of("400", "401", "403"));
        validarOperacaoOpenApi(documento.at(
                        "/paths/~1api~1v1~1operacoes-assistidas~1{identificador}/get"),
                false, "200", "RespostaDeOperacaoAssistida",
                List.of("400", "401", "403", "404"));
        validarOperacaoOpenApi(documento.at(
                        "/paths/~1api~1v1~1operacoes-assistidas~1{identificador}"
                                + "~1confirmacao-web/post"),
                true, "200", "RespostaDeOperacaoAssistida",
                List.of("401", "403", "404", "409", "422"));
        validarOperacaoOpenApi(documento.at(
                        "/paths/~1api~1v1~1operacoes-assistidas~1{identificador}"
                                + "~1cancelamento/post"),
                true, "200", "RespostaDeOperacaoAssistida",
                List.of("401", "403", "404", "409"));

        assertThat(documento.at(
                "/paths/~1api~1v1~1integracoes-confiaveis~1telegram~1vinculos")
                .isMissingNode()).isTrue();
        assertThat(documento.path("components").path("schemas").propertyNames())
                .contains("RespostaDeCodigoDeVinculo",
                        "RespostaDeVinculoDoTelegram",
                        "RespostaResumidaDeOperacaoAssistida",
                        "RespostaDeOperacaoAssistida", "RespostaDeErro");
    }

    private CodigoGerado gerarCodigo(MockHttpSession sessao) throws Exception {
        String resposta = api.perform(post(
                        "/api/v1/integracoes/telegram/codigos-de-vinculo")
                        .session(sessao).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/v1/integracoes/telegram/vinculo"))
                .andExpect(jsonPath("$.codigo", not(nullValue())))
                .andExpect(jsonPath("$.expiraEm", not(nullValue())))
                .andExpect(jsonPath("$.vinculo.estado").value("PENDENTE"))
                .andReturn().getResponse().getContentAsString();
        var raiz = json.readTree(resposta);
        return new CodigoGerado(raiz.get("codigo").asText(),
                UUID.fromString(raiz.get("vinculo").get("identificador").asText()));
    }

    private VinculoCriado vincular(MockHttpSession sessao, long telegram, long chat)
            throws Exception {
        CodigoGerado codigo = gerarCodigo(sessao);
        String resposta = api.perform(postConfiavel(
                        "/api/v1/integracoes-confiaveis/telegram/vinculos",
                        corpoDaTroca(codigo.codigo(), telegram, chat)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String agente = "agente-" + codigo.identificadorDoVinculo();
        String identificadorDaSessao =
                "sessao-" + codigo.identificadorDoVinculo();
        api.perform(postConfiavel(
                        "/api/v1/integracoes-confiaveis/telegram/vinculos/"
                                + codigo.identificadorDoVinculo()
                                + "/provisionamento",
                        corpoDoProvisionamento(
                                telegram, chat, agente, identificadorDaSessao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provisionado").value(true));
        return new VinculoCriado(codigo.identificadorDoVinculo(),
                json.readTree(resposta).get("token").asText(), agente,
                identificadorDaSessao);
    }

    private br.com.trilhaaprovacao.automacao.dominio.OperacaoAssistida
            prepararOperacao(UUID usuario, UUID vinculo, String chave,
                    String proposta) {
        return operacoes.preparar(usuario, vinculo, "REGISTRAR_ESTUDO",
                "Registrar estudo", proposta, "{\"plano\":1}", chave);
    }

    private MockHttpSession criarContaEEntrar(String email) throws Exception {
        api.perform(post("/api/v1/autenticacao/cadastro").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Pessoa","email":"%s",
                                 "senha":"senha-segura-123"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return (MockHttpSession) api.perform(post("/api/v1/autenticacao/login")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"senha-segura-123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private UUID identificarUsuario(String email) {
        return banco.queryForObject("""
                SELECT identificador FROM usuarios WHERE email = ?
                """, UUID.class, email);
    }

    private String corpoDaTroca(String codigo, long telegram, long chat) {
        return """
                {"codigo":"%s","identificadorDoBot":%d,
                 "identificadorDoTelegram":%d,"identificadorDoChat":%d}
                """.formatted(codigo, IDENTIFICADOR_DO_BOT, telegram, chat);
    }

    private String corpoDoProvisionamento(long telegram, long chat,
            String agente, String sessao) {
        return """
                {"identificadorDoBot":%d,"identificadorDoTelegram":%d,
                 "identificadorDoChat":%d,"identificadorDoAgente":"%s",
                 "identificadorDaSessao":"%s"}
                """.formatted(IDENTIFICADOR_DO_BOT, telegram, chat,
                        agente, sessao);
    }

    private MockHttpServletRequestBuilder postConfiavel(
            String caminho, String corpo) {
        return postConfiavel(caminho, corpo,
                IDENTIFICADOR_DA_CHAVE_DO_GATEWAY,
                UUID.randomUUID().toString());
    }

    private MockHttpServletRequestBuilder postConfiavel(String caminho,
            String corpo, String chave, String idempotencia) {
        return postConfiavel(caminho, corpo, chave, idempotencia,
                UUID.randomUUID().toString().replace("-", ""),
                Instant.now().getEpochSecond());
    }

    private MockHttpServletRequestBuilder postConfiavel(String caminho,
            String corpo, String chave, String idempotencia, String nonce,
            long instante) {
        String hashDoCorpo = sha256(corpo);
        String canonico = "TRILHA-HMAC-V1\n" + chave + "\n" + instante
                + "\n" + nonce + "\nPOST\n" + caminho + "\n"
                + hashDoCorpo + "\n" + idempotencia;
        return post(caminho)
                .header(ValidadorDeAssinaturaDoGateway.CABECALHO_DA_CHAVE,
                        chave)
                .header(ValidadorDeAssinaturaDoGateway.CABECALHO_DO_INSTANTE,
                        String.valueOf(instante))
                .header(ValidadorDeAssinaturaDoGateway.CABECALHO_DO_NONCE,
                        nonce)
                .header(ValidadorDeAssinaturaDoGateway.CABECALHO_DA_ASSINATURA,
                        hmac(canonico))
                .header(ValidadorDeAssinaturaDoGateway.CABECALHO_DA_IDEMPOTENCIA,
                        idempotencia)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo);
    }

    private String sha256(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException excecao) {
            throw new IllegalStateException("SHA-256 indisponivel.", excecao);
        }
    }

    private String hmac(String valor) {
        try {
            Mac autenticador = Mac.getInstance("HmacSHA256");
            autenticador.init(new SecretKeySpec(
                    SEGREDO_DO_GATEWAY.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return HexFormat.of().formatHex(autenticador.doFinal(
                    valor.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException excecao) {
            throw new IllegalStateException("HMAC-SHA-256 indisponivel.", excecao);
        }
    }

    private void inserirVinculoPendente(UUID identificador, UUID usuario,
            String hash) {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        banco.update("""
                INSERT INTO vinculos_de_canal (
                    identificador, usuario_id, canal, bot, estado,
                    codigo_de_vinculo_hash, codigo_expira_em,
                    criado_em, atualizado_em, versao)
                VALUES (?, ?, 'TELEGRAM', ?, 'PENDENTE', ?, ?, ?, ?, 0)
                """, identificador, usuario, IDENTIFICADOR_DO_BOT, hash,
                agora.plusMinutes(10), agora, agora);
    }

    private void validarOperacaoOpenApi(JsonNode operacao, boolean exigeCsrf,
            String sucesso, String fragmentoDoEsquema,
            List<String> respostasDeErro) {
        assertThat(operacao.isMissingNode()).isFalse();
        assertThat(operacao.path("summary").asString()).isNotBlank();
        assertThat(operacao.path("tags").valueStream().map(JsonNode::asString))
                .contains("Automação assistida");
        assertThat(operacao.at("/security/0/sessao").isMissingNode()).isFalse();
        assertThat(operacao.at("/security/0/csrf").isMissingNode())
                .isEqualTo(!exigeCsrf);

        JsonNode respostaDeSucesso = operacao.at("/responses/" + sucesso);
        assertThat(respostaDeSucesso.isMissingNode()).isFalse();
        if (fragmentoDoEsquema != null) {
            assertThat(respostaDeSucesso.path("content").valueStream()
                    .map(conteudo -> conteudo.at("/schema/$ref").asString()))
                    .anyMatch(referencia -> referencia.contains(fragmentoDoEsquema));
        }
        respostasDeErro.forEach(codigo -> assertThat(operacao
                .at("/responses/" + codigo + "/content").valueStream()
                .map(conteudo -> conteudo.at("/schema/$ref").asString()))
                .as("resposta HTTP %s da automacao", codigo)
                .anyMatch(referencia -> referencia.endsWith("/RespostaDeErro")));
    }

    private record CodigoGerado(String codigo, UUID identificadorDoVinculo) {
    }

    private record VinculoCriado(UUID identificadorDoVinculo, String token,
            String identificadorDoAgente, String identificadorDaSessao) {
    }
}
