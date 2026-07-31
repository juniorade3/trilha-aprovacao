package br.com.trilhaaprovacao.importacaoedital.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeAplicacaoDeOperacoesAssistidas;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeImportacaoCompletaDoEditalMcp;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeVinculosDoTelegram;
import br.com.trilhaaprovacao.automacao.infraestrutura.ContextoDaChamadaMcp;
import br.com.trilhaaprovacao.automacao.infraestrutura.IdentidadeDaIntegracaoMcp;
import br.com.trilhaaprovacao.concursos.aplicacao.ServicoDaEstruturaDeConcursos;
import br.com.trilhaaprovacao.concursos.dominio.SituacaoDoConcurso;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeMaterias;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ServicoDeStagingDaImportacaoDeEdital;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
        "trilha.automacao.segredo-de-hash=segredo-importacao-testes-123456789",
        "trilha.automacao.identificador-da-chave-do-gateway=gateway-importacao-teste",
        "trilha.automacao.segredo-do-gateway=segredo-gateway-importacao-testes-123456789",
        "trilha.automacao.validade-do-codigo=PT10M"
})
@AutoConfigureMockMvc
@Testcontainers
class ImportacaoDeEditalIntegracaoTest {
    private static final long BOT = 123456789L;
    private static final long TELEGRAM = 919191L;
    private static final long CHAT = 919191L;
    private static final String SESSAO_DO_CANAL = "sessao-importacao-e2e";

    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_importacao")
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
    @Autowired ServicoDeImportacaoCompletaDoEditalMcp importacaoMcp;
    @Autowired ServicoDeAplicacaoDeOperacoesAssistidas confirmacoes;
    @Autowired ServicoDeStagingDaImportacaoDeEdital staging;
    @Autowired ServicoDaEstruturaDeConcursos estrutura;
    @Autowired ServicoDeMaterias materias;

    @BeforeEach
    void limparBanco() {
        banco.execute("TRUNCATE TABLE usuarios CASCADE");
    }

