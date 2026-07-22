package br.com.trilhaaprovacao.compartilhado.api;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracaoDaDocumentacaoDaApi {
    public static final String ESQUEMA_DE_SESSAO = "sessao";
    public static final String ESQUEMA_DE_CSRF = "csrf";

    @Bean
    OpenAPI documentacaoDaApi() {
        Components componentes = new Components()
                .addSecuritySchemes(ESQUEMA_DE_SESSAO, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("JSESSIONID")
                        .description("Cookie de sessao obtido depois do login."))
                .addSecuritySchemes(ESQUEMA_DE_CSRF, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-XSRF-TOKEN")
                        .description("Token obtido em GET /api/v1/autenticacao/csrf e exigido nas operacoes que alteram dados."));

        ModelConverters.getInstance()
                .read(RespostaDeErro.class)
                .forEach(componentes::addSchemas);

        return new OpenAPI()
                .info(new Info()
                        .title("API Trilha da Aprovação")
                        .version("v1")
                        .description("""
                                API HTTP do Trilha da Aprovação.

                                A autenticação usa sessão. Antes de POST, PUT, PATCH ou DELETE,
                                consulte o endpoint de CSRF e envie o token no cabeçalho
                                X-XSRF-TOKEN. Os dados de negócio são sempre limitados ao usuário
                                autenticado.
                                """)
                        .contact(new Contact()
                                .name("Projeto Trilha da Aprovação")
                                .url("https://github.com/juniorade3/trilha-aprovacao")))
                .components(componentes)
                .tags(List.of(
                        new Tag().name("Autenticação").description("Cadastro, login, logout, sessão e CSRF."),
                        new Tag().name("Matérias e tópicos").description("Catálogo pessoal reutilizável."),
                        new Tag().name("Concursos").description("Concursos, editais, cargos, provas e grupos."),
                        new Tag().name("Conteúdo programático").description("Itens do edital e mapeamentos."),
                        new Tag().name("Materiais e estudos").description("Materiais, coberturas e registros de estudo."),
                        new Tag().name("Planejamento").description(
                                "Planos semanais, blocos, execucoes e replanejamento manual."),
                        new Tag().name("Evidências").description(
                                "Resultados de aprendizagem e diagnóstico objetivo por tópico."),
                        new Tag().name("Priorização de tópicos").description(
                                "Lacunas e ranking consultivo dos topicos exigidos."),
                        new Tag().name("Revisões espaçadas").description(
                                "Agenda deterministica de revisoes dos topicos exigidos."),
                        new Tag().name("Automação assistida").description(
                                "Vinculos de canal e historico de operacoes assistidas."),
                        new Tag().name("Dashboard").description("Visão objetiva do concurso ativo.")));
    }

    @Bean
    OpenApiCustomizer requisitosDeSegurancaERespostasDeErro() {
        ApiResponse respostaDeErro = new ApiResponse()
                .description("Erro padronizado da API.")
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new io.swagger.v3.oas.models.media.Schema<>()
                                .$ref("#/components/schemas/RespostaDeErro"))));

        return documentacao -> {
            ModelConverters.getInstance()
                    .read(RespostaDeErro.class)
                    .forEach(documentacao.getComponents()::addSchemas);
            documentacao.getPaths().forEach((caminho, item) ->
                    item.readOperationsMap().forEach((metodo, operacao) -> {
                        boolean autenticacaoPublica =
                                caminho.equals("/api/v1/autenticacao/cadastro")
                                        || caminho.equals("/api/v1/autenticacao/login")
                                        || caminho.equals("/api/v1/autenticacao/csrf");
                        boolean alteraDados = metodo != PathItem.HttpMethod.GET
                                && metodo != PathItem.HttpMethod.HEAD;

                        SecurityRequirement requisito = new SecurityRequirement();
                        if (!autenticacaoPublica) {
                            requisito.addList(ESQUEMA_DE_SESSAO);
                        }
                        if (alteraDados) {
                            requisito.addList(ESQUEMA_DE_CSRF);
                        }
                        if (!requisito.isEmpty()) {
                            operacao.addSecurityItem(requisito);
                        }

                        if (alteraDados) {
                            operacao.getResponses()
                                    .addApiResponse("400", respostaDeErro);
                        }
                        if (!autenticacaoPublica) {
                            operacao.getResponses()
                                    .addApiResponse("401", respostaDeErro);
                            operacao.getResponses()
                                    .addApiResponse("403", respostaDeErro);
                        }
                        operacao.getResponses().addApiResponse("500", respostaDeErro);
                    }));
        };
    }
}
