package br.com.trilhaaprovacao.conteudos.api;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
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
class ConteudosIntegracaoTest {
    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_conteudos")
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
    void deveExecutarCrudPaginadoDeMateriaComNormalizacaoEArquivamento() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");

        String identificador = criarMateria(sessao, "  Direito Constitucional  ", "#0e8f87");

        api.perform(post("/api/v1/materias").session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeMateria("direito constitucional", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MATERIA_JA_CADASTRADA"));

        api.perform(get("/api/v1/materias").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeItens").value(1))
                .andExpect(jsonPath("$.itens[0].nome").value("Direito Constitucional"))
                .andExpect(jsonPath("$.itens[0].cor").value("#0E8F87"))
                .andExpect(jsonPath("$.itens[0].identificador", not(nullValue())));

        api.perform(put("/api/v1/materias/{id}", identificador).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeMateria("Direito Constitucional Atualizado", "#12355b")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Direito Constitucional Atualizado"))
                .andExpect(jsonPath("$.cor").value("#12355B"));

        api.perform(post("/api/v1/materias/{id}/arquivamento", identificador).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arquivada\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arquivada").value(true));

        api.perform(get("/api/v1/materias").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeItens").value(0));
        api.perform(get("/api/v1/materias?incluirArquivadas=true").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeItens").value(1));

        api.perform(delete("/api/v1/materias/{id}", identificador).session(sessao).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deveRespeitarIrmaosMateriaDoPaiEImpedirCiclos() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        String materiaA = criarMateria(sessao, "Direito", null);
        String materiaB = criarMateria(sessao, "Tecnologia", null);
        String raiz = criarTopico(sessao, materiaA, "Constituicao", null, 1);
        String filho = criarTopico(sessao, materiaA, "Direitos", raiz, 1);

        api.perform(post("/api/v1/materias/{id}/topicos", materiaA).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeTopico(" direitos ", raiz, 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TOPICO_IRMAO_JA_CADASTRADO"));

        criarTopico(sessao, materiaA, "Direitos", null, 2);
        String paiDeOutraMateria = criarTopico(sessao, materiaB, "Redes", null, 1);

        api.perform(put("/api/v1/topicos/{id}", filho).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeTopico("Direitos", paiDeOutraMateria, 1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TOPICO_PAI_INVALIDO"));

        api.perform(put("/api/v1/topicos/{id}", raiz).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeTopico("Constituicao", filho, 1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CICLO_DE_TOPICOS"));

        api.perform(put("/api/v1/topicos/{id}", raiz).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeTopico("Constituicao", raiz, 1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TOPICO_NAO_PODE_SER_PAI_DE_SI"));

        api.perform(delete("/api/v1/topicos/{id}", raiz).session(sessao).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TOPICO_POSSUI_FILHOS"));

        api.perform(delete("/api/v1/materias/{id}", materiaA).session(sessao).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MATERIA_POSSUI_TOPICOS"));
    }

    @Test
    void deveIsolarMateriasETopicosPorUsuarioAutenticado() throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("pessoa.a@example.com");
        String materia = criarMateria(sessaoA, "Direito", null);
        String topico = criarTopico(sessaoA, materia, "Constituicao", null, 1);
        MockHttpSession sessaoB = criarContaEEntrar("pessoa.b@example.com");

        api.perform(get("/api/v1/materias").session(sessaoB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeItens").value(0));
        api.perform(get("/api/v1/materias/{id}", materia).session(sessaoB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("MATERIA_NAO_ENCONTRADA"));
        api.perform(get("/api/v1/topicos/{id}", topico).session(sessaoB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TOPICO_NAO_ENCONTRADO"));
        api.perform(delete("/api/v1/topicos/{id}", topico).session(sessaoB).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveResumirMateriaisEstudosEConcursosQueUsamAMateria() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("uso@example.com");
        String materia = criarMateria(sessao, "Direito", null);
        String topico = criarTopico(
                sessao, materia, "Direitos fundamentais", null, 1);
        UUID usuario = banco.queryForObject(
                "SELECT identificador FROM usuarios WHERE email = ?",
                UUID.class, "uso@example.com");
        inserirUsoDaMateria(
                usuario, UUID.fromString(materia), UUID.fromString(topico));

        api.perform(get("/api/v1/materias/{id}/uso", materia).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materiais[0].titulo").value("Aula 01"))
                .andExpect(jsonPath("$.materiais[0].tipo").value("AULA"))
                .andExpect(jsonPath("$.estudosRecentes[0].nomeDoTopico")
                        .value("Direitos fundamentais"))
                .andExpect(jsonPath("$.estudosRecentes[0].duracaoEmMinutos")
                        .value(60))
                .andExpect(jsonPath("$.concursos[0].nome").value("Concurso A"))
                .andExpect(jsonPath("$.concursos[0].ativo").value(true));

        MockHttpSession outraSessao = criarContaEEntrar("outra@example.com");
        api.perform(get("/api/v1/materias/{id}/uso", materia)
                        .session(outraSessao))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveBloquearTopicosEnquantoMateriaEstiverArquivada() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        String materia = criarMateria(sessao, "Direito", null);

        api.perform(post("/api/v1/materias/{id}/arquivamento", materia).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arquivada\":true}"))
                .andExpect(status().isOk());

        api.perform(post("/api/v1/materias/{id}/topicos", materia).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeTopico("Constituicao", null, 1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("MATERIA_ARQUIVADA"));
    }

    @Test
    void deveResponderEntradaInvalidaParaParametrosEIdentificadoresMalformados() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");

        api.perform(get("/api/v1/materias?pagina=-1").session(sessao))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ENTRADA_INVALIDA"));

        api.perform(get("/api/v1/materias/nao-e-uuid").session(sessao))
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

    private String criarMateria(MockHttpSession sessao, String nome, String cor) throws Exception {
        String resposta = api.perform(post("/api/v1/materias").session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeMateria(nome, cor)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get("identificador").asText();
    }

    private String criarTopico(MockHttpSession sessao, String materia, String nome, String pai, int ordem)
            throws Exception {
        String resposta = api.perform(post("/api/v1/materias/{id}/topicos", materia).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeTopico(nome, pai, ordem)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get("identificador").asText();
    }

    private String corpoDeMateria(String nome, String cor) {
        return """
                {"nome":"%s","descricao":"Descricao","cor":%s}
                """.formatted(nome, cor == null ? "null" : "\"" + cor + "\"");
    }

    private String corpoDeTopico(String nome, String pai, int ordem) {
        return """
                {"nome":"%s","descricao":"Descricao","identificadorDoTopicoPai":%s,"ordem":%d}
                """.formatted(nome, pai == null ? "null" : "\"" + pai + "\"", ordem);
    }

    private void inserirUsoDaMateria(UUID usuario, UUID materia, UUID topico) {
        OffsetDateTime agora = OffsetDateTime.now();
        UUID material = UUID.randomUUID();
        banco.update("""
                INSERT INTO materiais_de_estudo (
                    identificador, usuario_id, titulo, tipo, arquivado,
                    criado_em, atualizado_em, versao
                ) VALUES (?, ?, 'Aula 01', 'AULA', FALSE, ?, ?, 0)
                """, material, usuario, agora, agora);
        banco.update("""
                INSERT INTO coberturas_de_topicos_por_material (
                    identificador, material_id, topico_id, criado_em
                ) VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), material, topico, agora);
        banco.update("""
                INSERT INTO registros_de_estudo (
                    identificador, topico_id, material_id, data_hora,
                    duracao_em_minutos, situacao, criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, ?, 60, 'ATIVO', ?, ?, 0)
                """, UUID.randomUUID(), topico, material, agora, agora, agora);

        UUID concurso = UUID.randomUUID();
        UUID cargo = UUID.randomUUID();
        UUID prova = UUID.randomUUID();
        UUID grupo = UUID.randomUUID();
        banco.update("""
                INSERT INTO concursos (
                    identificador, usuario_id, nome, nome_normalizado, situacao,
                    ativo, criado_em, atualizado_em, versao
                ) VALUES (?, ?, 'Concurso A', 'concurso a', 'PLANEJADO',
                          TRUE, ?, ?, 0)
                """, concurso, usuario, agora, agora);
        banco.update("""
                INSERT INTO cargos_do_concurso (
                    identificador, concurso_id, nome, nome_normalizado,
                    nivel_de_escolaridade, selecionado, ordem,
                    criado_em, atualizado_em, versao
                ) VALUES (?, ?, 'Analista', 'analista', 'SUPERIOR', TRUE, 1,
                          ?, ?, 0)
                """, cargo, concurso, agora, agora);
        banco.update("""
                INSERT INTO provas (
                    identificador, cargo_id, nome, nome_normalizado, tipo,
                    carater, ordem, criado_em, atualizado_em, versao
                ) VALUES (?, ?, 'Objetiva', 'objetiva', 'OBJETIVA',
                          'CLASSIFICATORIO', 1, ?, ?, 0)
                """, prova, cargo, agora, agora);
        banco.update("""
                INSERT INTO grupos_de_conteudo (
                    identificador, prova_id, nome, nome_normalizado, ordem,
                    criado_em, atualizado_em, versao
                ) VALUES (?, ?, 'Basicos', 'basicos', 1, ?, ?, 0)
                """, grupo, prova, agora, agora);
        banco.update("""
                INSERT INTO materias_da_prova (
                    identificador, grupo_de_conteudo_id, materia_id, ordem,
                    criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, 1, ?, ?, 0)
                """, UUID.randomUUID(), grupo, materia, agora, agora);
    }
}
