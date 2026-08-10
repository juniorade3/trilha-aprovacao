package br.com.trilhaaprovacao.trilhas.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "debug=false")
@AutoConfigureMockMvc
@Testcontainers
class TrilhasPublicadasIntegracaoTest {
    private static final String TRILHA_TCU = "0a3c2d4f-36aa-4d3a-b9ad-6a852cef81a3";
    private static final String TAREFA_DE_PORTUGUES = "6c20fe2b-9522-1d88-86d2-3cf0be159a66";

    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_trilhas")
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

    @Test
    void deveExporCatalogoPublicoEIsolarOProgressoPorUsuario() throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("pessoa.a@example.com");
        MockHttpSession sessaoB = criarContaEEntrar("pessoa.b@example.com");

        api.perform(get("/api/v1/trilhas").session(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].identificador").value(TRILHA_TCU))
                .andExpect(jsonPath("$[0].quantidadeDeDisciplinas").value(15))
                .andExpect(jsonPath("$[0].quantidadeDeTarefas").value(356))
                .andExpect(jsonPath("$[0].aderida").value(false));

        api.perform(post("/api/v1/trilhas/{trilha}/adesao", TRILHA_TCU)
                        .session(sessaoA).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aderida").value(true));

        api.perform(put("/api/v1/trilhas/{trilha}/tarefas/{tarefa}/acompanhamento",
                        TRILHA_TCU, TAREFA_DE_PORTUGUES)
                        .session(sessaoA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"situacao\":\"CONCLUIDA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CONCLUIDA"));

        api.perform(get("/api/v1/trilhas/{trilha}", TRILHA_TCU).session(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trilha.quantidadeDeTarefasConcluidas").value(1))
                .andExpect(jsonPath("$.disciplinas[0].tarefas[0].situacao").value("CONCLUIDA"));

        api.perform(get("/api/v1/trilhas/{trilha}", TRILHA_TCU).session(sessaoB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trilha.aderida").value(false))
                .andExpect(jsonPath("$.disciplinas[0].tarefas[0].situacao").value("PENDENTE"));
    }

    @Test
    void deveExigirAdesaoAntesDeAlterarUmaTarefa() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.c@example.com");

        api.perform(put("/api/v1/trilhas/{trilha}/tarefas/{tarefa}/acompanhamento",
                        TRILHA_TCU, TAREFA_DE_PORTUGUES)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"situacao\":\"CONCLUIDA\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ADESAO_A_TRILHA_OBRIGATORIA"));
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
}
