package br.com.trilhaaprovacao.autenticacao.api;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.trilhaaprovacao.autenticacao.infraestrutura.RepositorioDeUsuarios;
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

@SpringBootTest(properties = "debug=false")
@AutoConfigureMockMvc
@Testcontainers
class AutenticacaoIntegracaoTest {

    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_teste")
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
    RepositorioDeUsuarios repositorioDeUsuarios;

    @Autowired
    JdbcTemplate banco;

    @BeforeEach
    void limparUsuarios() {
        repositorioDeUsuarios.deleteAll();
    }

    @Test
    void deveExecutarCadastroLoginSessaoELogoutComCsrf() throws Exception {
        cadastrar("Pessoa A", "pessoa.a@example.com");

        MockHttpSession sessao = entrar("pessoa.a@example.com");

        api.perform(get("/api/v1/autenticacao/sessao").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.nome").value("Pessoa A"))
                .andExpect(jsonPath("$.usuario.email").value("pessoa.a@example.com"))
                .andExpect(jsonPath("$.usuario.senha").doesNotExist());

        api.perform(post("/api/v1/autenticacao/logout").session(sessao).with(csrf()))
                .andExpect(status().isNoContent());

        api.perform(get("/api/v1/autenticacao/sessao").session(sessao))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveManterSessoesDeUsuariosIsoladas() throws Exception {
        cadastrar("Pessoa A", "pessoa.a@example.com");
        cadastrar("Pessoa B", "pessoa.b@example.com");

        MockHttpSession sessaoA = entrar("pessoa.a@example.com");
        MockHttpSession sessaoB = entrar("pessoa.b@example.com");

        String respostaA = api.perform(get("/api/v1/autenticacao/sessao").session(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.email").value("pessoa.a@example.com"))
                .andReturn().getResponse().getContentAsString();

        api.perform(get("/api/v1/autenticacao/sessao").session(sessaoB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.email").value("pessoa.b@example.com"))
                .andExpect(jsonPath("$.usuario.identificador", not(nullValue())));

        org.assertj.core.api.Assertions.assertThat(respostaA).doesNotContain("pessoa.b@example.com");
    }

    @Test
    void deveRejeitarRequisicaoMutavelSemCsrfComCorrelacao() throws Exception {
        api.perform(post("/api/v1/autenticacao/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeCadastro("Pessoa A", "pessoa.a@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(header().exists("X-Identificador-De-Correlacao"))
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"))
                .andExpect(jsonPath("$.identificadorDeCorrelacao", not(nullValue())));
    }

    @Test
    void deveRejeitarEmailDuplicadoESenhaCurta() throws Exception {
        cadastrar("Pessoa A", " PESSOA.A@example.com ");

        api.perform(post("/api/v1/autenticacao/cadastro").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeCadastro("Outra Pessoa", "pessoa.a@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMAIL_JA_CADASTRADO"));

        api.perform(post("/api/v1/autenticacao/cadastro").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Pessoa C","email":"pessoa.c@example.com","senha":"curta"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ENTRADA_INVALIDA"));
    }

    @Test
    void deveRejeitarCredenciaisInvalidasEContaInativa() throws Exception {
        cadastrar("Pessoa A", "pessoa.a@example.com");

        api.perform(post("/api/v1/autenticacao/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"pessoa.a@example.com","senha":"senha-incorreta"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Identificador-De-Correlacao"))
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"))
                .andExpect(jsonPath("$.identificadorDeCorrelacao", not(nullValue())));

        banco.update("update usuarios set situacao = 'INATIVO' where email = ?", "pessoa.a@example.com");

        api.perform(post("/api/v1/autenticacao/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"pessoa.a@example.com","senha":"senha-segura-123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));
    }

    private void cadastrar(String nome, String email) throws Exception {
        api.perform(post("/api/v1/autenticacao/cadastro").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeCadastro(nome, email)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    private MockHttpSession entrar(String email) throws Exception {
        return (MockHttpSession) api.perform(post("/api/v1/autenticacao/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"senha-segura-123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private String corpoDeCadastro(String nome, String email) {
        return """
                {"nome":"%s","email":"%s","senha":"senha-segura-123"}
                """.formatted(nome, email);
    }
}