    @Test
    void importaSomenteCargoEscolhidoComConfirmacaoReforcadaEProveniencia()
            throws Exception {
        MockHttpSession sessaoWeb = criarContaEEntrar(
                "importacao.e2e@example.com");
        UUID usuario = identificarUsuario("importacao.e2e@example.com");
        String texto = fixture("edital-dois-cargos.txt");

        JsonNode recebida = enviarTexto(sessaoWeb, texto,
                "edital-dois-cargos.txt");
        UUID importacao = UUID.fromString(recebida.get("identificador").asText());
        assertThat(recebida.get("estado").asText())
                .isEqualTo("AGUARDANDO_SELECAO");
        assertThat(recebida.get("extracao").get("cargos")).hasSize(2);
        String cargo = chaveDoCargo(recebida, "Auditor Federal");

        api.perform(put("/api/v1/importacoes-de-edital/{id}/decisoes",
                        importacao).session(sessaoWeb).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "chaveDoCargoSelecionado", cargo,
                                "modo", "CRIAR_NOVO",
                                "politicaDeReutilizacao", "EXIGIR_DECISAO",
                                "versaoDaExtracao", 1,
                                "decisoesHumanas", Map.of(),
                                "recursosParaReutilizar", Map.of(),
                                "definirEditalComoPrincipal", true,
                                "selecionarCargoCriado", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDADA"))
                .andExpect(jsonPath("$.chaveDoCargoSelecionado").value(cargo));

        ContextoDaChamadaMcp contexto = contexto(usuario);
        Map<String, Object> argumentos = Map.of(
                "identificadorDaImportacao", importacao.toString(),
                "chaveDoCargoSelecionado", cargo,
                "modo", "CRIAR_NOVO",
                "politicaDeReutilizacao", "EXIGIR_DECISAO",
                "decisoes", Map.of(
                        "reutilizacoes", List.of(),
                        "definirEditalComoPrincipal", true,
                        "selecionarCargoCriado", true));
        var preparada = importacaoMcp.preparar(contexto, argumentos);
        UUID operacao = UUID.fromString(preparada.dados()
                .get("identificadorDaOperacao").toString());
        String primeiroCodigo = preparada.dados()
                .get("codigoDeConfirmacao").toString();
        assertThat(preparada.dados().get("nivelDeConfirmacao"))
                .isEqualTo("REFORCADA");
        assertThat(preparada.dados().get("nadaFoiAlterado")).isEqualTo(true);
        assertThat(banco.queryForObject(
                "SELECT count(*) FROM concursos", Integer.class)).isZero();

        var primeira = confirmacoes.confirmarComResultado(operacao,
                primeiroCodigo, "TEXTO", BOT, TELEGRAM, CHAT,
                SESSAO_DO_CANAL, "update-importacao-1");
        assertThat(primeira.exigeNovaConfirmacao()).isTrue();
        assertThat(banco.queryForObject(
                "SELECT count(*) FROM concursos", Integer.class)).isZero();

        var segunda = confirmacoes.confirmarComResultado(operacao,
                primeira.proximoCodigo(), "TEXTO", BOT, TELEGRAM, CHAT,
                SESSAO_DO_CANAL, "update-importacao-2");
        assertThat(segunda.exigeNovaConfirmacao()).isFalse();
        assertThat(segunda.operacao().estado().name()).isEqualTo("APLICADA");

        UUID concurso = banco.queryForObject(
                "SELECT identificador FROM concursos WHERE usuario_id = ?",
                UUID.class, usuario);
        assertThat(banco.queryForList(
                "SELECT nome FROM cargos_do_concurso WHERE concurso_id = ?",
                String.class, concurso)).containsExactly("Auditor Federal");
        assertThat(banco.queryForObject(
                "SELECT count(*) FROM materias", Integer.class)).isEqualTo(1);
        assertThat(banco.queryForObject(
                "SELECT count(*) FROM topicos_da_materia", Integer.class))
                .isEqualTo(2);
        assertThat(banco.queryForObject(
                "SELECT count(*) FROM itens_do_edital", Integer.class))
                .isEqualTo(2);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM proveniencias_da_importacao_do_edital
                WHERE importacao_id = ? AND usuario_id = ?
                """, Integer.class, importacao, usuario)).isGreaterThan(10);
        assertThat(banco.queryForObject("""
                SELECT dados -> 'versaoConfirmada' ->> 'versaoDaExtracao'
                FROM relatorios_da_importacao_do_edital
                WHERE importacao_id = ? AND usuario_id = ?
                """, String.class, importacao, usuario)).isEqualTo("1");

        api.perform(get("/api/v1/importacoes-de-edital/{id}/relatorio",
                        importacao).session(sessaoWeb))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacaoDoConcurso")
                        .value("PLANEJADO"))
                .andExpect(jsonPath("$.contagens.materiasCriadas").value(1))
                .andExpect(jsonPath("$.contagens.itensCriados").value(2));

        var repetida = importacaoMcp.preparar(
                new ContextoDaChamadaMcp(contexto.identidade(),
                        UUID.randomUUID(), "update-importacao-repetida"),
                argumentos);
        assertThat(repetida.dados().get("identificadorDaOperacao").toString())
                .isEqualTo(operacao.toString());
        assertThat(repetida.dados().get("estado")).isEqualTo("APLICADA");
        assertThat(banco.queryForObject(
                "SELECT count(*) FROM concursos", Integer.class)).isEqualTo(1);

        MockHttpSession outraSessao = criarContaEEntrar(
                "outra.pessoa.importacao@example.com");
        api.perform(get("/api/v1/importacoes-de-edital/{id}", importacao)
                        .session(outraSessao))
                .andExpect(status().isNotFound());
    }

    @Test
    void reaproveitaRecebimentoIdenticoSemDuplicarStaging() throws Exception {
        MockHttpSession sessao = criarContaEEntrar(
                "idempotencia.importacao@example.com");
        String texto = fixture("edital-textual-simples.txt");

        UUID primeira = UUID.fromString(enviarTexto(sessao, texto,
                "primeiro-nome.txt").get("identificador").asText());
        UUID segunda = UUID.fromString(enviarTexto(sessao, texto,
                "segundo-nome.txt").get("identificador").asText());

        assertThat(segunda).isEqualTo(primeira);
        assertThat(banco.queryForObject(
                "SELECT count(*) FROM importacoes_de_edital", Integer.class))
                .isEqualTo(1);
        assertThat(banco.queryForObject(
                "SELECT count(*) FROM versoes_da_extracao_do_edital",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void serializaRecebimentosConcorrentesDoMesmoArquivo() throws Exception {
        criarContaEEntrar("concorrencia.importacao@example.com");
        UUID usuario = identificarUsuario("concorrencia.importacao@example.com");
        byte[] conteudo = fixture("edital-textual-simples.txt")
                .getBytes(StandardCharsets.UTF_8);
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(() -> {
                prontas.countDown();
                iniciar.await();
                return staging.receber(usuario, "primeiro.txt", conteudo)
                        .identificador();
            });
            var segunda = executor.submit(() -> {
                prontas.countDown();
                iniciar.await();
                return staging.receber(usuario, "segundo.txt", conteudo)
                        .identificador();
            });
            prontas.await();
            iniciar.countDown();

            assertThat(primeira.get()).isEqualTo(segunda.get());
        }
        assertThat(banco.queryForObject(
                "SELECT count(*) FROM importacoes_de_edital", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void iaDesabilitadaNaoAlteraStagingEMantemEditorManual()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar(
                "ia.desabilitada.importacao@example.com");
        JsonNode recebida = enviarTexto(sessao,
                fixture("edital-textual-simples.txt"),
                "edital-textual-simples.txt");
        UUID importacao = UUID.fromString(
                recebida.get("identificador").asText());
        String cargo = recebida.get("extracao").get("cargos")
                .get(0).get("chave").asText();
        assertThat(recebida.get("interpretacaoAssistidaDisponivel")
                .asBoolean()).isFalse();

        api.perform(post(
                        "/api/v1/importacoes-de-edital/{id}/extracao-assistida",
                        importacao).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "versaoEsperada", 1,
                                "chaveDoCargoAlvo", cargo))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("IA_DESABILITADA"));

        assertThat(banco.queryForObject("""
                SELECT count(*) FROM versoes_da_extracao_do_edital
                WHERE importacao_id = ?
                """, Integer.class, importacao)).isEqualTo(1);
        api.perform(get("/api/v1/importacoes-de-edital/{id}", importacao)
                        .session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versaoAtualDaExtracao").value(1))
                .andExpect(jsonPath("$.extracao").exists());
    }

    @Test
    @SuppressWarnings("unchecked")
    void correcaoManualVersionaESanitizaProvenienciaDoNavegador()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar(
                "correcao.manual.importacao@example.com");
        JsonNode recebida = enviarTexto(sessao,
                fixture("edital-textual-simples.txt"),
                "edital-textual-simples.txt");
        UUID importacao = UUID.fromString(
                recebida.get("identificador").asText());
        Map<String, Object> extracao = json.convertValue(
                recebida.get("extracao"), Map.class);
        Map<String, Object> concurso =
                (Map<String, Object>) extracao.get("concurso");
        Map<String, Object> nome =
                (Map<String, Object>) concurso.get("nome");
        nome.put("valor", "Concurso corrigido pelo usuário");
        nome.put("confianca", 0.99);
        nome.put("inferido", false);
        nome.put("fonte", Map.of(
                "pagina", 999,
                "secao", "fonte forjada",
                "trecho", "trecho forjado"));

        api.perform(put("/api/v1/importacoes-de-edital/{id}/extracao",
                        importacao).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "versaoEsperada", 1,
                                "extracao", extracao))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versaoAtualDaExtracao").value(2))
                .andExpect(jsonPath("$.extracao.concurso.nome.valor")
                        .value("Concurso corrigido pelo usuário"))
                .andExpect(jsonPath("$.extracao.concurso.nome.confianca")
                        .value(1))
                .andExpect(jsonPath("$.extracao.concurso.nome.inferido")
                        .value(false))
                .andExpect(jsonPath(
                        "$.extracao.concurso.nome.fonte.pagina").isEmpty())
                .andExpect(jsonPath(
                        "$.extracao.concurso.nome.fonte.secao")
                        .value("Correção do usuário"));
        assertThat(banco.queryForObject("""
                SELECT versao_do_extrator
                FROM versoes_da_extracao_do_edital
                WHERE importacao_id = ? AND numero_da_versao = 2
                """, String.class, importacao)).isEqualTo("manual-1");
    }

    @Test
    void complementaConcursoEReutilizaMateriaSomenteComDecisaoExplicita()
            throws Exception {
        MockHttpSession sessaoWeb = criarContaEEntrar(
                "complemento.importacao@example.com");
        UUID usuario = identificarUsuario("complemento.importacao@example.com");
        UUID concurso = estrutura.criarConcurso(usuario,
                "Concurso existente", null, null, null,
                SituacaoDoConcurso.PLANEJADO, null).identificador();
        UUID materiaExistente = materias.criar(usuario,
                "Direito Administrativo", null, "#475569").identificador();
        String texto = fixture("edital-textual-simples.txt");
        String resposta = api.perform(post(
                        "/api/v1/importacoes-de-edital/textos")
                        .session(sessaoWeb).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "texto", texto,
                                "nomeDaFonte", "complemento.txt",
                                "modo", "COMPLEMENTAR_EXISTENTE",
                                "identificadorDoConcursoExistente",
                                concurso))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode recebida = json.readTree(resposta);
        UUID importacao = UUID.fromString(recebida.get("identificador").asText());
        String cargo = recebida.get("extracao").get("cargos").get(0)
                .get("chave").asText();
        String materia = recebida.get("extracao").get("materias").get(0)
                .get("chave").asText();
        ContextoDaChamadaMcp contexto = contexto(usuario);
        Map<String, Object> argumentos = Map.of(
                "identificadorDaImportacao", importacao.toString(),
                "chaveDoCargoSelecionado", cargo,
                "modo", "COMPLEMENTAR_EXISTENTE",
                "identificadorDoConcursoExistente", concurso.toString(),
                "politicaDeReutilizacao", "EXIGIR_DECISAO",
                "decisoes", Map.of(
                        "reutilizacoes", List.of(Map.of(
                                "chaveExtraida", materia,
                                "identificadorDoRecurso", materiaExistente)),
                        "definirEditalComoPrincipal", false,
                        "selecionarCargoCriado", false));
        var preparada = importacaoMcp.preparar(contexto, argumentos);
        UUID operacao = UUID.fromString(preparada.dados()
                .get("identificadorDaOperacao").toString());
        var primeira = confirmacoes.confirmarComResultado(operacao,
                preparada.dados().get("codigoDeConfirmacao").toString(),
                "TEXTO", BOT, TELEGRAM, CHAT, SESSAO_DO_CANAL,
                "update-complemento-1");
        confirmacoes.confirmarComResultado(operacao,
                primeira.proximoCodigo(), "TEXTO", BOT, TELEGRAM, CHAT,
                SESSAO_DO_CANAL, "update-complemento-2");

        assertThat(banco.queryForObject(
                "SELECT count(*) FROM concursos", Integer.class)).isEqualTo(1);
        assertThat(banco.queryForObject(
                "SELECT count(*) FROM materias", Integer.class)).isEqualTo(1);
        assertThat(banco.queryForObject(
                "SELECT count(*) FROM materias_da_prova", Integer.class))
                .isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM proveniencias_da_importacao_do_edital
                WHERE importacao_id = ? AND tipo_do_recurso = 'MATERIA'
                  AND recurso_id = ?
                """, Integer.class, importacao, materiaExistente)).isZero();
        assertThat(banco.queryForObject("""
                SELECT count(*)
                FROM relatorios_da_importacao_do_edital r,
                     jsonb_array_elements(r.dados -> 'reutilizacoes') item
                WHERE r.importacao_id = ?
                  AND item ->> 'tipo' IN ('CONCURSO', 'MATERIA')
                """, Integer.class, importacao)).isEqualTo(2);
        assertThat(banco.queryForObject("""
                SELECT jsonb_exists(
                    dados -> 'identificadoresCriados', 'concurso')
                FROM relatorios_da_importacao_do_edital
                WHERE importacao_id = ?
                """, Boolean.class, importacao)).isFalse();
    }

    @Test
    void falhaNoUltimoItemReverteTodaEstruturaEAuditoria() throws Exception {
        MockHttpSession sessaoWeb = criarContaEEntrar(
                "rollback.importacao@example.com");
        UUID usuario = identificarUsuario("rollback.importacao@example.com");
        JsonNode recebida = enviarTexto(sessaoWeb,
                fixture("edital-textual-simples.txt"), "rollback.txt");
        UUID importacao = UUID.fromString(recebida.get("identificador").asText());
        String cargo = recebida.get("extracao").get("cargos").get(0)
                .get("chave").asText();
        ContextoDaChamadaMcp contexto = contexto(usuario);
        Map<String, Object> argumentos = Map.of(
                "identificadorDaImportacao", importacao.toString(),
                "chaveDoCargoSelecionado", cargo,
                "modo", "CRIAR_NOVO",
                "politicaDeReutilizacao", "EXIGIR_DECISAO",
                "decisoes", Map.of());
        var preparada = importacaoMcp.preparar(contexto, argumentos);
        UUID operacao = UUID.fromString(preparada.dados()
                .get("identificadorDaOperacao").toString());
        var primeira = confirmacoes.confirmarComResultado(operacao,
                preparada.dados().get("codigoDeConfirmacao").toString(),
                "TEXTO", BOT, TELEGRAM, CHAT, SESSAO_DO_CANAL,
                "update-rollback-1");

        banco.execute("""
                CREATE FUNCTION falhar_item_importacao_teste()
                RETURNS trigger LANGUAGE plpgsql AS '
                BEGIN RAISE EXCEPTION ''falha controlada no item''; END'
                """);
        banco.execute("""
                CREATE TRIGGER falhar_item_importacao_teste
                BEFORE INSERT ON itens_do_edital
                FOR EACH ROW EXECUTE FUNCTION falhar_item_importacao_teste()
                """);
        try {
            assertThatThrownBy(() -> confirmacoes.confirmarComResultado(
                    operacao, primeira.proximoCodigo(), "TEXTO", BOT,
                    TELEGRAM, CHAT, SESSAO_DO_CANAL, "update-rollback-2"))
                    .isInstanceOf(RuntimeException.class);
            for (String tabela : List.of("concursos", "editais",
                    "cargos_do_concurso", "provas", "grupos_de_conteudo",
                    "materias", "topicos_da_materia", "itens_do_edital",
                    "proveniencias_da_importacao_do_edital",
                    "relatorios_da_importacao_do_edital")) {
                assertThat(banco.queryForObject(
                        "SELECT count(*) FROM " + tabela, Integer.class))
                        .as(tabela).isZero();
            }
        } finally {
            banco.execute("DROP TRIGGER IF EXISTS falhar_item_importacao_teste "
                    + "ON itens_do_edital");
            banco.execute("DROP FUNCTION IF EXISTS "
                    + "falhar_item_importacao_teste()");
        }
    }

    private JsonNode enviarTexto(MockHttpSession sessao, String texto,
            String nome) throws Exception {
        String resposta = api.perform(post(
                        "/api/v1/importacoes-de-edital/textos")
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "texto", texto,
                                "nomeDaFonte", nome,
                                "modo", "CRIAR_NOVO"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta);
    }

    private ContextoDaChamadaMcp contexto(UUID usuario) {
        var codigo = vinculos.gerarCodigo(usuario);
        var troca = vinculos.trocarCodigo(codigo.codigo(), BOT,
                TELEGRAM, CHAT);
        UUID vinculo = troca.vinculo().identificador();
        vinculos.registrarProvisionamento(vinculo, BOT, TELEGRAM, CHAT,
                "agente-importacao-e2e", SESSAO_DO_CANAL);
        var identidade = new IdentidadeDaIntegracaoMcp(usuario, vinculo,
                UUID.randomUUID(), BOT, TELEGRAM, "agente-importacao-e2e",
                SESSAO_DO_CANAL, 0, Set.of("operacoes:preparar"));
        return new ContextoDaChamadaMcp(identidade, UUID.randomUUID(),
                "update-importacao-preparacao");
    }

    private String chaveDoCargo(JsonNode importacao, String nome) {
        for (JsonNode cargo : importacao.get("extracao").get("cargos")) {
            if (nome.equals(cargo.get("nome").get("valor").asText())) {
                return cargo.get("chave").asText();
            }
        }
        throw new IllegalStateException("Cargo nao encontrado: " + nome);
    }

    private MockHttpSession criarContaEEntrar(String email) throws Exception {
        api.perform(post("/api/v1/autenticacao/cadastro").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Pessoa",
                                "email", email,
                                "senha", "senha-segura-123"))))
                .andExpect(status().isCreated());
        return (MockHttpSession) api.perform(
                        post("/api/v1/autenticacao/login").with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of(
                                        "email", email,
                                        "senha", "senha-segura-123"))))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private UUID identificarUsuario(String email) {
        return banco.queryForObject(
                "SELECT identificador FROM usuarios WHERE email = ?",
                UUID.class, email);
    }

    private static String fixture(String nome) throws Exception {
        try (var entrada = ImportacaoDeEditalIntegracaoTest.class
                .getResourceAsStream("/fixtures/editais/" + nome)) {
            if (entrada == null) throw new IllegalStateException(
                    "Fixture ausente: " + nome);
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
