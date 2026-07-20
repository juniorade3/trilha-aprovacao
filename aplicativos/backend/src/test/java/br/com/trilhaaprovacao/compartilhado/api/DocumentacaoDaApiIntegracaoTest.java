package br.com.trilhaaprovacao.compartilhado.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class DocumentacaoDaApiIntegracaoTest {
    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_openapi")
                    .withUsername("teste")
                    .withPassword("teste");

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        propriedades.add("spring.datasource.username", POSTGRESQL::getUsername);
        propriedades.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired MockMvc api;
    @Autowired ObjectMapper mapeador;

    @Test
    void deveGerarOpenApiComGruposSegurancaErrosECaminhosPrincipais() throws Exception {
        String corpo = api.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("API Trilha da Aprovação"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.components.securitySchemes.sessao.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.sessao.in").value("cookie"))
                .andExpect(jsonPath("$.components.securitySchemes.sessao.name").value("JSESSIONID"))
                .andExpect(jsonPath("$.components.securitySchemes.csrf.name").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.components.schemas.RespostaDeErro").exists())
                .andExpect(jsonPath("$.paths['/api/v1/autenticacao/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/materias'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/concursos'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/materiais'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/planos-semanais'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/planos-semanais'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/planos-semanais/{identificador}/disponibilidades'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/planos-semanais/{plano}/blocos'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/blocos-de-estudo/{identificador}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/blocos-de-estudo/{identificador}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/planos-semanais/{plano}/ordem-dos-blocos'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/planos-semanais/{identificador}/ativacao'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/blocos-de-estudo/{identificador}/reagendamento'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/blocos-de-estudo/{identificador}/cancelamento'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/execucoes-de-bloco/{identificador}/correcao'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/planos-semanais/{identificador}/encerramento'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/planos-semanais/{identificador}/cancelamento'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/planejamento/hoje'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/planejamento/execucao-em-andamento'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/blocos-de-estudo/{identificador}/inicio'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/blocos-de-estudo/{identificador}/execucao'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/blocos-de-estudo/{identificador}/topicos-para-registro'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/blocos-de-estudo/{identificador}/conclusao'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/blocos-de-estudo/{identificador}/interrupcao'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/execucoes-de-bloco/{identificador}/registro-de-estudo'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/dashboard'].get").exists())
                .andReturn().getResponse().getContentAsString();

        JsonNode documento = mapeador.readTree(corpo);
        List<String> grupos = documento.get("tags").valueStream()
                .map(tag -> tag.get("name").asString())
                .toList();
        assertThat(grupos).containsExactly(
                "Autenticação",
                "Matérias e tópicos",
                "Concursos",
                "Conteúdo programático",
                "Materiais e estudos",
                "Planejamento",
                "Dashboard");
        assertThat(documento.at("/paths/~1api~1v1~1dashboard/get/security/0/sessao")
                .isMissingNode()).isFalse();
        assertThat(documento.at("/paths/~1api~1v1~1estudos/post/security/0/csrf")
                .isMissingNode()).isFalse();
        assertThat(documento.at("/paths/~1api~1v1~1planos-semanais/post/security/0/csrf")
                .isMissingNode()).isFalse();
        assertThat(documento.at("/paths/~1api~1v1~1dashboard/get/responses/401")
                .isMissingNode()).isFalse();
    }

    @Test
    void deveExporSwaggerUiSemAutenticacaoNoPerfilLocal() throws Exception {
        api.perform(get("/swagger-ui.html"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));

        api.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
