package br.com.trilhaaprovacao.planejamento.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
class IntegracaoPlanejamentoEstudosTest {
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 7, 20);

    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_planejamento_estudos")
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
                TRUNCATE TABLE execucoes_de_bloco, blocos_de_estudo,
                    disponibilidades_do_dia, planos_semanais,
                    registros_de_estudo, coberturas_de_topicos_por_material,
                    materiais_de_estudo, mapeamentos_de_itens_do_edital,
                    itens_do_edital, materias_da_prova, grupos_de_conteudo,
                    provas, cargos_do_concurso, editais, concursos,
                    topicos_da_materia, materias, usuarios CASCADE
                """);
    }

    @Test
    void deveCriarUmUnicoEstudoAoConcluirBlocoComTopico() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("integracao@example.com");
        String materia = criarMateria(sessao, "Auditoria");
        String topico = criarTopico(sessao, materia, "Risco de auditoria");
        String plano = criarPlanoAtivo(sessao);
        String bloco = criarBloco(sessao, plano, "Estudar riscos", materia, topico);

        iniciar(sessao, bloco);
        String primeiraResposta = concluir(sessao, bloco, 45, null);
        String registro = json.readTree(primeiraResposta)
                .get("execucao").get("identificadorDoRegistroDeEstudo").asString();

        api.perform(post("/api/v1/blocos-de-estudo/{id}/conclusao", bloco)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "duracaoExecutadaEmMinutos":45,
                                  "observacao":"Execução concluída"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execucao.identificadorDoRegistroDeEstudo")
                        .value(registro))
                .andExpect(jsonPath("$.estudo.identificadorDoTopico").value(topico));

        assertEquals(1, quantidadeDeRegistros());
        assertEquals(registro, banco.queryForObject("""
                SELECT registro_de_estudo_id::text
                FROM execucoes_de_bloco
                WHERE bloco_id = ?::uuid
                """, String.class, bloco));
    }

    @Test
    void devePermitirEscolherTopicoDepoisSemDuplicarEIsolarUsuario() throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("posterior.a@example.com");
        String materia = criarMateria(sessaoA, "Português");
        String topico = criarTopico(sessaoA, materia, "Concordância verbal");
        String plano = criarPlanoAtivo(sessaoA);
        String bloco = criarBloco(sessaoA, plano, "Resolver questões", materia, null);

        iniciar(sessaoA, bloco);
        String resposta = concluir(sessaoA, bloco, 30, null);
        String execucao = json.readTree(resposta).get("execucao")
                .get("identificador").asString();

        assertEquals(0, quantidadeDeRegistros());

        String primeiraVinculacao = registrarNoHistorico(
                sessaoA, execucao, topico, status().isOk());
        String registro = json.readTree(primeiraVinculacao)
                .get("estudo").get("identificador").asString();

        api.perform(post("/api/v1/execucoes-de-bloco/{id}/registro-de-estudo", execucao)
                        .session(sessaoA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identificadorDoTopico\":\"" + topico + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estudo.identificador").value(registro));

        MockHttpSession sessaoB = criarContaEEntrar("posterior.b@example.com");
        registrarNoHistorico(sessaoB, execucao, topico, status().isNotFound());

        assertEquals(1, quantidadeDeRegistros());
    }

    @Test
    void deveConcluirAtividadeLivreSemCriarEstudo() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("livre@example.com");
        String plano = criarPlanoAtivo(sessao);
        String bloco = criarBloco(sessao, plano, "Organizar caderno", null, null);

        iniciar(sessao, bloco);
        api.perform(post("/api/v1/blocos-de-estudo/{id}/conclusao", bloco)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "duracaoExecutadaEmMinutos":20,
                                  "observacao":"Organização concluída"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estudo").doesNotExist())
                .andExpect(jsonPath("$.execucao.identificadorDoRegistroDeEstudo")
                        .doesNotExist());

        assertEquals(0, quantidadeDeRegistros());
    }

    @Test
    void deveCorrigirExecucaoEEstudoVinculadoComRastreabilidade() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("correcao@example.com");
        String materia = criarMateria(sessao, "Banco de dados");
        String topico = criarTopico(sessao, materia, "Modelagem relacional");
        String plano = criarPlanoAtivo(sessao);
        String bloco = criarBloco(sessao, plano, "Estudar modelagem", materia, topico);
        iniciar(sessao, bloco);
        String resposta = concluir(sessao, bloco, 45, null);
        String execucao = json.readTree(resposta).get("execucao")
                .get("identificador").asString();
        String registroOriginal = json.readTree(resposta).get("execucao")
                .get("identificadorDoRegistroDeEstudo").asString();

        api.perform(put("/api/v1/execucoes-de-bloco/{id}/correcao", execucao)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resultado":"PARCIALMENTE_CONCLUIDO",
                                 "duracaoExecutadaEmMinutos":30,
                                 "observacao":"Duração corrigida"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloco.estado")
                        .value("PARCIALMENTE_CONCLUIDO"))
                .andExpect(jsonPath("$.execucao.duracaoExecutadaEmMinutos").value(30))
                .andExpect(jsonPath("$.execucao.identificadorDoRegistroDeEstudo")
                        .isNotEmpty());

        assertEquals("CORRIGIDO", banco.queryForObject("""
                SELECT situacao FROM registros_de_estudo WHERE identificador = ?::uuid
                """, String.class, registroOriginal));
        assertEquals(2, quantidadeDeRegistros());
    }

    @Test
    void deveRecuperarExecucaoAposNovaConsultaEBloquearSegundoInicio() throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("retomada.a@example.com");
        String plano = criarPlanoAtivo(sessaoA);
        String primeiro = adicionarBloco(sessaoA, plano, "Primeiro bloco", null, null, 1);
        String segundo = adicionarBloco(sessaoA, plano, "Segundo bloco", null, null, 2);
        ativarPlano(sessaoA, plano);

        iniciar(sessaoA, primeiro);

        api.perform(get("/api/v1/planejamento/execucao-em-andamento")
                        .session(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloco.identificador").value(primeiro))
                .andExpect(jsonPath("$.bloco.estado").value("EM_ANDAMENTO"));

        api.perform(post("/api/v1/blocos-de-estudo/{id}/inicio", segundo)
                        .session(sessaoA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataDeReferencia\":\"" + SEGUNDA + "\"}"))
                .andExpect(status().isConflict());

        MockHttpSession sessaoB = criarContaEEntrar("retomada.b@example.com");
        api.perform(get("/api/v1/blocos-de-estudo/{id}/execucao", primeiro)
                        .session(sessaoB))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveReverterFinalizacaoQuandoIntegracaoComEstudosFalha() throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("rollback.a@example.com");
        String materiaA = criarMateria(sessaoA, "Segurança da informação");
        String plano = criarPlanoAtivo(sessaoA);
        String bloco = criarBloco(sessaoA, plano, "Estudar segurança", materiaA, null);
        iniciar(sessaoA, bloco);

        MockHttpSession sessaoB = criarContaEEntrar("rollback.b@example.com");
        String materiaB = criarMateria(sessaoB, "Português");
        String topicoB = criarTopico(sessaoB, materiaB, "Interpretação de texto");

        api.perform(post("/api/v1/blocos-de-estudo/{id}/conclusao", bloco)
                        .session(sessaoA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duracaoExecutadaEmMinutos\":45,"
                                + "\"observacao\":\"Não deve persistir\","
                                + "\"identificadorDoTopico\":\"" + topicoB + "\"}"))
                .andExpect(status().isNotFound());

        api.perform(get("/api/v1/blocos-de-estudo/{id}/execucao", bloco)
                        .session(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloco.estado").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.execucao.encerradaEm").doesNotExist())
                .andExpect(jsonPath("$.execucao.resultado").doesNotExist());
        assertEquals(0, quantidadeDeRegistros());
    }

    private String registrarNoHistorico(MockHttpSession sessao, String execucao,
            String topico, org.springframework.test.web.servlet.ResultMatcher resultado)
            throws Exception {
        return api.perform(post("/api/v1/execucoes-de-bloco/{id}/registro-de-estudo", execucao)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identificadorDoTopico\":\"" + topico + "\"}"))
                .andExpect(resultado)
                .andReturn().getResponse().getContentAsString();
    }

    private String concluir(MockHttpSession sessao, String bloco, int duracao,
            String topico) throws Exception {
        String campoDoTopico = topico == null ? "" :
                ",\"identificadorDoTopico\":\"" + topico + "\"";
        return api.perform(post("/api/v1/blocos-de-estudo/{id}/conclusao", bloco)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duracaoExecutadaEmMinutos\":" + duracao
                                + ",\"observacao\":\"Execução concluída\""
                                + campoDoTopico + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void iniciar(MockHttpSession sessao, String bloco) throws Exception {
        api.perform(post("/api/v1/blocos-de-estudo/{id}/inicio", bloco)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataDeReferencia\":\"" + SEGUNDA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloco.estado").value("EM_ANDAMENTO"));
    }

    private String criarPlanoAtivo(MockHttpSession sessao) throws Exception {
        String corpo = api.perform(post("/api/v1/planos-semanais")
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataInicial\":\"" + SEGUNDA + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String plano = json.readTree(corpo).get("identificador").asString();

        api.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades(180)))
                .andExpect(status().isOk());
        return plano;
    }

    private String criarBloco(MockHttpSession sessao, String plano, String titulo,
            String materia, String topico) throws Exception {
        String bloco = adicionarBloco(sessao, plano, titulo, materia, topico, 1);
        ativarPlano(sessao, plano);
        return bloco;
    }

    private String adicionarBloco(MockHttpSession sessao, String plano, String titulo,
            String materia, String topico, int ordem) throws Exception {
        String referencias = materia == null ? "" :
                ",\"identificadorDaMateria\":\"" + materia + "\""
                        + (topico == null ? "" :
                        ",\"identificadorDoTopico\":\"" + topico + "\"");
        String corpo = api.perform(post("/api/v1/planos-semanais/{id}/blocos", plano)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"" + titulo
                                + "\",\"tipoDeAtividade\":\"TEORIA\""
                                + ",\"data\":\"" + SEGUNDA + "\""
                                + ",\"duracaoPrevistaEmMinutos\":60,\"ordem\":" + ordem
                                + referencias + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("identificador").asString();
    }

    private void ativarPlano(MockHttpSession sessao, String plano) throws Exception {
        api.perform(post("/api/v1/planos-semanais/{id}/ativacao", plano)
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk());
    }

    private String criarMateria(MockHttpSession sessao, String nome) throws Exception {
        String corpo = api.perform(post("/api/v1/materias")
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("identificador").asString();
    }

    private String criarTopico(MockHttpSession sessao, String materia, String nome)
            throws Exception {
        String corpo = api.perform(post("/api/v1/materias/{id}/topicos", materia)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"ordem\":1}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("identificador").asString();
    }

    private MockHttpSession criarContaEEntrar(String email) throws Exception {
        api.perform(post("/api/v1/autenticacao/cadastro").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Pessoa\",\"email\":\"" + email
                                + "\",\"senha\":\"senha-segura-123\"}"))
                .andExpect(status().isCreated());
        return (MockHttpSession) api.perform(post("/api/v1/autenticacao/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email
                                + "\",\"senha\":\"senha-segura-123\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private int quantidadeDeRegistros() {
        return banco.queryForObject("SELECT COUNT(*) FROM registros_de_estudo", Integer.class);
    }

    private String disponibilidades(int minutosDaSegunda) {
        StringBuilder corpo = new StringBuilder("{\"disponibilidades\":[");
        for (int indice = 0; indice < 7; indice++) {
            if (indice > 0) corpo.append(',');
            corpo.append("{\"data\":\"")
                    .append(SEGUNDA.plusDays(indice))
                    .append("\",\"minutosDisponiveis\":")
                    .append(indice == 0 ? minutosDaSegunda : 0)
                    .append('}');
        }
        return corpo.append("]}").toString();
    }
}
