package br.com.trilhaaprovacao.fluxo.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;
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

@SpringBootTest(properties = "debug=false")
@AutoConfigureMockMvc
@Testcontainers
class FluxoAdaptativoCompletoIntegracaoTest {
    private static final LocalDate HOJE = LocalDate.now();
    private static final LocalDate SEGUNDA =
            HOJE.minusDays(HOJE.getDayOfWeek().getValue() - 1L);
    private static final LocalDate DOMINGO = SEGUNDA.plusDays(6);
    private static final LocalDate PROXIMA_SEGUNDA = SEGUNDA.plusWeeks(1);

    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_fluxo_adaptativo")
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
        banco.execute("""
                TRUNCATE TABLE blocos_de_estudo, disponibilidades_do_dia, planos_semanais,
                    registros_de_estudo, coberturas_de_topicos_por_material,
                    materiais_de_estudo, mapeamentos_de_itens_do_edital, itens_do_edital,
                    materias_da_prova, grupos_de_conteudo, provas, cargos_do_concurso,
                    editais, concursos, topicos_da_materia, materias, usuarios CASCADE
                """);
    }

    @Test
    void novaEvidenciaDeveAlterarDiagnosticoRevisaoRankingEPlanoSemDuplicar()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar();
        String materia = criar(sessao, "/api/v1/materias",
                "{\"nome\":\"Direito Constitucional\"}");
        String consolidado = criarTopico(sessao, materia, "Controle concentrado", 1);
        String fraco = criarTopico(sessao, materia, "Direitos fundamentais", 2);
        String nuncaEstudado = criarTopico(sessao, materia, "Poder constituinte", 3);
        String material = criar(sessao, "/api/v1/materiais",
                "{\"titulo\":\"Curso completo\",\"tipo\":\"PDF\"}");
        vincularMaterial(sessao, material, consolidado);
        vincularMaterial(sessao, material, fraco);
        vincularMaterial(sessao, material, nuncaEstudado);
        String concurso = criarContextoOficial(
                sessao, materia, consolidado, fraco, nuncaEstudado);
        api.perform(post("/api/v1/concursos/{id}/ativacao", concurso)
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk());

        String estudoFraco = registrarQuestoesComPadrao(sessao, fraco, material,
                SEGUNDA, 10, 5, "Confusão entre direitos", 2);
        String segundoEstudoFraco = registrarQuestoesComPadrao(
                sessao, fraco, material, SEGUNDA.plusDays(1),
                10, 5, "  confusao entre DIREITOS ", 3);
        registrarQuestoes(sessao, consolidado, material, DOMINGO, 20, 18);

        JsonNode diagnosticoInicial = diagnostico(sessao, DOMINGO);
        JsonNode fracoInicial = localizar(
                diagnosticoInicial, "identificadorDoTopico", fraco);
        JsonNode nuncaInicial = localizar(
                diagnosticoInicial, "identificadorDoTopico", nuncaEstudado);
        assertThat(fracoInicial.get("percentualRecenteDeAcertos").decimalValue())
                .isEqualByComparingTo("50.00");
        assertThat(fracoInicial.get("ultimaRecordacao").isNull()).isTrue();
        assertThat(fracoInicial.get("padroesDeErroRepetidos").get(0)
                .get("quantidadeDeEvidencias").asInt()).isEqualTo(2);
        assertThat(fracoInicial.get("padroesDeErroRepetidos").get(0)
                .get("quantidadeDeOcorrencias").asInt()).isEqualTo(5);
        assertThat(nuncaInicial.get("quantidadeDeEvidencias").asInt()).isZero();

        JsonNode rankingInicial = ranking(sessao, DOMINGO);
        JsonNode prioridadeFracaInicial = localizarTopico(rankingInicial, fraco);
        JsonNode prioridadeNuncaInicial = localizarTopico(
                rankingInicial, nuncaEstudado);
        JsonNode prioridadeConsolidadaInicial = localizarTopico(
                rankingInicial, consolidado);
        assertThat(prioridadeFracaInicial.get("grupo").asString())
                .isEqualTo("FRAQUEZA");
        assertThat(prioridadeFracaInicial.get("faixa").asString())
                .isEqualTo("PRECISA_REFORCO");
        assertThat(prioridadeFracaInicial.get("indicadores")
                .get("quantidadePadroesRepetidos").asInt()).isEqualTo(1);
        assertThat(prioridadeNuncaInicial.get("grupo").asString())
                .isEqualTo("LACUNA");
        assertThat(prioridadeConsolidadaInicial.get("grupo").asString())
                .isEqualTo("CONSOLIDADO");

        JsonNode revisaoInicial = localizar(
                revisoes(sessao, DOMINGO, DOMINGO),
                "identificadorDoTopico", fraco);
        assertThat(revisaoInicial.get("situacao").asString()).isEqualTo("VENCIDA");
        assertThat(revisaoInicial.get("dataDevida").asString())
                .isEqualTo(SEGUNDA.plusDays(1).toString());

        String planoAtual = criarPlano(sessao, SEGUNDA);
        definirDisponibilidade(sessao, planoAtual, DOMINGO, 150);
        String blocoManual = criarBlocoManual(sessao, planoAtual, DOMINGO);
        String previaInicial = gerarPrevia(sessao, planoAtual, DOMINGO);
        assertThat(gerarPrevia(sessao, planoAtual, DOMINGO))
                .isEqualTo(previaInicial);
        JsonNode arvoreDaPreviaInicial = json.readTree(previaInicial);
        JsonNode revisaoSugeridaInicial = localizarBlocoSugerido(
                arvoreDaPreviaInicial, fraco, "REVISAO");
        assertThat(ordemDosBlocosSugeridos(arvoreDaPreviaInicial))
                .as("a revisao vencida precede a maior lacuna elegivel")
                .containsExactly("REVISAO:" + fraco,
                        "TEORIA:" + nuncaEstudado);
        assertThat(codigosDasJustificativas(revisaoSugeridaInicial))
                .contains("REVISAO_ESPECIFICA",
                        "REVISAO_DEVIDA_EM_" + SEGUNDA.plusDays(1));
        String proximoPlano = criarPlano(sessao, PROXIMA_SEGUNDA);
        definirDisponibilidades(sessao, proximoPlano, java.util.Map.of(
                PROXIMA_SEGUNDA, 100,
                PROXIMA_SEGUNDA.plusDays(6), 100));
        JsonNode previaAntesDaMelhora = json.readTree(
                gerarPrevia(sessao, proximoPlano, PROXIMA_SEGUNDA));
        JsonNode revisaoAntesDaMelhora = localizarBlocoSugerido(
                previaAntesDaMelhora, fraco, "REVISAO");
        assertThat(dataDoBlocoSugerido(previaAntesDaMelhora, revisaoAntesDaMelhora))
                .as("a revisao vencida entra no primeiro dia disponivel")
                .isEqualTo(PROXIMA_SEGUNDA);

        aplicarPrevia(sessao, planoAtual, DOMINGO, arvoreDaPreviaInicial, false);
        assertThat(banco.queryForObject("""
                SELECT COUNT(*) FROM blocos_de_estudo
                WHERE identificador = ?::uuid AND origem = 'MANUAL'
                """, Integer.class, blocoManual)).isEqualTo(1);

        api.perform(post("/api/v1/planos-semanais/{id}/ativacao", planoAtual)
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk());
        String blocoDaRevisao = banco.queryForObject("""
                SELECT identificador::text
                FROM blocos_de_estudo
                WHERE plano_id = ?::uuid AND topico_id = ?::uuid
                  AND tipo_de_atividade = 'REVISAO' AND estado = 'PLANEJADO'
                ORDER BY data, ordem LIMIT 1
                """, String.class, planoAtual, fraco);

        api.perform(post("/api/v1/blocos-de-estudo/{id}/inicio", blocoDaRevisao)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataDeReferencia\":\"" + DOMINGO + "\"}"))
                .andExpect(status().isOk());
        String primeiraConclusao = api.perform(
                        post("/api/v1/blocos-de-estudo/{id}/conclusao", blocoDaRevisao)
                                .session(sessao).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"duracaoExecutadaEmMinutos":20,
                                         "observacao":"Recordacao excelente",
                                         "evidencia":{"nivelDeRecordacao":5}}
                                        """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String registroDaRevisao = json.readTree(primeiraConclusao)
                .get("estudo").get("identificador").asString();
        String retryDaConclusao = api.perform(
                        post("/api/v1/blocos-de-estudo/{id}/conclusao", blocoDaRevisao)
                                .session(sessao).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"duracaoExecutadaEmMinutos":20,
                                         "observacao":"Recordacao excelente",
                                         "evidencia":{"nivelDeRecordacao":5}}
                                        """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(retryDaConclusao)
                .get("estudo").get("identificador").asString())
                .isEqualTo(registroDaRevisao);

        JsonNode fracoAposRevisao = localizar(
                diagnostico(sessao, DOMINGO), "identificadorDoTopico", fraco);
        assertThat(fracoAposRevisao.get("ultimaRecordacao").asInt()).isEqualTo(5);
        assertThat(fracoAposRevisao.get("percentualRecenteDeAcertos").decimalValue())
                .isEqualByComparingTo("50.00");
        JsonNode prioridadeAposRevisao = localizarTopico(
                ranking(sessao, DOMINGO), fraco);
        assertThat(prioridadeAposRevisao.get("faixa").asString())
                .as("o percentual baixo prevalece sobre a recordacao alta")
                .isEqualTo("PRECISA_REFORCO");

        corrigirQuestoes(sessao, estudoFraco, fraco, material, DOMINGO, 10, 10);
        corrigirQuestoes(
                sessao, segundoEstudoFraco, fraco, material, DOMINGO, 10, 10);
        assertThat(banco.queryForObject("""
                SELECT COUNT(*) FROM registros_de_estudo
                WHERE identificador IN (?::uuid, ?::uuid)
                  AND situacao = 'CORRIGIDO'
                """, Integer.class, estudoFraco, segundoEstudoFraco)).isEqualTo(2);

        JsonNode fracoCorrigido = localizar(
                diagnostico(sessao, PROXIMA_SEGUNDA),
                "identificadorDoTopico", fraco);
        assertThat(fracoCorrigido.get("quantidadeDeEvidencias").asInt()).isEqualTo(3);
        assertThat(fracoCorrigido.get("totaisHistoricos")
                .get("questoes").asLong()).isEqualTo(20);
        assertThat(fracoCorrigido.get("totaisHistoricos")
                .get("acertos").asLong()).isEqualTo(20);
        assertThat(fracoCorrigido.get("percentualRecenteDeAcertos").decimalValue())
                .isEqualByComparingTo("100.00");
        assertThat(fracoCorrigido.get("ultimaRecordacao").asInt()).isEqualTo(5);
        JsonNode prioridadeCorrigida = localizarTopico(
                ranking(sessao, PROXIMA_SEGUNDA), fraco);
        assertThat(prioridadeCorrigida.get("grupo").asString())
                .isEqualTo("CONSOLIDADO");
        assertThat(prioridadeCorrigida.get("faixa").asString())
                .isEqualTo("CONSOLIDADO");
        assertThat(prioridadeCorrigida.get("indicadores")
                .get("percentual").decimalValue()).isEqualByComparingTo("100.00");

        JsonNode proximaRevisao = localizar(
                revisoes(sessao, PROXIMA_SEGUNDA, PROXIMA_SEGUNDA.plusDays(6)),
                "identificadorDoTopico", fraco);
        assertThat(proximaRevisao.get("etapa").asInt()).isEqualTo(2);
        String dataDaProximaRevisao = proximaRevisao.get("dataDevida").asString();
        assertThat(dataDaProximaRevisao)
                .isEqualTo(LocalDate.parse(
                        proximaRevisao.get("ultimaRevisao").asString())
                        .plusDays(7).toString());
        assertThat(proximaRevisao.get("situacao").asString()).isEqualTo("FUTURA");

        String novaPrevia = gerarPrevia(
                sessao, proximoPlano, PROXIMA_SEGUNDA);
        JsonNode arvoreDaNovaPrevia = json.readTree(novaPrevia);
        assertThat(arvoreDaNovaPrevia.get("assinaturaDaPrevia").asString())
                .isNotEqualTo(previaAntesDaMelhora
                        .get("assinaturaDaPrevia").asString());
        JsonNode novaRevisaoSugerida = localizarBlocoSugerido(
                arvoreDaNovaPrevia, fraco, "REVISAO");
        assertThat(dataDoBlocoSugerido(
                arvoreDaNovaPrevia, novaRevisaoSugerida))
                .as("a nova recordacao move a revisao de segunda para a data devida")
                .isEqualTo(PROXIMA_SEGUNDA.plusDays(6));
        assertThat(codigosDasJustificativas(novaRevisaoSugerida))
                .contains("REVISAO_ESPECIFICA",
                        "REVISAO_DEVIDA_EM_" + dataDaProximaRevisao);
        assertThat(codigosDasJustificativas(novaRevisaoSugerida))
                .doesNotContain("REVISAO_DEVIDA_EM_" + SEGUNDA.plusDays(1));
        aplicarPrevia(sessao, proximoPlano, PROXIMA_SEGUNDA,
                arvoreDaNovaPrevia, false);
        assertThat(banco.queryForObject("""
                SELECT COUNT(*) FROM blocos_de_estudo
                WHERE plano_id = ?::uuid AND topico_id = ?::uuid
                  AND tipo_de_atividade = 'REVISAO'
                  AND estado IN ('PLANEJADO', 'EM_ANDAMENTO')
                """, Integer.class, proximoPlano, fraco)).isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT COUNT(*) FROM registros_de_estudo
                WHERE identificador = ?::uuid
                """, Integer.class, registroDaRevisao)).isEqualTo(1);
    }

    private MockHttpSession criarContaEEntrar() throws Exception {
        api.perform(post("/api/v1/autenticacao/cadastro").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Pessoa Auditada",
                                 "email":"fluxo.adaptativo@example.com",
                                 "senha":"senha-segura-123"}
                                """))
                .andExpect(status().isCreated());
        return (MockHttpSession) api.perform(
                        post("/api/v1/autenticacao/login").with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":"fluxo.adaptativo@example.com",
                                         "senha":"senha-segura-123"}
                                        """))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private String criarTopico(MockHttpSession sessao, String materia,
            String nome, int ordem) throws Exception {
        return criar(sessao, "/api/v1/materias/" + materia + "/topicos",
                """
                {"nome":"%s","ordem":%d}
                """.formatted(nome, ordem));
    }

    private void vincularMaterial(MockHttpSession sessao, String material,
            String topico) throws Exception {
        criar(sessao, "/api/v1/materiais/" + material + "/topicos",
                "{\"identificadorDoTopico\":\"" + topico + "\"}");
    }

    private String criarContextoOficial(MockHttpSession sessao, String materia,
            String consolidado, String fraco, String nuncaEstudado) throws Exception {
        String concurso = criar(sessao, "/api/v1/concursos",
                "{\"nome\":\"Concurso auditado\",\"situacao\":\"PLANEJADO\"}");
        String edital = criar(sessao, "/api/v1/concursos/" + concurso + "/editais",
                "{\"titulo\":\"Edital principal\"}");
        api.perform(post("/api/v1/editais/{id}/definicao-como-principal", edital)
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk());
        String cargo = criar(sessao, "/api/v1/concursos/" + concurso + "/cargos",
                """
                {"nome":"Analista","nivelDeEscolaridade":"SUPERIOR","ordem":1}
                """);
        api.perform(post("/api/v1/cargos/{id}/selecao", cargo)
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk());
        String prova = criar(sessao, "/api/v1/cargos/" + cargo + "/provas",
                """
                {"nome":"Prova objetiva","tipo":"OBJETIVA",
                 "carater":"CLASSIFICATORIO","ordem":1}
                """);
        String grupo = criar(sessao, "/api/v1/provas/" + prova + "/grupos",
                "{\"nome\":\"Conhecimentos específicos\",\"ordem\":1}");
        String materiaDaProva = criar(sessao,
                "/api/v1/grupos-de-conteudo/" + grupo + "/materias",
                "{\"identificadorDaMateria\":\"" + materia + "\",\"ordem\":1}");
        mapearItem(sessao, materiaDaProva, edital, consolidado,
                "Controle de constitucionalidade", 1);
        mapearItem(sessao, materiaDaProva, edital, fraco,
                "Direitos e garantias fundamentais", 2);
        mapearItem(sessao, materiaDaProva, edital, nuncaEstudado,
                "Poder constituinte", 3);
        return concurso;
    }

    private void mapearItem(MockHttpSession sessao, String materiaDaProva,
            String edital, String topico, String descricao, int ordem)
            throws Exception {
        String item = criar(sessao,
                "/api/v1/materias-da-prova/" + materiaDaProva + "/itens",
                """
                {"identificadorDoEdital":"%s","descricaoOriginal":"%s","ordem":%d}
                """.formatted(edital, descricao, ordem));
        criar(sessao, "/api/v1/itens-do-edital/" + item + "/mapeamentos",
                "{\"identificadorDoTopicoDaMateria\":\"" + topico + "\"}");
    }

    private String registrarQuestoes(MockHttpSession sessao, String topico,
            String material, LocalDate data, int questoes, int acertos)
            throws Exception {
        return criar(sessao, "/api/v1/estudos",
                """
                {"identificadorDoTopico":"%s","identificadorDoMaterial":"%s",
                 "tipoDeEstudo":"QUESTOES","dataHora":"%sT10:00:00-03:00",
                 "duracaoEmMinutos":50,
                 "evidencia":{"resultadoDeQuestoes":{
                   "quantidadeDeQuestoes":%d,"quantidadeDeAcertos":%d}}}
                """.formatted(topico, material, data, questoes, acertos));
    }

    private String registrarQuestoesComPadrao(MockHttpSession sessao,
            String topico, String material, LocalDate data, int questoes,
            int acertos, String descricaoDoPadrao, int ocorrencias)
            throws Exception {
        return criar(sessao, "/api/v1/estudos",
                """
                {"identificadorDoTopico":"%s","identificadorDoMaterial":"%s",
                 "tipoDeEstudo":"QUESTOES","dataHora":"%sT10:00:00-03:00",
                 "duracaoEmMinutos":50,
                 "evidencia":{
                   "resultadoDeQuestoes":{
                     "quantidadeDeQuestoes":%d,"quantidadeDeAcertos":%d},
                   "padroesDeErro":[{
                     "descricao":"%s","quantidadeDeOcorrencias":%d}]}}
                """.formatted(topico, material, data, questoes, acertos,
                        descricaoDoPadrao, ocorrencias));
    }

    private void corrigirQuestoes(MockHttpSession sessao, String estudo,
            String topico, String material, LocalDate data, int questoes,
            int acertos) throws Exception {
        api.perform(put("/api/v1/estudos/{id}/correcao", estudo)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identificadorDoTopico":"%s",
                                 "identificadorDoMaterial":"%s",
                                 "tipoDeEstudo":"QUESTOES",
                                 "dataHora":"%sT10:00:00-03:00",
                                 "duracaoEmMinutos":50,
                                 "evidencia":{"resultadoDeQuestoes":{
                                   "quantidadeDeQuestoes":%d,
                                   "quantidadeDeAcertos":%d}}}
                                """.formatted(
                                topico, material, data, questoes, acertos)))
                .andExpect(status().isOk());
    }

    private JsonNode diagnostico(MockHttpSession sessao, LocalDate referencia)
            throws Exception {
        return respostaJson(api.perform(get(
                        "/api/v1/evidencias/diagnostico-de-topicos")
                        .param("dataDeReferencia", referencia.toString())
                        .param("somenteExigidosNoConcursoAtivo", "true")
                        .session(sessao))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode ranking(MockHttpSession sessao, LocalDate referencia)
            throws Exception {
        return respostaJson(api.perform(get("/api/v1/priorizacao-de-topicos")
                        .param("dataDeReferencia", referencia.toString())
                        .session(sessao))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode revisoes(MockHttpSession sessao, LocalDate referencia,
            LocalDate ate) throws Exception {
        JsonNode resposta = respostaJson(api.perform(
                        get("/api/v1/revisoes-espacadas")
                                .param("dataDeReferencia", referencia.toString())
                                .param("ate", ate.toString())
                                .session(sessao))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return resposta.get("revisoes");
    }

    private String criarPlano(MockHttpSession sessao, LocalDate inicio)
            throws Exception {
        return criar(sessao, "/api/v1/planos-semanais",
                "{\"dataInicial\":\"" + inicio + "\"}");
    }

    private void definirDisponibilidade(MockHttpSession sessao, String plano,
            LocalDate diaComMinutos, int minutos) throws Exception {
        definirDisponibilidades(sessao, plano,
                java.util.Map.of(diaComMinutos, minutos));
    }

    private void definirDisponibilidades(MockHttpSession sessao, String plano,
            java.util.Map<LocalDate, Integer> minutosPorDia) throws Exception {
        LocalDate inicio = minutosPorDia.keySet().iterator().next()
                .with(java.time.DayOfWeek.MONDAY);
        StringBuilder corpo = new StringBuilder("{\"disponibilidades\":[");
        for (int indice = 0; indice < 7; indice++) {
            if (indice > 0) {
                corpo.append(',');
            }
            LocalDate data = inicio.plusDays(indice);
            corpo.append("{\"data\":\"").append(data)
                    .append("\",\"minutosDisponiveis\":")
                    .append(minutosPorDia.getOrDefault(data, 0))
                    .append('}');
        }
        corpo.append("]}");
        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo.toString()))
                .andExpect(status().isOk());
    }

    private String criarBlocoManual(MockHttpSession sessao, String plano,
            LocalDate data) throws Exception {
        return criar(sessao, "/api/v1/planos-semanais/" + plano + "/blocos",
                """
                {"titulo":"Reflexao semanal","tipoDeAtividade":"OUTRA",
                 "data":"%s","duracaoPrevistaEmMinutos":30,"ordem":1}
                """.formatted(data));
    }

    private String gerarPrevia(MockHttpSession sessao, String plano,
            LocalDate referencia) throws Exception {
        return api.perform(post(
                        "/api/v1/planos-semanais/{id}/geracao-deterministica/previa",
                        plano).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dataDeReferencia":"%s",
                                 "duracaoDoBlocoPrincipalEmMinutos":50,
                                 "quantidadeDeMateriasPorDia":1}
                                """.formatted(referencia)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void aplicarPrevia(MockHttpSession sessao, String plano,
            LocalDate referencia, JsonNode previa, boolean substituir)
            throws Exception {
        api.perform(post(
                        "/api/v1/planos-semanais/{id}/geracao-deterministica", plano)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dataDeReferencia":"%s",
                                 "duracaoDoBlocoPrincipalEmMinutos":50,
                                 "quantidadeDeMateriasPorDia":1,
                                 "substituirBlocosGerados":%s,
                                 "assinaturaDaPrevia":"%s"}
                                """.formatted(referencia, substituir,
                                previa.get("assinaturaDaPrevia").asString())))
                .andExpect(status().isOk());
    }

    private String criar(MockHttpSession sessao, String caminho, String corpo)
            throws Exception {
        String resposta = api.perform(post(caminho).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return respostaJson(resposta).get("identificador").asString();
    }

    private JsonNode localizarTopico(JsonNode ranking, String topico) {
        return ranking.get("materias").valueStream()
                .flatMap(materia -> materia.get("topicos").valueStream())
                .filter(item -> topico.equals(item.get("id").asString()))
                .findFirst()
                .orElseThrow();
    }

    private JsonNode localizar(JsonNode lista, String campo, String valor) {
        return lista.valueStream()
                .filter(item -> valor.equals(item.get(campo).asString()))
                .findFirst()
                .orElseThrow();
    }

    private JsonNode localizarBlocoSugerido(JsonNode previa, String topico,
            String tipo) {
        return previa.get("dias").valueStream()
                .flatMap(dia -> dia.get("blocosSugeridos").valueStream())
                .filter(bloco -> topico.equals(
                        bloco.get("identificadorDoTopico").asString()))
                .filter(bloco -> tipo.equals(bloco.get("tipoDeAtividade").asString()))
                .findFirst()
                .orElseThrow();
    }

    private LocalDate dataDoBlocoSugerido(JsonNode previa, JsonNode bloco) {
        return previa.get("dias").valueStream()
                .filter(dia -> dia.get("blocosSugeridos").valueStream()
                        .anyMatch(item -> item.equals(bloco)))
                .map(dia -> LocalDate.parse(dia.get("data").asString()))
                .findFirst()
                .orElseThrow();
    }

    private java.util.List<String> ordemDosBlocosSugeridos(JsonNode previa) {
        return previa.get("dias").valueStream()
                .flatMap(dia -> dia.get("blocosSugeridos").valueStream())
                .map(bloco -> bloco.get("tipoDeAtividade").asString() + ":"
                        + bloco.get("identificadorDoTopico").asString())
                .toList();
    }

    private java.util.List<String> codigosDasJustificativas(JsonNode bloco) {
        return bloco.get("justificativas").valueStream()
                .map(justificativa -> justificativa.get("codigo").asString())
                .toList();
    }

    private JsonNode respostaJson(String resposta) {
        return json.readTree(resposta);
    }
}
