package br.com.trilhaaprovacao.fluxo.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
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
class FluxoCompletoIntegracaoTest {
    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_fluxo_completo")
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
    void deveExecutarOsVinteETresPassosDoFluxoCompleto() throws Exception {
        api.perform(post("/api/v1/autenticacao/cadastro").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Pessoa","email":"fluxo@example.com",
                                 "senha":"senha-segura-123"}
                                """))
                .andExpect(status().isCreated());
        MockHttpSession sessao = (MockHttpSession) api.perform(
                        post("/api/v1/autenticacao/login").with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":"fluxo@example.com",
                                         "senha":"senha-segura-123"}
                                        """))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        String materia = criar(sessao, "/api/v1/materias",
                "{\"nome\":\"Direito Constitucional\"}");
        String topico = criar(sessao,
                "/api/v1/materias/" + materia + "/topicos",
                """
                {"nome":"Direitos fundamentais","ordem":1}
                """);
        String material = criar(sessao, "/api/v1/materiais",
                """
                {"titulo":"Aula 01","tipo":"AULA","duracaoEstimadaEmMinutos":90}
                """);
        criar(sessao, "/api/v1/materiais/" + material + "/topicos",
                "{\"identificadorDoTopico\":\"" + topico + "\"}");

        Estrutura concursoA = criarEstrutura(
                sessao, materia, topico, "A", "Conhecimentos básicos");
        api.perform(post("/api/v1/concursos/{id}/ativacao", concursoA.concurso())
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk());

        criar(sessao, "/api/v1/estudos",
                """
                {"identificadorDoTopico":"%s","identificadorDoMaterial":"%s",
                 "dataHora":"%s","duracaoEmMinutos":60,
                 "observacao":"Fluxo completo"}
                """.formatted(topico, material, OffsetDateTime.now()));

        api.perform(get("/api/v1/dashboard").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concursoAtivo.nome").value("Concurso A"))
                .andExpect(jsonPath("$.tempoEstudadoNaSemanaEmMinutos").value(60))
                .andExpect(jsonPath("$.quantidadeDeTopicosComEstudo").value(1));

        Estrutura concursoB = criarEstrutura(
                sessao, materia, topico, "B", "Conhecimentos gerais");
        api.perform(post("/api/v1/concursos/{id}/ativacao", concursoB.concurso())
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk());

        api.perform(get("/api/v1/dashboard").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concursoAtivo.nome").value("Concurso B"))
                .andExpect(jsonPath("$.tempoEstudadoNaSemanaEmMinutos").value(60))
                .andExpect(jsonPath("$.quantidadeDeTopicosExigidos").value(1))
                .andExpect(jsonPath("$.quantidadeDeTopicosComEstudo").value(1));

        api.perform(post("/api/v1/autenticacao/logout")
                        .session(sessao).with(csrf()))
                .andExpect(status().isNoContent());
        api.perform(get("/api/v1/dashboard").session(sessao))
                .andExpect(status().isUnauthorized());
    }

    private Estrutura criarEstrutura(
            MockHttpSession sessao,
            String materia,
            String topico,
            String sufixo,
            String nomeDoGrupo) throws Exception {
        String concurso = criar(sessao, "/api/v1/concursos",
                """
                {"nome":"Concurso %s","situacao":"PLANEJADO"}
                """.formatted(sufixo));
        String edital = criar(sessao,
                "/api/v1/concursos/" + concurso + "/editais",
                "{\"titulo\":\"Edital " + sufixo + "\"}");
        api.perform(post("/api/v1/editais/{id}/definicao-como-principal", edital)
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk());
        String cargo = criar(sessao,
                "/api/v1/concursos/" + concurso + "/cargos",
                """
                {"nome":"Analista %s","nivelDeEscolaridade":"SUPERIOR","ordem":1}
                """.formatted(sufixo));
        api.perform(post("/api/v1/cargos/{id}/selecao", cargo)
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk());
        String prova = criar(sessao, "/api/v1/cargos/" + cargo + "/provas",
                """
                {"nome":"Prova objetiva %s","tipo":"OBJETIVA",
                 "carater":"CLASSIFICATORIO","ordem":1}
                """.formatted(sufixo));
        String grupo = criar(sessao, "/api/v1/provas/" + prova + "/grupos",
                "{\"nome\":\"" + nomeDoGrupo + "\",\"ordem\":1}");
        String materiaDaProva = criar(sessao,
                "/api/v1/grupos-de-conteudo/" + grupo + "/materias",
                "{\"identificadorDaMateria\":\"" + materia + "\",\"ordem\":1}");
        String item = criar(sessao,
                "/api/v1/materias-da-prova/" + materiaDaProva + "/itens",
                """
                {"identificadorDoEdital":"%s",
                 "descricaoOriginal":"Direitos fundamentais %s","ordem":1}
                """.formatted(edital, sufixo));
        criar(sessao, "/api/v1/itens-do-edital/" + item + "/mapeamentos",
                "{\"identificadorDoTopicoDaMateria\":\"" + topico + "\"}");
        return new Estrutura(concurso);
    }

    private String criar(MockHttpSession sessao, String caminho, String corpo)
            throws Exception {
        String resposta = api.perform(post(caminho).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get("identificador").asText();
    }

    private record Estrutura(String concurso) {
    }
}
