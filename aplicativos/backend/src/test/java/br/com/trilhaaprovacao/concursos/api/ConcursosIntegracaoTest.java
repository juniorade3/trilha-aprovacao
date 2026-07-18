package br.com.trilhaaprovacao.concursos.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.trilhaaprovacao.concursos.dominio.SituacaoDoConcurso;
import br.com.trilhaaprovacao.concursos.infraestrutura.ConcursoPersistido;
import br.com.trilhaaprovacao.concursos.infraestrutura.RepositorioDeConcursos;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.OptimisticLockingFailureException;
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
class ConcursosIntegracaoTest {
    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_concursos")
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

    @Autowired
    RepositorioDeConcursos concursos;

    @BeforeEach
    void limparBanco() {
        banco.execute("""
                TRUNCATE TABLE mapeamentos_de_itens_do_edital, itens_do_edital,
                    materias_da_prova, grupos_de_conteudo, provas,
                    cargos_do_concurso, editais, concursos, topicos_da_materia,
                    materias, usuarios
                """);
    }

    @Test
    void deveConstruirArvoreGradualmenteComSelecoesUnicasEReusoDeMateria() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        String materia = criarMateria(sessao, "Direito Constitucional");
        String concursoA = criarConcurso(sessao, "Receita Federal");
        String concursoB = criarConcurso(sessao, "Controladoria");

        ativarConcurso(sessao, concursoA);
        ativarConcurso(sessao, concursoB);
        api.perform(get("/api/v1/concursos/{id}", concursoA).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
        api.perform(get("/api/v1/concursos/{id}", concursoB).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));

