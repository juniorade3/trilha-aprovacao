package br.com.trilhaaprovacao.conteudoprogramatico.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ConteudoProgramaticoIntegracaoTest {
    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_conteudo_programatico")
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
                TRUNCATE TABLE blocos_de_estudo, disponibilidades_do_dia, planos_semanais,
                    registros_de_estudo, coberturas_de_topicos_por_material,
                    materiais_de_estudo, mapeamentos_de_itens_do_edital, itens_do_edital,
                    materias_da_prova, grupos_de_conteudo, provas,
                    cargos_do_concurso, editais, concursos, topicos_da_materia,
                    materias, usuarios
                """);
    }

    @Test
    void deveManterArvoreMapearTopicoERemoverSomenteOVinculo() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        String materia = criarMateria(sessao, "Direito Constitucional");
        String topico = criarTopico(sessao, materia, null, "Direitos fundamentais", 1);
        Estrutura estrutura = criarEstrutura(sessao, materia, "Receita", "Edital 1");
        String redacao = "  Direitos e garantias fundamentais; remédios constitucionais.  ";

        String raiz = criarItem(sessao, estrutura.materiaDaProva(),
                estrutura.edital(), redacao, null, 1);
        String filho = criarItem(sessao, estrutura.materiaDaProva(),
                estrutura.edital(), "Habeas corpus.", raiz, 2);

        api.perform(get("/api/v1/materias-da-prova/{id}/itens",
                        estrutura.materiaDaProva()).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].descricaoOriginal").value(redacao))
                .andExpect(jsonPath("$[1].identificadorDoItemPai").value(raiz));
        api.perform(put("/api/v1/itens-do-edital/{id}", raiz)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeAlteracaoDoItem("Raiz", filho, 1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CICLO_DE_ITENS"));
        api.perform(delete("/api/v1/itens-do-edital/{id}", raiz)
                        .session(sessao).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ITEM_POSSUI_FILHOS"));
        api.perform(delete("/api/v1/editais/{id}", estrutura.edital())
                        .session(sessao).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EDITAL_POSSUI_ITENS"));

        api.perform(post("/api/v1/itens-do-edital/{id}/mapeamentos", filho)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeMapeamento(topico)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.confirmado").value(true))
                .andExpect(jsonPath("$.nomeDoTopico").value("Direitos fundamentais"));
        api.perform(post("/api/v1/itens-do-edital/{id}/mapeamentos", filho)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeMapeamento(topico)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MAPEAMENTO_DUPLICADO"));
        api.perform(delete("/api/v1/itens-do-edital/{id}", filho)
                        .session(sessao).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ITEM_POSSUI_MAPEAMENTOS"));
        api.perform(delete("/api/v1/topicos/{id}", topico)
                        .session(sessao).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TOPICO_POSSUI_MAPEAMENTOS"));
        api.perform(delete("/api/v1/materias-da-prova/{id}",
                        estrutura.materiaDaProva()).session(sessao).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo")
                        .value("MATERIA_DA_PROVA_POSSUI_ITENS"));
        api.perform(delete("/api/v1/itens-do-edital/{item}/mapeamentos/{topico}",
                        filho, topico).session(sessao).with(csrf()))
                .andExpect(status().isNoContent());

        api.perform(get("/api/v1/itens-do-edital/{id}", filho).session(sessao))
                .andExpect(status().isOk());
        api.perform(get("/api/v1/topicos/{id}", topico).session(sessao))
                .andExpect(status().isOk());
        api.perform(get("/api/v1/itens-do-edital/{id}/mapeamentos", filho)
                        .session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deveRecusarMateriaTopicoPaiEEditalIncompativeis() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        String materiaA = criarMateria(sessao, "Direito");
        String materiaB = criarMateria(sessao, "Portugues");
        String topicoB = criarTopico(sessao, materiaB, null, "Gramatica", 1);
        Estrutura estruturaA = criarEstrutura(sessao, materiaA, "Concurso A", "Edital A");
        Estrutura estruturaB = criarEstrutura(sessao, materiaB, "Concurso B", "Edital B");
        String itemA = criarItem(sessao, estruturaA.materiaDaProva(),
                estruturaA.edital(), "Constituicao.", null, 1);
        String itemB = criarItem(sessao, estruturaB.materiaDaProva(),
                estruturaB.edital(), "Gramatica.", null, 1);

        api.perform(post("/api/v1/materias-da-prova/{id}/itens",
                        estruturaA.materiaDaProva()).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeItem(
                                estruturaB.edital(), "Edital cruzado", null, 2)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("EDITAL_DE_OUTRO_CONCURSO"));
        api.perform(post("/api/v1/materias-da-prova/{id}/itens",
                        estruturaA.materiaDaProva()).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeItem(
                                estruturaA.edital(), "Pai cruzado", itemB, 2)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ITEM_PAI_INVALIDO"));
        api.perform(post("/api/v1/itens-do-edital/{id}/mapeamentos", itemA)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeMapeamento(topicoB)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TOPICO_DE_OUTRA_MATERIA"));
    }

    @Test
    void deveBloquearAlteracoesEmConcursoArquivado() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        String materia = criarMateria(sessao, "Administrativo");
        String topico = criarTopico(sessao, materia, null, "Atos", 1);
        Estrutura estrutura = criarEstrutura(sessao, materia, "Tribunal", "Edital");
        String item = criarItem(sessao, estrutura.materiaDaProva(),
                estrutura.edital(), "Atos administrativos.", null, 1);

        api.perform(post("/api/v1/concursos/{id}/arquivamento", estrutura.concurso())
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arquivado\":true}"))
                .andExpect(status().isOk());
        api.perform(put("/api/v1/itens-do-edital/{id}", item)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeAlteracaoDoItem("Alterado", null, 1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONCURSO_ARQUIVADO"));
        api.perform(post("/api/v1/itens-do-edital/{id}/mapeamentos", item)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeMapeamento(topico)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONCURSO_ARQUIVADO"));
        api.perform(get("/api/v1/itens-do-edital/{id}", item).session(sessao))
                .andExpect(status().isOk());
    }

    @Test
    void deveIsolarItensEMapeamentosPeloUsuarioAutenticado() throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("pessoa.a@example.com");
        String materia = criarMateria(sessaoA, "Direito");
        String topico = criarTopico(sessaoA, materia, null, "Constitucional", 1);
        Estrutura estrutura = criarEstrutura(sessaoA, materia, "Receita", "Edital");
        String item = criarItem(sessaoA, estrutura.materiaDaProva(),
                estrutura.edital(), "Constituicao.", null, 1);
        api.perform(post("/api/v1/itens-do-edital/{id}/mapeamentos", item)
                        .session(sessaoA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeMapeamento(topico)))
                .andExpect(status().isCreated());
        MockHttpSession sessaoB = criarContaEEntrar("pessoa.b@example.com");

        api.perform(get("/api/v1/materias-da-prova/{id}/itens",
                        estrutura.materiaDaProva()).session(sessaoB))
                .andExpect(status().isNotFound());
        api.perform(get("/api/v1/itens-do-edital/{id}", item).session(sessaoB))
                .andExpect(status().isNotFound());
        api.perform(get("/api/v1/itens-do-edital/{id}/mapeamentos", item)
                        .session(sessaoB))
                .andExpect(status().isNotFound());
        api.perform(delete("/api/v1/itens-do-edital/{item}/mapeamentos/{topico}",
                        item, topico).session(sessaoB).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveValidarEntradaEExigirAutenticacao() throws Exception {
        api.perform(get("/api/v1/itens-do-edital/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        String materia = criarMateria(sessao, "Direito");
        Estrutura estrutura = criarEstrutura(sessao, materia, "Receita", "Edital");

        api.perform(post("/api/v1/materias-da-prova/{id}/itens",
                        estrutura.materiaDaProva()).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeItem(estrutura.edital(), " ", null, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ENTRADA_INVALIDA"));
    }

    private MockHttpSession criarContaEEntrar(String email) throws Exception {
        api.perform(post("/api/v1/autenticacao/cadastro").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Pessoa","email":"%s","senha":"senha-segura-123"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return (MockHttpSession) api.perform(post("/api/v1/autenticacao/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"senha-segura-123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private Estrutura criarEstrutura(
            MockHttpSession sessao, String materia, String nomeConcurso, String tituloEdital)
            throws Exception {
        String concurso = identificador(api.perform(post("/api/v1/concursos")
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"%s","situacao":"PLANEJADO"}
                                """.formatted(nomeConcurso)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String edital = identificador(api.perform(post("/api/v1/concursos/{id}/editais", concurso)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"" + tituloEdital + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String cargo = identificador(api.perform(post("/api/v1/concursos/{id}/cargos", concurso)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Analista","nivelDeEscolaridade":"SUPERIOR","ordem":1}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String prova = identificador(api.perform(post("/api/v1/cargos/{id}/provas", cargo)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Objetiva","tipo":"OBJETIVA",
                                 "carater":"CLASSIFICATORIO","ordem":1}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String grupo = identificador(api.perform(post("/api/v1/provas/{id}/grupos", prova)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Gerais\",\"ordem\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String materiaDaProva = identificador(api.perform(
                        post("/api/v1/grupos-de-conteudo/{id}/materias", grupo)
                                .session(sessao).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"identificadorDaMateria":"%s","ordem":1}
                                        """.formatted(materia)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return new Estrutura(concurso, edital, materiaDaProva);
    }

    private String criarMateria(MockHttpSession sessao, String nome) throws Exception {
        return identificador(api.perform(post("/api/v1/materias")
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String criarTopico(MockHttpSession sessao, String materia, String pai,
            String nome, int ordem) throws Exception {
        String paiJson = pai == null ? "null" : "\"" + pai + "\"";
        return identificador(api.perform(post("/api/v1/materias/{id}/topicos", materia)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"%s","identificadorDoTopicoPai":%s,"ordem":%d}
                                """.formatted(nome, paiJson, ordem)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String criarItem(MockHttpSession sessao, String materiaDaProva,
            String edital, String descricao, String pai, int ordem) throws Exception {
        return identificador(api.perform(post("/api/v1/materias-da-prova/{id}/itens",
                        materiaDaProva).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeItem(edital, descricao, pai, ordem)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String corpoDeItem(String edital, String descricao, String pai, int ordem) {
        String paiJson = pai == null ? "null" : "\"" + pai + "\"";
        return """
                {"identificadorDoEdital":"%s","descricaoOriginal":"%s",
                 "identificadorDoItemPai":%s,"ordem":%d}
                """.formatted(edital, descricao, paiJson, ordem);
    }

    private String corpoDeAlteracaoDoItem(String descricao, String pai, int ordem) {
        String paiJson = pai == null ? "null" : "\"" + pai + "\"";
        return """
                {"descricaoOriginal":"%s","identificadorDoItemPai":%s,"ordem":%d}
                """.formatted(descricao, paiJson, ordem);
    }

    private String corpoDeMapeamento(String topico) {
        return "{\"identificadorDoTopicoDaMateria\":\"" + topico + "\"}";
    }

    private String identificador(String resposta) {
        return json.readTree(resposta).get("identificador").asText();
    }

    private record Estrutura(String concurso, String edital, String materiaDaProva) {
    }
}
