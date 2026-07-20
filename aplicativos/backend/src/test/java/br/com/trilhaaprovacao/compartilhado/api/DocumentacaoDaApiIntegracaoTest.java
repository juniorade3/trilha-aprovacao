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
                .andExpect(jsonPath("$.paths['/api/v1/planos-semanais/{identificador}/materias-para-geracao'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/planos-semanais/{identificador}/prioridades-de-materias'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/planos-semanais/{identificador}/geracao-deterministica/previa'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/planos-semanais/{identificador}/geracao-deterministica'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/planos-semanais/{identificador}/replanejamento/previa'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/planos-semanais/{identificador}/replanejamento'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/planos-semanais/{identificador}/historico-semanal'].get").exists())
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

        validarOperacaoDaGeracao(documento.at("/paths/~1api~1v1~1planos-semanais~1"
                        + "{identificador}~1materias-para-geracao/get"),
                null, "RespostaDeMateriasParaGeracao",
                List.of("200", "401", "404", "422"));
        validarOperacaoDaGeracao(documento.at("/paths/~1api~1v1~1planos-semanais~1"
                        + "{identificador}~1prioridades-de-materias/put"),
                "RequisicaoDeAlteracaoDasPrioridades", "RespostaDeMateriasParaGeracao",
                List.of("200", "400", "401", "403", "404", "409", "422"));
        validarOperacaoDaGeracao(documento.at("/paths/~1api~1v1~1planos-semanais~1"
                        + "{identificador}~1geracao-deterministica~1previa/post"),
                "RequisicaoDePreviaDaGeracao", "RespostaDaPreviaDaGeracao",
                List.of("200", "400", "401", "403", "404", "409", "422"));
        validarOperacaoDaGeracao(documento.at("/paths/~1api~1v1~1planos-semanais~1"
                        + "{identificador}~1geracao-deterministica/post"),
                "RequisicaoDeAplicacaoDaGeracao", "RespostaDaAplicacaoDaGeracao",
                List.of("200", "400", "401", "403", "404", "409", "422"));
        validarOperacaoDaGeracao(documento.at("/paths/~1api~1v1~1planos-semanais~1"
                        + "{identificador}~1replanejamento~1previa/post"),
                "RequisicaoDaPreviaDoReplanejamento",
                "ResultadoDaPreviaDoReplanejamento",
                List.of("200", "400", "401", "403", "404", "409", "422"));
        validarOperacaoDaGeracao(documento.at("/paths/~1api~1v1~1planos-semanais~1"
                        + "{identificador}~1replanejamento/post"),
                "RequisicaoDeAplicacaoDoReplanejamento",
                "RespostaDaAplicacaoDoReplanejamento",
                List.of("200", "400", "401", "403", "404", "409", "422"));
        validarOperacaoDaGeracao(documento.at("/paths/~1api~1v1~1planos-semanais~1"
                        + "{identificador}~1historico-semanal/get"),
                null, "RespostaDoHistoricoSemanal",
                List.of("200", "400", "401", "403", "404", "409", "422"));
    }

    @Test
    void deveExporSwaggerUiSemAutenticacaoNoPerfilLocal() throws Exception {
        api.perform(get("/swagger-ui.html"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));

        api.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    private void validarOperacaoDaGeracao(JsonNode operacao, String esquemaDaRequisicao,
            String esquemaDaResposta, List<String> respostasEsperadas) {
        assertThat(operacao.isMissingNode()).isFalse();
        assertThat(operacao.path("summary").asString()).isNotBlank();
        assertThat(operacao.path("description").asString()).isNotBlank();
        assertThat(operacao.path("tags").valueStream().map(JsonNode::asString))
                .contains("Planejamento");
        assertThat(operacao.at("/security/0/sessao").isMissingNode()).isFalse();

        if (esquemaDaRequisicao == null) {
            assertThat(operacao.path("requestBody").isMissingNode()).isTrue();
            assertThat(operacao.at("/security/0/csrf").isMissingNode()).isTrue();
        } else {
            assertThat(operacao.at("/security/0/csrf").isMissingNode()).isFalse();
            assertThat(operacao.at("/requestBody/content/application~1json/schema/$ref")
                    .asString()).endsWith("/" + esquemaDaRequisicao);
        }

        assertThat(operacao.at("/responses/200/content").valueStream()
                .map(conteudo -> conteudo.at("/schema/$ref").asString()))
                .anyMatch(referencia -> referencia.endsWith("/" + esquemaDaResposta));
        respostasEsperadas.forEach(codigo -> {
            JsonNode resposta = operacao.at("/responses/" + codigo);
            assertThat(resposta.isMissingNode())
                    .as("resposta HTTP %s documentada", codigo)
                    .isFalse();
            if (!codigo.equals("200")) {
                assertThat(resposta.path("content").valueStream()
                        .map(conteudo -> conteudo.at("/schema/$ref").asString()))
                        .as("resposta HTTP %s usa RespostaDeErro", codigo)
                        .anyMatch(referencia -> referencia.endsWith("/RespostaDeErro"));
            }
        });
    }
}