        String editalA = criarEdital(sessao, concursoA, "Edital 1");
        String editalB = criarEdital(sessao, concursoA, "Retificacao");
        definirEditalPrincipal(sessao, editalA);
        definirEditalPrincipal(sessao, editalB);
        api.perform(get("/api/v1/editais/{id}", editalA).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal").value(false));
        api.perform(get("/api/v1/editais/{id}", editalB).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal").value(true));

        String cargoA = criarCargo(sessao, concursoA, "Auditor", 1);
        String cargoB = criarCargo(sessao, concursoA, "Analista", 2);
        api.perform(post("/api/v1/concursos/{id}/cargos", concursoA).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeCargo(" auditor ", 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CARGO_DUPLICADO"));
        selecionarCargo(sessao, cargoA);
        selecionarCargo(sessao, cargoB);
        api.perform(get("/api/v1/cargos/{id}", cargoA).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selecionado").value(false));
        api.perform(get("/api/v1/cargos/{id}", cargoB).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selecionado").value(true));

        api.perform(post("/api/v1/cargos/{id}/provas", cargoB).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeProva("Prova invalida", "80", "90")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PROVA_INVALIDA"));
        String prova = criarProva(sessao, cargoB, "Prova Objetiva");
        api.perform(post("/api/v1/cargos/{id}/provas", cargoB).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeProva(" prova objetiva ", "100", "50")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PROVA_DUPLICADA"));

        String grupo = criarGrupo(sessao, prova, "Conhecimentos basicos");
        api.perform(post("/api/v1/provas/{id}/grupos", prova).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeGrupo(" conhecimentos basicos ")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("GRUPO_DUPLICADO"));
        String vinculacaoA = vincularMateria(sessao, grupo, materia);

        String cargoDoConcursoB = criarCargo(sessao, concursoB, "Auditor", 1);
        String provaDoConcursoB = criarProva(sessao, cargoDoConcursoB, "Objetiva");
        String grupoDoConcursoB = criarGrupo(sessao, provaDoConcursoB, "Especificos");
        String vinculacaoB = vincularMateria(sessao, grupoDoConcursoB, materia);

        api.perform(get("/api/v1/materias-da-prova/{id}", vinculacaoA).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificadorDaMateria").value(materia))
                .andExpect(jsonPath("$.nomeDaMateria").value("Direito Constitucional"));
        api.perform(get("/api/v1/materias-da-prova/{id}", vinculacaoB).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificadorDaMateria").value(materia));
        api.perform(post("/api/v1/grupos-de-conteudo/{id}/materias", grupo)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeMateriaDaProva(materia)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MATERIA_JA_UTILIZADA_NO_GRUPO"));

        api.perform(delete("/api/v1/grupos-de-conteudo/{id}", grupo).session(sessao).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("GRUPO_POSSUI_MATERIAS"));
        api.perform(delete("/api/v1/concursos/{id}", concursoA).session(sessao).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONCURSO_POSSUI_DEPENDENCIAS"));
    }

    @Test
    void deveExecutarAlteracoesExclusoesEImpedirConteudoEmConcursoArquivado()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        String concurso = criarConcurso(sessao, "Tribunal");
        String edital = criarEdital(sessao, concurso, "Edital inicial");
        String cargo = criarCargo(sessao, concurso, "Tecnico", 1);
        String prova = criarProva(sessao, cargo, "Objetiva");
        String grupo = criarGrupo(sessao, prova, "Conhecimentos gerais");
        String materia = criarMateria(sessao, "Portugues");
        String vinculacao = vincularMateria(sessao, grupo, materia);

        api.perform(put("/api/v1/concursos/{id}", concurso).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeConcurso("Tribunal Regional")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Tribunal Regional"));
        api.perform(put("/api/v1/editais/{id}", edital).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeEdital("Edital atualizado")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Edital atualizado"));
        api.perform(put("/api/v1/cargos/{id}", cargo).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeCargo("Tecnico Judiciario", 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordem").value(2));
        api.perform(put("/api/v1/provas/{id}", prova).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeProva("Objetiva atualizada", "120", "60")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Objetiva atualizada"));
        api.perform(put("/api/v1/grupos-de-conteudo/{id}", grupo).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeGrupo("Basicos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Basicos"));
        api.perform(put("/api/v1/materias-da-prova/{id}", vinculacao).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ordem":2,"peso":2.5,"quantidadeDeQuestoes":15,"pontuacaoMaxima":30}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordem").value(2));

        api.perform(post("/api/v1/concursos/{id}/arquivamento", concurso)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arquivado\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("ARQUIVADO"))
                .andExpect(jsonPath("$.ativo").value(false));
        api.perform(post("/api/v1/concursos/{id}/editais", concurso).session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeEdital("Outro edital")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONCURSO_ARQUIVADO"));
        api.perform(delete("/api/v1/materias-da-prova/{id}", vinculacao)
                        .session(sessao).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONCURSO_ARQUIVADO"));
    }

    @Test
    void deveIsolarTodaAEstruturaPeloUsuarioAutenticado() throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("pessoa.a@example.com");
        String concurso = criarConcurso(sessaoA, "Receita");
        String edital = criarEdital(sessaoA, concurso, "Edital");
        String cargo = criarCargo(sessaoA, concurso, "Auditor", 1);
        String prova = criarProva(sessaoA, cargo, "Objetiva");
        String grupo = criarGrupo(sessaoA, prova, "Especificos");
        String materia = criarMateria(sessaoA, "Direito");
        String vinculacao = vincularMateria(sessaoA, grupo, materia);
        MockHttpSession sessaoB = criarContaEEntrar("pessoa.b@example.com");

        api.perform(get("/api/v1/concursos").session(sessaoB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeItens").value(0));
        verificarNaoEncontrado(sessaoB, "/api/v1/concursos/" + concurso);
        verificarNaoEncontrado(sessaoB, "/api/v1/editais/" + edital);
        verificarNaoEncontrado(sessaoB, "/api/v1/cargos/" + cargo);
        verificarNaoEncontrado(sessaoB, "/api/v1/provas/" + prova);
        verificarNaoEncontrado(sessaoB, "/api/v1/grupos-de-conteudo/" + grupo);
        verificarNaoEncontrado(sessaoB, "/api/v1/materias-da-prova/" + vinculacao);
    }

    @Test
    void devePaginarEExcluirAHierarquiaDasFolhasAteOConcurso() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        String concurso = criarConcurso(sessao, "Concurso com estrutura");
        criarConcurso(sessao, "Concurso adicional");
        String edital = criarEdital(sessao, concurso, "Edital");
        String cargo = criarCargo(sessao, concurso, "Analista", 1);
        String prova = criarProva(sessao, cargo, "Objetiva");
        String grupo = criarGrupo(sessao, prova, "Gerais");
        String materia = criarMateria(sessao, "Raciocinio Logico");
        String vinculacao = vincularMateria(sessao, grupo, materia);

        api.perform(get("/api/v1/concursos?tamanho=1").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeItens").value(2))
                .andExpect(jsonPath("$.totalDePaginas").value(2))
                .andExpect(jsonPath("$.itens.length()").value(1));

        excluirComSucesso(sessao, "/api/v1/materias-da-prova/" + vinculacao);
        excluirComSucesso(sessao, "/api/v1/grupos-de-conteudo/" + grupo);
        excluirComSucesso(sessao, "/api/v1/provas/" + prova);
        excluirComSucesso(sessao, "/api/v1/cargos/" + cargo);
        excluirComSucesso(sessao, "/api/v1/editais/" + edital);
        excluirComSucesso(sessao, "/api/v1/concursos/" + concurso);
    }

    @Test
    void deveDetectarAtualizacaoConcorrentePelaVersao() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        UUID identificador = UUID.fromString(criarConcurso(sessao, "Concurso concorrente"));
        ConcursoPersistido primeiraCopia = concursos.findById(identificador).orElseThrow();
        ConcursoPersistido segundaCopia = concursos.findById(identificador).orElseThrow();

        primeiraCopia.atualizarDe(primeiraCopia.paraDominio().alterar("Primeira alteracao",
                null, null, null, SituacaoDoConcurso.PLANEJADO, null));
        concursos.saveAndFlush(primeiraCopia);
        segundaCopia.atualizarDe(segundaCopia.paraDominio().alterar("Segunda alteracao",
                null, null, null, SituacaoDoConcurso.PLANEJADO, null));

        org.junit.jupiter.api.Assertions.assertThrows(
                OptimisticLockingFailureException.class,
                () -> concursos.saveAndFlush(segundaCopia));
    }

    @Test
    void deveExigirAutenticacaoParaAListaDeConcursos() throws Exception {
        api.perform(get("/api/v1/concursos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("AUTENTICACAO_NECESSARIA"));
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

    private String criarConcurso(MockHttpSession sessao, String nome) throws Exception {
        return identificador(api.perform(post("/api/v1/concursos").session(sessao).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoDeConcurso(nome)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString());
    }

    private String criarEdital(MockHttpSession sessao, String concurso, String titulo)
            throws Exception {
        return identificador(api.perform(post("/api/v1/concursos/{id}/editais", concurso)
                .session(sessao).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoDeEdital(titulo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String criarCargo(MockHttpSession sessao, String concurso, String nome, int ordem)
            throws Exception {
        return identificador(api.perform(post("/api/v1/concursos/{id}/cargos", concurso)
                .session(sessao).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoDeCargo(nome, ordem)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String criarProva(MockHttpSession sessao, String cargo, String nome)
            throws Exception {
        return identificador(api.perform(post("/api/v1/cargos/{id}/provas", cargo)
                .session(sessao).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoDeProva(nome, "100", "50")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String criarGrupo(MockHttpSession sessao, String prova, String nome)
            throws Exception {
        return identificador(api.perform(post("/api/v1/provas/{id}/grupos", prova)
                .session(sessao).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoDeGrupo(nome)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String criarMateria(MockHttpSession sessao, String nome) throws Exception {
        return identificador(api.perform(post("/api/v1/materias").session(sessao).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String vincularMateria(
            MockHttpSession sessao, String grupo, String materia) throws Exception {
        return identificador(api.perform(post("/api/v1/grupos-de-conteudo/{id}/materias", grupo)
                .session(sessao).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoDeMateriaDaProva(materia)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private void ativarConcurso(MockHttpSession sessao, String concurso) throws Exception {
        api.perform(post("/api/v1/concursos/{id}/ativacao", concurso)
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk());
    }

    private void definirEditalPrincipal(MockHttpSession sessao, String edital)
            throws Exception {
        api.perform(post("/api/v1/editais/{id}/definicao-como-principal", edital)
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk());
    }

    private void selecionarCargo(MockHttpSession sessao, String cargo) throws Exception {
        api.perform(post("/api/v1/cargos/{id}/selecao", cargo)
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk());
    }

    private void verificarNaoEncontrado(MockHttpSession sessao, String caminho)
            throws Exception {
        api.perform(get(caminho).session(sessao))
                .andExpect(status().isNotFound());
    }

    private void excluirComSucesso(MockHttpSession sessao, String caminho)
            throws Exception {
        api.perform(delete(caminho).session(sessao).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private String identificador(String resposta) {
        return json.readTree(resposta).get("identificador").asText();
    }

    private String corpoDeConcurso(String nome) {
        return """
                {"nome":"%s","descricao":"Descricao","orgao":"Orgao","banca":"Banca",
                 "situacao":"PLANEJADO","dataPrevistaPrincipal":"2027-01-10"}
                """.formatted(nome);
    }

    private String corpoDeEdital(String titulo) {
        return """
                {"titulo":"%s","numero":"1","ano":2026,"descricao":"Descricao",
                 "dataDePublicacao":"2026-07-18",
                 "enderecoDoDocumento":"https://example.com/edital.pdf"}
                """.formatted(titulo);
    }

    private String corpoDeCargo(String nome, int ordem) {
        return """
                {"nome":"%s","area":"Fiscal","especialidade":"Geral",
                 "nivelDeEscolaridade":"SUPERIOR","ordem":%d}
                """.formatted(nome, ordem);
    }

    private String corpoDeProva(String nome, String maxima, String minima) {
        return """
                {"nome":"%s","tipo":"OBJETIVA","carater":"ELIMINATORIO",
                 "ordem":1,"duracaoEmMinutos":240,"quantidadeDeQuestoes":100,
                 "pontuacaoMaxima":%s,"pontuacaoMinima":%s}
                """.formatted(nome, maxima, minima);
    }

    private String corpoDeGrupo(String nome) {
        return """
                {"nome":"%s","ordem":1,"quantidadeDeQuestoes":20,
                 "pontuacaoMaxima":40,"pontuacaoMinima":20}
                """.formatted(nome);
    }

    private String corpoDeMateriaDaProva(String materia) {
        return """
                {"identificadorDaMateria":"%s","ordem":1,"peso":1.5,
                 "quantidadeDeQuestoes":10,"pontuacaoMaxima":20}
                """.formatted(materia);
    }
}
