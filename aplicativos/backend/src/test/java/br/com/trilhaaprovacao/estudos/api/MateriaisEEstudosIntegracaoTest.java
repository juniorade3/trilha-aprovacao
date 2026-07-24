package br.com.trilhaaprovacao.estudos.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "debug=false")
@AutoConfigureMockMvc
@Testcontainers
class MateriaisEEstudosIntegracaoTest {
    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_estudos")
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
    void deveExecutarCrudValidarUrlEArquivamentoDoMaterial() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        api.perform(post("/api/v1/materiais").session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeMaterial("Invalido", "arquivo-local")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("MATERIAL_INVALIDO"));
        String material = criarMaterial(sessao, "Curso completo");

        api.perform(get("/api/v1/materiais").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeItens").value(1));
        api.perform(put("/api/v1/materiais/{id}", material).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeMaterial("Curso atualizado", "https://example.com/aula")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Curso atualizado"));
        api.perform(post("/api/v1/materiais/{id}/arquivamento", material)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arquivado\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arquivado").value(true));
        api.perform(put("/api/v1/materiais/{id}", material).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeMaterial("Bloqueado", null)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deveListarAtalhosAtivosPorTopicoFiltrarMateriaisEIsolarUsuario()
            throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("atalhos.a@example.com");
        String materia = criarMateria(sessaoA, "Direito Constitucional");
        String topico = criarTopico(sessaoA, materia, "Direitos fundamentais");
        String materialAtivo = criarMaterial(sessaoA, "Aula 01");
        String materialArquivado = criarMaterial(sessaoA, "Aula antiga");
        criarMaterial(sessaoA, "Aula sem cobertura");
        adicionarCobertura(sessaoA, materialAtivo, topico);
        adicionarCobertura(sessaoA, materialArquivado, topico);
        api.perform(post("/api/v1/materiais/{id}/arquivamento", materialArquivado)
                        .session(sessaoA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arquivado\":true}"))
                .andExpect(status().isOk());

        api.perform(get("/api/v1/materiais").session(sessaoA)
                        .param("identificadorDoTopico", topico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeItens").value(1))
                .andExpect(jsonPath("$.itens[0].identificador").value(materialAtivo));
        api.perform(get("/api/v1/materiais/atalhos-por-topico").session(sessaoA)
                        .param("identificadoresDosTopicos", topico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].identificadorDoTopico").value(topico))
                .andExpect(jsonPath("$[0].identificadorDoMaterial").value(materialAtivo))
                .andExpect(jsonPath("$[0].tituloDoMaterial").value("Aula 01"));

        MockHttpSession sessaoB = criarContaEEntrar("atalhos.b@example.com");
        api.perform(get("/api/v1/materiais").session(sessaoB)
                        .param("identificadorDoTopico", topico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeItens").value(0));
        api.perform(get("/api/v1/materiais/atalhos-por-topico").session(sessaoB)
                        .param("identificadoresDosTopicos", topico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deveValidarCoberturaDuracaoEPreservarEstudoAoRemoverVinculo()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        String materia = criarMateria(sessao, "Direito");
        String topico = criarTopico(sessao, materia, "Constitucional");
        String outroTopico = criarTopico(sessao, materia, "Administrativo");
        String material = criarMaterial(sessao, "PDF");

        api.perform(post("/api/v1/estudos").session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeEstudo(outroTopico, material, 60)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("MATERIAL_NAO_COBRE_TOPICO"));
        adicionarCobertura(sessao, material, topico);
        api.perform(post("/api/v1/materiais/{id}/topicos", material)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identificadorDoTopico\":\"" + topico + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("COBERTURA_DUPLICADA"));
        api.perform(post("/api/v1/estudos").session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeEstudo(topico, material, 0)))
                .andExpect(status().isBadRequest());
        api.perform(post("/api/v1/estudos").session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeEstudo(topico, material, 1441)))
                .andExpect(status().isBadRequest());
        String estudo = registrarEstudo(sessao, topico, material, 60);

        api.perform(delete("/api/v1/materiais/{material}/topicos/{topico}",
                        material, topico).session(sessao).with(csrf()))
                .andExpect(status().isNoContent());
        api.perform(get("/api/v1/estudos/{id}", estudo).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("ATIVO"));
        api.perform(get("/api/v1/topicos/{id}", topico).session(sessao))
                .andExpect(status().isOk());
        api.perform(delete("/api/v1/materiais/{id}", material)
                        .session(sessao).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MATERIAL_POSSUI_HISTORICO"));
    }

    @Test
    void deveCorrigirECancelarMantendoRastreabilidade() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        String materia = criarMateria(sessao, "Portugues");
        String topico = criarTopico(sessao, materia, "Gramatica");
        String original = registrarEstudo(sessao, topico, null, 60);

        String correcao = identificador(api.perform(put(
                        "/api/v1/estudos/{id}/correcao", original)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeEstudo(topico, null, 45)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificadorDoRegistroDeOrigem").value(original))
                .andReturn().getResponse().getContentAsString());
        api.perform(get("/api/v1/estudos/{id}", original).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CORRIGIDO"));
        api.perform(post("/api/v1/estudos/{id}/cancelamento", correcao)
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CANCELADO"));
        api.perform(post("/api/v1/estudos/{id}/cancelamento", correcao)
                        .session(sessao).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
        api.perform(get("/api/v1/estudos").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeItens").value(2));
    }

    @Test
    void deveReutilizarUmEstudoEmDoisConcursosPeloMesmoTopico() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        String materia = criarMateria(sessao, "Raciocinio Logico");
        String topico = criarTopico(sessao, materia, "Proposicoes");
        mapearTopicoEmConcurso(sessao, materia, topico, "A");
        mapearTopicoEmConcurso(sessao, materia, topico, "B");
        registrarEstudo(sessao, topico, null, 60);

        Integer itensMapeados = banco.queryForObject("""
                SELECT COUNT(DISTINCT item_do_edital_id)
                FROM mapeamentos_de_itens_do_edital
                WHERE topico_da_materia_id = ?::uuid
                """, Integer.class, topico);
        Integer estudos = banco.queryForObject("""
                SELECT COUNT(*) FROM registros_de_estudo
                WHERE topico_id = ?::uuid AND situacao = 'ATIVO'
                """, Integer.class, topico);
        org.junit.jupiter.api.Assertions.assertEquals(2, itensMapeados);
        org.junit.jupiter.api.Assertions.assertEquals(1, estudos);
    }

    @Test
    void deveIsolarDadosEExigirAutenticacao() throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("pessoa.a@example.com");
        String materia = criarMateria(sessaoA, "Direito");
        String topico = criarTopico(sessaoA, materia, "Civil");
        String material = criarMaterial(sessaoA, "Livro");
        adicionarCobertura(sessaoA, material, topico);
        String estudo = registrarEstudo(sessaoA, topico, material, 30);
        MockHttpSession sessaoB = criarContaEEntrar("pessoa.b@example.com");

        api.perform(get("/api/v1/materiais").session(sessaoB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeItens").value(0));
        api.perform(get("/api/v1/materiais/{id}", material).session(sessaoB))
                .andExpect(status().isNotFound());
        api.perform(get("/api/v1/estudos/{id}", estudo).session(sessaoB))
                .andExpect(status().isNotFound());
        api.perform(get("/api/v1/estudos")).andExpect(status().isUnauthorized());
    }

    @Test
    void deveRegistrarEvidenciasDiagnosticarEIsolarPadroes() throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("evidencia.a@example.com");
        String materia = criarMateria(sessaoA, "Matematica");
        String topico = criarTopico(sessaoA, materia, "Equacoes");

        api.perform(post("/api/v1/estudos").session(sessaoA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeEstudoComEvidencia(
                                topico, "2026-07-01T10:00:00-03:00", 10, 7,
                                "Erro de sinal", 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoDeEstudo").value("QUESTOES"))
                .andExpect(jsonPath("$.evidencia.quantidadeDeErros").value(3));
        api.perform(post("/api/v1/estudos").session(sessaoA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeEstudoComEvidencia(
                                topico, "2026-07-20T11:00:00-03:00", 20, 16,
                                "  erro   de sinal ", 1)))
                .andExpect(status().isCreated());

        api.perform(post("/api/v1/estudos").session(sessaoA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identificadorDoTopico":"%s","tipoDeEstudo":"QUESTOES",
                                 "dataHora":"2026-07-20T12:00:00-03:00",
                                 "duracaoEmMinutos":30}
                                """.formatted(topico)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RESULTADO_DE_QUESTOES_OBRIGATORIO"));
        for (String tipo : List.of("SIMULADO", "CADERNO_DE_ERROS")) {
            api.perform(post("/api/v1/estudos").session(sessaoA).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"identificadorDoTopico":"%s","tipoDeEstudo":"%s",
                                     "dataHora":"2026-07-20T12:00:00-03:00",
                                     "duracaoEmMinutos":30}
                                    """.formatted(topico, tipo)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo")
                            .value("RESULTADO_DE_QUESTOES_OBRIGATORIO"));
        }
        api.perform(post("/api/v1/estudos").session(sessaoA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identificadorDoTopico":"%s","tipoDeEstudo":"REVISAO",
                                 "dataHora":"2026-07-20T12:00:00-03:00",
                                 "duracaoEmMinutos":30}
                                """.formatted(topico)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RECORDACAO_OBRIGATORIA"));
        api.perform(post("/api/v1/estudos").session(sessaoA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identificadorDoTopico":"%s","tipoDeEstudo":"QUESTOES",
                                 "dataHora":"2026-07-20T12:00:00-03:00",
                                 "duracaoEmMinutos":30,
                                 "evidencia":{"resultadoDeQuestoes":{
                                   "quantidadeDeQuestoes":1,"quantidadeDeAcertos":0},
                                   "padroesDeErro":[null]}}
                                """.formatted(topico)))
                .andExpect(status().isBadRequest());

        api.perform(get("/api/v1/evidencias/diagnostico-de-topicos")
                        .param("dataDeReferencia", "2026-07-20").session(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nomeDoTopico").value("Equacoes"))
                .andExpect(jsonPath("$[0].quantidadeDeEvidencias").value(2))
                .andExpect(jsonPath("$[0].totaisDosUltimosTrintaDias.questoes").value(30))
                .andExpect(jsonPath("$[0].totaisDosUltimosTrintaDias.acertos").value(23))
                .andExpect(jsonPath("$[0].percentualRecenteDeAcertos").value(76.67))
                .andExpect(jsonPath("$[0].padroesDeErroRepetidos[0].quantidadeDeEvidencias")
                        .value(2));
        api.perform(get("/api/v1/evidencias/padroes-de-erro")
                        .param("identificadorDoTopico", topico).session(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Erro de sinal"));

        MockHttpSession sessaoB = criarContaEEntrar("evidencia.b@example.com");
        api.perform(get("/api/v1/evidencias/diagnostico-de-topicos")
                        .param("dataDeReferencia", "2026-07-20").session(sessaoB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        api.perform(get("/api/v1/evidencias/padroes-de-erro")
                        .param("identificadorDoTopico", topico).session(sessaoB))
                .andExpect(status().isNotFound());
        api.perform(get("/api/v1/evidencias/diagnostico-de-topicos")
                        .param("dataDeReferencia", "2026-07-20")
                        .param("identificadorDaMateria", materia).session(sessaoB))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveDiagnosticarJanelaInclusivaSomenteComFatosAtivosERevisoes()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("janela.evidencias@example.com");
        String materia = criarMateria(sessao, "Estatistica");
        String topico = criarTopico(sessao, materia, "Probabilidade");
        criarTopico(sessao, materia, "Sem evidencias");

        registrarEvidencia(sessao, corpoDeEstudoComEvidencia(
                topico, "2026-06-20T10:00:00-03:00", 10, 9, "Cálculo", 1));
        registrarEvidencia(sessao, corpoDeEstudoComEvidencia(
                topico, "2026-06-21T10:00:00-03:00", 10, 5, "Cálculo", 1));
        registrarEvidencia(sessao, corpoDeEstudoComEvidencia(
                topico, "2026-07-20T10:00:00-03:00", 10, 8, "Cálculo", 1));
        String cancelado = registrarEvidencia(sessao, corpoDeEstudoComEvidencia(
                topico, "2026-07-10T10:00:00-03:00", 100, 0, "Cálculo", 1));
        api.perform(post("/api/v1/estudos/{id}/cancelamento", cancelado)
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk());
        String corrigido = registrarEvidencia(sessao, corpoDeEstudoComEvidencia(
                topico, "2026-07-11T10:00:00-03:00", 100, 0, "Cálculo", 1));
        api.perform(put("/api/v1/estudos/{id}/correcao", corrigido)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeEstudoComEvidencia(
                                topico, "2026-07-12T10:00:00-03:00",
                                5, 4, "Cálculo", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoDeEstudo").value("QUESTOES"));
        registrarEvidencia(sessao, corpoDeRevisao(
                topico, "2026-06-21T18:00:00-03:00", 2, 5));
        registrarEvidencia(sessao, corpoDeRevisao(
                topico, "2026-07-20T18:00:00-03:00", 4, 3));

        api.perform(get("/api/v1/evidencias/diagnostico-de-topicos")
                        .param("dataDeReferencia", "2026-07-20").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nomeDoTopico").value("Probabilidade"))
                .andExpect(jsonPath("$[0].quantidadeDeEvidencias").value(6))
                .andExpect(jsonPath("$[0].totaisHistoricos.questoes").value(35))
                .andExpect(jsonPath("$[0].totaisHistoricos.acertos").value(26))
                .andExpect(jsonPath("$[0].totaisDosUltimosTrintaDias.questoes").value(25))
                .andExpect(jsonPath("$[0].totaisDosUltimosTrintaDias.acertos").value(17))
                .andExpect(jsonPath("$[0].percentualRecenteDeAcertos").value(68.00))
                .andExpect(jsonPath("$[0].ultimaRecordacao").value(4))
                .andExpect(jsonPath("$[0].mediaRecenteDeRecordacao").value(3.00))
                .andExpect(jsonPath("$[0].ultimaDificuldade").value(3))
                .andExpect(jsonPath("$[0].mediaRecenteDeDificuldade").value(4.00))
                .andExpect(jsonPath("$[0].resultadoDaUltimaRevisao")
                        .value("CONSOLIDADA"))
                .andExpect(jsonPath("$[1].nomeDoTopico").value("Sem evidencias"))
                .andExpect(jsonPath("$[1].quantidadeDeEvidencias").value(0));

        mapearTopicoEmConcurso(sessao, materia, topico, "janela");
        api.perform(get("/api/v1/evidencias/diagnostico-de-topicos")
                        .param("dataDeReferencia", "2026-07-20")
                        .param("identificadorDaMateria", materia)
                        .param("somenteExigidosNoConcursoAtivo", "true")
                        .session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].identificadorDoTopico").value(topico))
                .andExpect(jsonPath("$[0].exigidoNoConcursoAtivo").value(true));
        api.perform(get("/api/v1/evidencias/diagnostico-de-topicos").session(sessao))
                .andExpect(status().isBadRequest());
    }

    private MockHttpSession criarContaEEntrar(String email) throws Exception {
        api.perform(post("/api/v1/autenticacao/cadastro").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Pessoa","email":"%s","senha":"senha-segura-123"}
                                """.formatted(email))).andExpect(status().isCreated());
        return (MockHttpSession) api.perform(post("/api/v1/autenticacao/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"senha-segura-123"}
                                """.formatted(email))).andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private String criarMateria(MockHttpSession sessao, String nome) throws Exception {
        return identificador(api.perform(post("/api/v1/materias").session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String criarTopico(MockHttpSession sessao, String materia, String nome)
            throws Exception {
        return identificador(api.perform(post("/api/v1/materias/{id}/topicos", materia)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"ordem\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String criarMaterial(MockHttpSession sessao, String titulo) throws Exception {
        return identificador(api.perform(post("/api/v1/materiais")
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeMaterial(titulo, "https://example.com/material")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void adicionarCobertura(
            MockHttpSession sessao, String material, String topico) throws Exception {
        api.perform(post("/api/v1/materiais/{id}/topicos", material)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identificadorDoTopico\":\"" + topico + "\"}"))
                .andExpect(status().isCreated());
    }

    private String registrarEstudo(MockHttpSession sessao, String topico,
            String material, int duracao) throws Exception {
        return identificador(api.perform(post("/api/v1/estudos").session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeEstudo(topico, material, duracao)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void mapearTopicoEmConcurso(MockHttpSession sessao, String materia,
            String topico, String sufixo) throws Exception {
        String concurso = criar(sessao, "/api/v1/concursos",
                """
                {"nome":"Concurso %s","situacao":"PLANEJADO"}
                """.formatted(sufixo));
        String edital = criar(sessao, "/api/v1/concursos/" + concurso + "/editais",
                "{\"titulo\":\"Edital " + sufixo + "\"}");
        String cargo = criar(sessao, "/api/v1/concursos/" + concurso + "/cargos",
                """
                {"nome":"Cargo %s","nivelDeEscolaridade":"SUPERIOR","ordem":1}
                """.formatted(sufixo));
        String prova = criar(sessao, "/api/v1/cargos/" + cargo + "/provas",
                """
                {"nome":"Prova %s","tipo":"OBJETIVA","carater":"CLASSIFICATORIO","ordem":1}
                """.formatted(sufixo));
        String grupo = criar(sessao, "/api/v1/provas/" + prova + "/grupos",
                "{\"nome\":\"Grupo " + sufixo + "\",\"ordem\":1}");
        String materiaDaProva = criar(sessao,
                "/api/v1/grupos-de-conteudo/" + grupo + "/materias",
                "{\"identificadorDaMateria\":\"" + materia + "\",\"ordem\":1}");
        String item = criar(sessao,
                "/api/v1/materias-da-prova/" + materiaDaProva + "/itens",
                "{\"identificadorDoEdital\":\"" + edital
                        + "\",\"descricaoOriginal\":\"Item " + sufixo + "\",\"ordem\":1}");
        api.perform(post("/api/v1/itens-do-edital/{id}/mapeamentos", item)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identificadorDoTopicoDaMateria\":\"" + topico + "\"}"))
                .andExpect(status().isCreated());
        api.perform(post("/api/v1/editais/{id}/definicao-como-principal", edital)
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk());
        api.perform(post("/api/v1/cargos/{id}/selecao", cargo)
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk());
        api.perform(post("/api/v1/concursos/{id}/ativacao", concurso)
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk());
    }

    private String criar(MockHttpSession sessao, String caminho, String corpo)
            throws Exception {
        return identificador(api.perform(post(caminho).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String corpoDeMaterial(String titulo, String endereco) {
        String url = endereco == null ? "null" : "\"" + endereco + "\"";
        return """
                {"titulo":"%s","tipo":"AULA","descricao":"Descricao",
                 "fonte":"Fonte","endereco":%s,"duracaoEstimadaEmMinutos":120}
                """.formatted(titulo, url);
    }

    private String corpoDeEstudo(String topico, String material, int duracao) {
        String materialJson = material == null ? "null" : "\"" + material + "\"";
        return """
                {"identificadorDoTopico":"%s","identificadorDoMaterial":%s,
                 "dataHora":"2026-07-18T10:00:00-03:00",
                 "duracaoEmMinutos":%d,"observacao":"Sessao de estudo"}
                """.formatted(topico, materialJson, duracao);
    }

    private String corpoDeEstudoComEvidencia(String topico, String dataHora,
            int questoes, int acertos, String padrao, int ocorrencias) {
        return """
                {"identificadorDoTopico":"%s","tipoDeEstudo":"QUESTOES",
                 "dataHora":"%s","duracaoEmMinutos":45,
                 "evidencia":{"resultadoDeQuestoes":{"quantidadeDeQuestoes":%d,
                 "quantidadeDeAcertos":%d},"dificuldadePercebida":4,
                 "padroesDeErro":[{"descricao":"%s","quantidadeDeOcorrencias":%d}]}}
                """.formatted(topico, dataHora, questoes, acertos, padrao, ocorrencias);
    }

    private String corpoDeRevisao(String topico, String dataHora,
            int recordacao, int dificuldade) {
        return """
                {"identificadorDoTopico":"%s","tipoDeEstudo":"REVISAO",
                 "dataHora":"%s","duracaoEmMinutos":30,
                 "evidencia":{"nivelDeRecordacao":%d,"dificuldadePercebida":%d}}
                """.formatted(topico, dataHora, recordacao, dificuldade);
    }

    private String registrarEvidencia(MockHttpSession sessao, String corpo)
            throws Exception {
        return identificador(api.perform(post("/api/v1/estudos")
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String identificador(String resposta) {
        return json.readTree(resposta).get("identificador").asText();
    }
}
