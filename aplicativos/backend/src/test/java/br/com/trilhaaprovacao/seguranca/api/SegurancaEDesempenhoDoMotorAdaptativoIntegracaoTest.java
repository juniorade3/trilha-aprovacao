package br.com.trilhaaprovacao.seguranca.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.trilhaaprovacao.evidencias.aplicacao.ConsultaDeDiagnosticoDeTopicos;
import br.com.trilhaaprovacao.priorizacao.aplicacao.ConsultaDePriorizacaoDeTopicos;
import br.com.trilhaaprovacao.revisoes.aplicacao.ConsultaDeRevisoesEspacadas;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "debug=false")
@AutoConfigureMockMvc
@Testcontainers
class SegurancaEDesempenhoDoMotorAdaptativoIntegracaoTest {
    private static final Logger LOG = LoggerFactory.getLogger(
            SegurancaEDesempenhoDoMotorAdaptativoIntegracaoTest.class);
    private static final LocalDate REFERENCIA = LocalDate.of(2026, 7, 21);
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 7, 20);

    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_seguranca_desempenho")
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
    @MockitoSpyBean JdbcTemplate banco;
    @Autowired ConsultaDeDiagnosticoDeTopicos diagnostico;
    @Autowired ConsultaDePriorizacaoDeTopicos priorizacao;
    @Autowired ConsultaDeRevisoesEspacadas revisoes;

    @BeforeEach
    void limparBanco() {
        banco.execute("TRUNCATE TABLE usuarios CASCADE");
        clearInvocations(banco);
    }

    @Test
    void deveExigirAutenticacaoEmTodaLeituraEValidarCsrfAntesDaEscrita()
            throws Exception {
        List<String> leituras = List.of(
                "/api/v1/estudos",
                "/api/v1/evidencias/diagnostico-de-topicos"
                        + "?dataDeReferencia=2026-07-21",
                "/api/v1/priorizacao-de-topicos?dataDeReferencia=2026-07-21",
                "/api/v1/revisoes-espacadas"
                        + "?dataDeReferencia=2026-07-21&ate=2026-07-28",
                "/api/v1/planos-semanais?dataInicial=2026-07-20",
                "/api/v1/planejamento/hoje?data=2026-07-21");
        for (String leitura : leituras) {
            api.perform(get(leitura))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("AUTENTICACAO_NECESSARIA"));
        }

        MockHttpSession sessao = criarContaEEntrar("csrf.motor@example.com");
        List<EscritaSemCsrf> escritas = List.of(
                new EscritaSemCsrf("POST", "/api/v1/estudos", "{}"),
                new EscritaSemCsrf("POST", "/api/v1/planos-semanais", "{}"),
                new EscritaSemCsrf("PUT",
                        "/api/v1/estudos/" + UUID.randomUUID() + "/correcao", "{}"),
                new EscritaSemCsrf("POST",
                        "/api/v1/blocos-de-estudo/" + UUID.randomUUID() + "/conclusao",
                        "{}"),
                new EscritaSemCsrf("POST",
                        "/api/v1/planos-semanais/" + UUID.randomUUID()
                                + "/geracao-deterministica/previa",
                        "{}"));
        for (EscritaSemCsrf escrita : escritas) {
            var pedido = escrita.metodo().equals("PUT")
                    ? put(escrita.caminho()) : post(escrita.caminho());
            api.perform(pedido.session(sessao)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(escrita.corpo()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
        }
    }

    @Test
    void deveRevogarAcessoDaSessaoQuandoAContaForInativada()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("sessao.inativa@example.com");
        banco.update("""
                UPDATE usuarios SET situacao = 'INATIVO'
                WHERE email = 'sessao.inativa@example.com'
                """);

        api.perform(get("/api/v1/estudos").session(sessao))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo")
                        .value("USUARIO_DA_SESSAO_NAO_ENCONTRADO"));
    }

    @Test
    void deveOcultarRecursosAlheiosEIsolarConsultasAgregadas()
            throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("isolamento.a@example.com");
        MockHttpSession sessaoB = criarContaEEntrar("isolamento.b@example.com");
        UUID usuarioA = usuario("isolamento.a@example.com");
        UUID usuarioB = usuario("isolamento.b@example.com");

        String materiaA = criarMateria(sessaoA, "Materia A");
        String topicoA = criarTopico(sessaoA, materiaA, "Topico secreto A");
        String materiaB = criarMateria(sessaoB, "Materia B");
        String topicoB = criarTopico(sessaoB, materiaB, "Topico visivel B");
        String estudoA = registrarEstudo(sessaoA, topicoA);
        String planoA = criarPlano(sessaoA);
        alterarDisponibilidade(sessaoA, planoA);
        String blocoA = criarBloco(sessaoA, planoA, materiaA, topicoA);
        inserirContextoOficial(usuarioA, UUID.fromString(materiaA),
                UUID.fromString(topicoA), "a");
        inserirContextoOficial(usuarioB, UUID.fromString(materiaB),
                UUID.fromString(topicoB), "b");

        api.perform(get("/api/v1/estudos/{id}", estudoA).session(sessaoB))
                .andExpect(status().isNotFound());
        api.perform(put("/api/v1/estudos/{id}/correcao", estudoA)
                        .session(sessaoB).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoEstudo(topicoB)))
                .andExpect(status().isNotFound());
        api.perform(post("/api/v1/estudos/{id}/cancelamento", estudoA)
                        .session(sessaoB).with(csrf()))
                .andExpect(status().isNotFound());

        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", planoA)
                        .session(sessaoB).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades()))
                .andExpect(status().isNotFound());
        api.perform(post("/api/v1/planos-semanais/{id}/blocos", planoA)
                        .session(sessaoB).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoBloco(materiaB, topicoB)))
                .andExpect(status().isNotFound());
        api.perform(get("/api/v1/blocos-de-estudo/{id}/execucao", blocoA)
                        .session(sessaoB))
                .andExpect(status().isNotFound());
        api.perform(post("/api/v1/blocos-de-estudo/{id}/inicio", blocoA)
                        .session(sessaoB).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataDeReferencia\":\"2026-07-20\"}"))
                .andExpect(status().isNotFound());
        api.perform(post(
                        "/api/v1/planos-semanais/{id}/geracao-deterministica/previa",
                        planoA).session(sessaoB).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dataDeReferencia":"2026-07-20",
                                 "duracaoDoBlocoPrincipalEmMinutos":50}
                                """))
                .andExpect(status().isNotFound());
        api.perform(post("/api/v1/planos-semanais/{id}/replanejamento/previa",
                        planoA).session(sessaoB).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dataDeReferencia":"2026-07-20",
                                 "identificadoresDasPendenciasIgnoradas":[]}
                                """))
                .andExpect(status().isNotFound());

        api.perform(get("/api/v1/evidencias/diagnostico-de-topicos")
                        .session(sessaoB)
                        .param("dataDeReferencia", REFERENCIA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].identificadorDoTopico").value(topicoB));
        api.perform(get("/api/v1/evidencias/diagnostico-de-topicos")
                        .session(sessaoB)
                        .param("dataDeReferencia", REFERENCIA.toString())
                        .param("identificadorDaMateria", materiaA))
                .andExpect(status().isNotFound());
        api.perform(get("/api/v1/priorizacao-de-topicos").session(sessaoB)
                        .param("dataDeReferencia", REFERENCIA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.topicosExigidos").value(1))
                .andExpect(jsonPath("$.materias[0].topicos[0].id")
                        .value(topicoB));
        api.perform(get("/api/v1/priorizacao-de-topicos").session(sessaoB)
                        .param("dataDeReferencia", REFERENCIA.toString())
                        .param("identificadorDaMateria", materiaA))
                .andExpect(status().isNotFound());
        api.perform(get("/api/v1/revisoes-espacadas").session(sessaoB)
                        .param("dataDeReferencia", REFERENCIA.toString())
                        .param("ate", REFERENCIA.plusDays(60).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisoes").isEmpty());
        api.perform(get("/api/v1/planejamento/hoje").session(sessaoB)
                        .param("data", SEGUNDA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SEM_PLANO"));
    }

    @Test
    void deveManterConstraintsChavesEstrangeirasEIndicesCriticos() {
        List<String> constraints = banco.queryForList("""
                SELECT conname
                FROM pg_constraint
                WHERE connamespace = 'public'::regnamespace
                """, String.class);
        assertThat(constraints).contains(
                "registros_topico_fk",
                "registros_material_fk",
                "registros_origem_fk",
                "registros_origem_unica",
                "fk_evidencias_registro",
                "uk_evidencias_registro",
                "ck_evidencias_questoes",
                "fk_padroes_usuario",
                "fk_padroes_topico",
                "uk_padroes_usuario_topico_descricao",
                "fk_ocorrencias_evidencia",
                "fk_ocorrencias_padrao",
                "uk_ocorrencias_evidencia_padrao",
                "uk_execucoes_bloco",
                "fk_execucoes_registro_de_estudo",
                "uk_execucoes_registro_de_estudo",
                "uk_prioridades_plano_materia",
                "fk_requisicoes_idempotentes_estudo_usuario",
                "fk_requisicoes_idempotentes_estudo_registro",
                "uk_requisicoes_idempotentes_estudo_usuario_chave");

        List<String> indices = banco.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                """, String.class);
        assertThat(indices).contains(
                "materias_usuario_arquivada_indice",
                "topicos_materia_arquivado_indice",
                "itens_edital_indice",
                "mapeamentos_item_topico_unico",
                "materiais_estudo_usuario_arquivado_indice",
                "coberturas_material_topico_unico",
                "idx_registros_topico_data_situacao",
                "uk_evidencias_registro",
                "uk_padroes_usuario_topico_descricao",
                "idx_ocorrencias_padrao_evidencia",
                "uk_planos_semanais_usuario_data_nao_cancelado",
                "idx_blocos_plano_data_ordem",
                "idx_blocos_topico",
                "uk_execucoes_usuario_em_andamento",
                "idx_execucoes_registro_de_estudo");
    }

    @Test
    void deveAnalisarPlanosDasConsultasCriticasComMassaRepresentativa() {
        UUID usuarioAlvo = UUID.randomUUID();
        UUID materiaAlvo = UUID.randomUUID();
        UUID usuarioRuido = UUID.randomUUID();
        UUID materiaRuido = UUID.randomUUID();
        inserirUsuario(usuarioAlvo, "desempenho.alvo@example.com");
        inserirUsuario(usuarioRuido, "desempenho.ruido@example.com");
        inserirMassaDoUsuario(usuarioAlvo, materiaAlvo, "alvo", true);
        inserirMassaDoUsuario(usuarioRuido, materiaRuido, "ruido", false);
        banco.execute("ANALYZE");

        List<PlanoExecutado> planos = new ArrayList<>();
        planos.add(executarEExplicar(
                sql -> sql.contains("FROM padroes_de_erro p")
                        && sql.contains("HAVING COUNT(DISTINCT e.identificador) >= 2"),
                () -> {
                    var resultado = diagnostico.consultar(
                            usuarioAlvo, REFERENCIA, null, false);
                    assertThat(resultado).hasSize(500);
                },
                "diagnostico-padroes"));
        planos.add(executarEExplicar(
                sql -> sql.contains("COALESCE(SUM(f.quantidade_de_questoes), 0)")
                        && sql.contains("FROM topicos_da_materia t"),
                () -> diagnostico.consultar(usuarioAlvo, REFERENCIA, null, false),
                "diagnostico-topicos"));
        planos.add(executarEExplicar(
                sql -> sql.contains("WITH itens_oficiais AS")
                        && sql.contains("fatos_agregados AS"),
                () -> {
                    var resultado = priorizacao.consultar(
                            usuarioAlvo, REFERENCIA, null);
                    assertThat(resultado.resumo().topicosExigidos())
                            .isEqualTo(500);
                },
                "priorizacao"));
        planos.add(executarEExplicar(
                sql -> sql.contains("WITH topicos_oficiais AS")
                        && sql.contains("blocos_abertos AS"),
                () -> {
                    var resultado = revisoes.consultar(
                            usuarioAlvo, REFERENCIA, REFERENCIA.plusDays(60));
                    assertThat(resultado.revisoes()).hasSize(500);
                },
                "revisoes"));

        assertThat(planos).allSatisfy(plano -> {
            assertThat(plano.linhas()).isNotEmpty();
            assertThat(plano.texto()).contains("Planning Time:", "Execution Time:");
        });
    }

    private PlanoExecutado executarEExplicar(Predicate<String> seletor,
            Runnable consulta, String nome) {
        clearInvocations(banco);
        consulta.run();
        Invocation chamada = mockingDetails(banco).getInvocations().stream()
                .filter(invocacao -> invocacao.getArguments().length > 0)
                .filter(invocacao -> invocacao.getArgument(0) instanceof String)
                .filter(invocacao -> seletor.test(invocacao.getArgument(0)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "A consulta SQL critica nao foi capturada: " + nome));
        String sql = chamada.getArgument(0);
        Object[] parametros = parametrosDaChamada(chamada);
        List<String> linhas = banco.query(
                "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + sql,
                (resultado, linha) -> resultado.getString(1), parametros);
        PlanoExecutado plano = new PlanoExecutado(nome, List.copyOf(linhas));
        LOG.info("EXPLAIN ANALYZE [{}]\n{}", nome, plano.texto());
        return plano;
    }

    private Object[] parametrosDaChamada(Invocation chamada) {
        Object[] argumentos = chamada.getArguments();
        Object ultimo = argumentos[argumentos.length - 1];
        if (ultimo instanceof Object[] parametros) {
            return parametros;
        }
        return Arrays.copyOfRange(argumentos, 2, argumentos.length);
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

    private UUID usuario(String email) {
        return banco.queryForObject(
                "SELECT identificador FROM usuarios WHERE email = ?",
                UUID.class, email);
    }

    private String criarMateria(MockHttpSession sessao, String nome)
            throws Exception {
        return identificador(api.perform(post("/api/v1/materias")
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String criarTopico(MockHttpSession sessao, String materia, String nome)
            throws Exception {
        return identificador(api.perform(
                        post("/api/v1/materias/{id}/topicos", materia)
                                .session(sessao).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"nome":"%s","ordem":1}
                                        """.formatted(nome)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String registrarEstudo(MockHttpSession sessao, String topico)
            throws Exception {
        return identificador(api.perform(post("/api/v1/estudos")
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoEstudo(topico)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String corpoDoEstudo(String topico) {
        return """
                {"identificadorDoTopico":"%s","tipoDeEstudo":"QUESTOES",
                 "dataHora":"2026-07-20T10:00:00-03:00",
                 "duracaoEmMinutos":30,
                 "evidencia":{"resultadoDeQuestoes":{
                   "quantidadeDeQuestoes":20,"quantidadeDeAcertos":15}}}
                """.formatted(topico);
    }

    private String criarPlano(MockHttpSession sessao) throws Exception {
        return identificador(api.perform(post("/api/v1/planos-semanais")
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataInicial\":\"" + SEGUNDA + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private void alterarDisponibilidade(MockHttpSession sessao, String plano)
            throws Exception {
        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades()))
                .andExpect(status().isOk());
    }

    private String disponibilidades() {
        StringBuilder corpo = new StringBuilder("{\"disponibilidades\":[");
        for (int dia = 0; dia < 7; dia++) {
            if (dia > 0) {
                corpo.append(',');
            }
            corpo.append("""
                    {"data":"%s","minutosDisponiveis":%d}
                    """.formatted(SEGUNDA.plusDays(dia), dia == 0 ? 120 : 0));
        }
        return corpo.append("]}").toString();
    }

    private String criarBloco(MockHttpSession sessao, String plano,
            String materia, String topico) throws Exception {
        return identificador(api.perform(
                        post("/api/v1/planos-semanais/{id}/blocos", plano)
                                .session(sessao).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpoDoBloco(materia, topico)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String corpoDoBloco(String materia, String topico) {
        return """
                {"titulo":"Bloco protegido","tipoDeAtividade":"TEORIA",
                 "data":"2026-07-20","duracaoPrevistaEmMinutos":60,"ordem":1,
                 "identificadorDaMateria":"%s","identificadorDoTopico":"%s"}
                """.formatted(materia, topico);
    }

    private String identificador(String resposta) {
        return json.readTree(resposta).get("identificador").asString();
    }

    private void inserirContextoOficial(
            UUID usuario, UUID materia, UUID topico, String prefixo) {
        UUID concurso = UUID.randomUUID();
        UUID edital = UUID.randomUUID();
        UUID cargo = UUID.randomUUID();
        UUID prova = UUID.randomUUID();
        UUID grupo = UUID.randomUUID();
        UUID materiaDaProva = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        banco.update("""
                INSERT INTO concursos (identificador, usuario_id, nome,
                    nome_normalizado, situacao, ativo, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, 'PLANEJADO', TRUE, now(), now())
                """, concurso, usuario, "Concurso " + prefixo,
                "concurso " + prefixo);
        banco.update("""
                INSERT INTO editais (identificador, concurso_id, titulo,
                    principal, criado_em, atualizado_em)
                VALUES (?, ?, ?, TRUE, now(), now())
                """, edital, concurso, "Edital " + prefixo);
        banco.update("""
                INSERT INTO cargos_do_concurso (identificador, concurso_id,
                    nome, nome_normalizado, nivel_de_escolaridade, selecionado,
                    ordem, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, 'SUPERIOR', TRUE, 1, now(), now())
                """, cargo, concurso, "Cargo " + prefixo, "cargo " + prefixo);
        banco.update("""
                INSERT INTO provas (identificador, cargo_id, nome,
                    nome_normalizado, tipo, carater, ordem, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, 'OBJETIVA', 'CLASSIFICATORIO', 1, now(), now())
                """, prova, cargo, "Prova " + prefixo, "prova " + prefixo);
        banco.update("""
                INSERT INTO grupos_de_conteudo (identificador, prova_id, nome,
                    nome_normalizado, ordem, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, 1, now(), now())
                """, grupo, prova, "Grupo " + prefixo, "grupo " + prefixo);
        banco.update("""
                INSERT INTO materias_da_prova (identificador,
                    grupo_de_conteudo_id, materia_id, ordem, criado_em, atualizado_em)
                VALUES (?, ?, ?, 1, now(), now())
                """, materiaDaProva, grupo, materia);
        banco.update("""
                INSERT INTO itens_do_edital (identificador, edital_id,
                    materia_da_prova_id, descricao_original, ordem,
                    criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, 1, now(), now())
                """, item, edital, materiaDaProva, "Item " + prefixo);
        banco.update("""
                INSERT INTO mapeamentos_de_itens_do_edital (identificador,
                    item_do_edital_id, topico_da_materia_id, confirmado, criado_em)
                VALUES (?, ?, ?, TRUE, now())
                """, UUID.randomUUID(), item, topico);
    }

    private void inserirUsuario(UUID usuario, String email) {
        banco.update("""
                INSERT INTO usuarios (identificador, nome, email, senha_hash,
                    situacao, criado_em, atualizado_em)
                VALUES (?, 'Desempenho', ?, 'hash-inerte', 'ATIVO', now(), now())
                """, usuario, email);
    }

    private void inserirMassaDoUsuario(UUID usuario, UUID materia,
            String prefixo, boolean criarContexto) {
        banco.update("""
                INSERT INTO materias (identificador, usuario_id, nome,
                    nome_normalizado, arquivada, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, FALSE, now(), now())
                """, materia, usuario, "Materia " + prefixo, "materia " + prefixo);
        banco.update("""
                INSERT INTO topicos_da_materia (identificador, materia_id,
                    nome, nome_normalizado, ordem, arquivado, criado_em, atualizado_em)
                SELECT md5(? || '-topico-' || numero)::uuid, ?, 'Topico ' || numero,
                       'topico ' || numero, numero, FALSE, now(), now()
                FROM generate_series(1, 500) numero
                """, prefixo, materia);
        banco.update("""
                INSERT INTO registros_de_estudo (identificador, topico_id,
                    data_hora, duracao_em_minutos, situacao, tipo_de_estudo,
                    criado_em, atualizado_em)
                SELECT md5(? || '-registro-' || topico || '-' || evento)::uuid,
                       md5(? || '-topico-' || topico)::uuid,
                       TIMESTAMPTZ '2026-07-21 10:00:00-03'
                         - make_interval(days => evento - 1),
                       30, 'ATIVO',
                       CASE WHEN evento % 5 = 0 THEN 'REVISAO' ELSE 'QUESTOES' END,
                       now(), now()
                FROM generate_series(1, 500) topico
                CROSS JOIN generate_series(1, 30) evento
                """, prefixo, prefixo);
        banco.update("""
                INSERT INTO evidencias_de_aprendizagem (identificador,
                    registro_de_estudo_id, quantidade_de_questoes,
                    quantidade_de_acertos, nivel_de_recordacao,
                    dificuldade_percebida, criado_em, atualizado_em)
                SELECT md5(? || '-evidencia-' || topico || '-' || evento)::uuid,
                       md5(? || '-registro-' || topico || '-' || evento)::uuid,
                       20, 12 + (evento % 9),
                       CASE WHEN evento % 5 = 0 THEN 1 + (evento % 5) END,
                       1 + (evento % 5), now(), now()
                FROM generate_series(1, 500) topico
                CROSS JOIN generate_series(1, 30) evento
                """, prefixo, prefixo);
        banco.update("""
                INSERT INTO padroes_de_erro (identificador, usuario_id, topico_id,
                    descricao, descricao_normalizada, criado_em, atualizado_em)
                SELECT md5(? || '-padrao-' || topico)::uuid, ?,
                       md5(? || '-topico-' || topico)::uuid,
                       'Padrao ' || topico, 'padrao ' || topico, now(), now()
                FROM generate_series(1, 100) topico
                """, prefixo, usuario, prefixo);
        banco.update("""
                INSERT INTO ocorrencias_de_padrao_de_erro (identificador,
                    evidencia_id, padrao_de_erro_id, quantidade_de_ocorrencias,
                    criado_em)
                SELECT md5(? || '-ocorrencia-' || topico || '-' || evento)::uuid,
                       md5(? || '-evidencia-' || topico || '-' || evento)::uuid,
                       md5(? || '-padrao-' || topico)::uuid, evento, now()
                FROM generate_series(1, 100) topico
                CROSS JOIN generate_series(1, 2) evento
                """, prefixo, prefixo, prefixo);
        UUID material = UUID.randomUUID();
        banco.update("""
                INSERT INTO materiais_de_estudo (identificador, usuario_id,
                    titulo, tipo, arquivado, criado_em, atualizado_em)
                VALUES (?, ?, ?, 'PDF', FALSE, now(), now())
                """, material, usuario, "Material " + prefixo);
        banco.update("""
                INSERT INTO coberturas_de_topicos_por_material (identificador,
                    material_id, topico_id, criado_em)
                SELECT gen_random_uuid(), ?,
                       md5(? || '-topico-' || topico)::uuid, now()
                FROM generate_series(1, 500) topico
                """, material, prefixo);
        if (criarContexto) {
            inserirContextoDeMassa(usuario, materia, prefixo);
            inserirBlocosDeRevisaoDaMassa(usuario, materia, prefixo);
        }
    }

    private void inserirContextoDeMassa(
            UUID usuario, UUID materia, String prefixo) {
        UUID concurso = UUID.randomUUID();
        UUID edital = UUID.randomUUID();
        UUID cargo = UUID.randomUUID();
        UUID prova = UUID.randomUUID();
        UUID grupo = UUID.randomUUID();
        UUID materiaDaProva = UUID.randomUUID();
        banco.update("""
                INSERT INTO concursos (identificador, usuario_id, nome,
                    nome_normalizado, situacao, ativo, criado_em, atualizado_em)
                VALUES (?, ?, 'Concurso desempenho', 'concurso desempenho',
                    'PLANEJADO', TRUE, now(), now())
                """, concurso, usuario);
        banco.update("""
                INSERT INTO editais (identificador, concurso_id, titulo,
                    principal, criado_em, atualizado_em)
                VALUES (?, ?, 'Edital desempenho', TRUE, now(), now())
                """, edital, concurso);
        banco.update("""
                INSERT INTO cargos_do_concurso (identificador, concurso_id, nome,
                    nome_normalizado, nivel_de_escolaridade, selecionado, ordem,
                    criado_em, atualizado_em)
                VALUES (?, ?, 'Cargo desempenho', 'cargo desempenho', 'SUPERIOR',
                    TRUE, 1, now(), now())
                """, cargo, concurso);
        banco.update("""
                INSERT INTO provas (identificador, cargo_id, nome, nome_normalizado,
                    tipo, carater, ordem, criado_em, atualizado_em)
                VALUES (?, ?, 'Prova desempenho', 'prova desempenho', 'OBJETIVA',
                    'CLASSIFICATORIO', 1, now(), now())
                """, prova, cargo);
        banco.update("""
                INSERT INTO grupos_de_conteudo (identificador, prova_id, nome,
                    nome_normalizado, ordem, criado_em, atualizado_em)
                VALUES (?, ?, 'Grupo desempenho', 'grupo desempenho', 1, now(), now())
                """, grupo, prova);
        banco.update("""
                INSERT INTO materias_da_prova (identificador,
                    grupo_de_conteudo_id, materia_id, ordem, criado_em, atualizado_em)
                VALUES (?, ?, ?, 1, now(), now())
                """, materiaDaProva, grupo, materia);
        banco.update("""
                INSERT INTO itens_do_edital (identificador, edital_id,
                    materia_da_prova_id, descricao_original, ordem,
                    criado_em, atualizado_em)
                SELECT md5(? || '-item-' || numero)::uuid, ?, ?,
                       'Item ' || numero, numero, now(), now()
                FROM generate_series(1, 500) numero
                """, prefixo, edital, materiaDaProva);
        banco.update("""
                INSERT INTO mapeamentos_de_itens_do_edital (identificador,
                    item_do_edital_id, topico_da_materia_id, confirmado, criado_em)
                SELECT gen_random_uuid(), md5(? || '-item-' || numero)::uuid,
                       md5(? || '-topico-' || numero)::uuid, TRUE, now()
                FROM generate_series(1, 500) numero
                """, prefixo, prefixo);
    }

    private void inserirBlocosDeRevisaoDaMassa(
            UUID usuario, UUID materia, String prefixo) {
        UUID plano = UUID.randomUUID();
        banco.update("""
                INSERT INTO planos_semanais (identificador, usuario_id,
                    data_inicial, estado, criado_em, atualizado_em)
                VALUES (?, ?, DATE '2026-07-20', 'ATIVO', now(), now())
                """, plano, usuario);
        banco.update("""
                INSERT INTO blocos_de_estudo (identificador, plano_id,
                    materia_id, topico_id, titulo, tipo_de_atividade, data,
                    duracao_prevista_em_minutos, ordem, estado, origem,
                    criado_em, atualizado_em)
                SELECT gen_random_uuid(), ?, ?,
                       md5(? || '-topico-' || numero)::uuid,
                       'Revisao ' || numero, 'REVISAO', DATE '2026-07-21',
                       20, numero, 'PLANEJADO', 'GERADO_DETERMINISTICAMENTE',
                       now(), now()
                FROM generate_series(1, 100) numero
                """, plano, materia, prefixo);
    }

    private record EscritaSemCsrf(String metodo, String caminho, String corpo) {
    }

    private record PlanoExecutado(String nome, List<String> linhas) {
        String texto() {
            return String.join("\n", linhas);
        }
    }
}
