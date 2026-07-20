package br.com.trilhaaprovacao.dashboard.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

@SpringBootTest(properties = "debug=false")
@AutoConfigureMockMvc
@Testcontainers
class DashboardIntegracaoTest {
    private static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");

    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_dashboard")
                    .withUsername("teste")
                    .withPassword("teste");

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        propriedades.add("spring.datasource.username", POSTGRESQL::getUsername);
        propriedades.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired MockMvc api;
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
    void deveApresentarEstadoSemConcursoEExigirAutenticacao() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("sem.concurso@example.com");

        api.perform(get("/api/v1/dashboard").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concursoAtivo").isEmpty())
                .andExpect(jsonPath("$.tempoEstudadoNaSemanaEmMinutos").value(0))
                .andExpect(jsonPath("$.quantidadeDeTopicosExigidos").value(0))
                .andExpect(jsonPath("$.atividadeRecente").isEmpty())
                .andExpect(jsonPath("$.alertas").isEmpty());

        api.perform(get("/api/v1/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveGerarAlertasDeterministicosParaConcursoIncompleto() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("incompleto@example.com");
        UUID usuario = usuario("incompleto@example.com");
        inserirConcurso(usuario, "Concurso incompleto", true);

        api.perform(get("/api/v1/dashboard").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concursoAtivo.nome").value("Concurso incompleto"))
                .andExpect(jsonPath("$.concursoAtivo.nomeDoCargoSelecionado").isEmpty())
                .andExpect(jsonPath("$.alertas.length()").value(2))
                .andExpect(jsonPath("$.alertas[0].codigo")
                        .value("SEM_CARGO_SELECIONADO"))
                .andExpect(jsonPath("$.alertas[1].codigo")
                        .value("CONCURSO_SEM_PROVA"));
    }

    @Test
    void deveAgregarFatosIsolarUsuarioEReutilizarEstudoEmOutroConcurso()
            throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("pessoa.a@example.com");
        MockHttpSession sessaoB = criarContaEEntrar("pessoa.b@example.com");
        UUID usuarioA = usuario("pessoa.a@example.com");

        UUID concursoA = inserirConcurso(usuarioA, "Concurso A", true);
        UUID cargoA = inserirCargo(concursoA, "Auditor", true);
        UUID provaA = inserirProva(cargoA, "Prova objetiva");
        UUID grupoA = inserirGrupo(provaA, "Conhecimentos gerais");
        inserirGrupo(provaA, "Grupo vazio");
        UUID materiaComTopico = inserirMateria(usuarioA, "Direito");
        UUID materiaSemTopico = inserirMateria(usuarioA, "Tecnologia");
        UUID topico = inserirTopico(materiaComTopico, "Constitucional");
        UUID materiaDaProva = inserirMateriaDaProva(grupoA, materiaComTopico, 1);
        inserirMateriaDaProva(grupoA, materiaSemTopico, 2);
        UUID editalA = inserirEdital(concursoA, "Edital A");
        UUID itemMapeado = inserirItem(editalA, materiaDaProva, "Direitos fundamentais", 1);
        inserirItem(editalA, materiaDaProva, "Poder constituinte", 2);
        inserirMapeamento(itemMapeado, topico);

        api.perform(get("/api/v1/dashboard").session(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertas[3].codigo")
                        .value("PROVA_PROXIMA_SEM_ESTUDOS"));

        inserirEstudo(topico, 60, "ATIVO");
        inserirEstudo(topico, 40, "CANCELADO");

        LocalDate dataDaProva = LocalDate.now(FUSO_HORARIO).plusDays(3);
        api.perform(get("/api/v1/dashboard").session(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concursoAtivo.nome").value("Concurso A"))
                .andExpect(jsonPath("$.concursoAtivo.nomeDoCargoSelecionado")
                        .value("Auditor"))
                .andExpect(jsonPath("$.dataDaProximaProva")
                        .value(dataDaProva.toString()))
                .andExpect(jsonPath("$.diasAteAProva").value(3))
                .andExpect(jsonPath("$.tempoEstudadoNaSemanaEmMinutos").value(60))
                .andExpect(jsonPath("$.quantidadeDeMaterias").value(2))
                .andExpect(jsonPath("$.quantidadeDeTopicosExigidos").value(1))
                .andExpect(jsonPath("$.quantidadeDeTopicosComEstudo").value(1))
                .andExpect(jsonPath("$.quantidadeDeItensMapeados").value(1))
                .andExpect(jsonPath("$.quantidadeDeItensSemMapeamento").value(1))
                .andExpect(jsonPath("$.atividadeRecente.length()").value(1))
                .andExpect(jsonPath("$.atividadeRecente[0].nomeDoTopico")
                        .value("Constitucional"))
                .andExpect(jsonPath("$.alertas.length()").value(3))
                .andExpect(jsonPath("$.alertas[0].codigo").value("GRUPO_SEM_MATERIA"))
                .andExpect(jsonPath("$.alertas[1].codigo").value("ITEM_SEM_MAPEAMENTO"))
                .andExpect(jsonPath("$.alertas[2].codigo").value("MATERIA_SEM_TOPICO"));

        api.perform(get("/api/v1/dashboard").session(sessaoB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concursoAtivo").isEmpty())
                .andExpect(jsonPath("$.tempoEstudadoNaSemanaEmMinutos").value(0));

        UUID concursoB = inserirConcurso(usuarioA, "Concurso B", false);
        UUID cargoB = inserirCargo(concursoB, "Analista", true);
        UUID provaB = inserirProva(cargoB, "Prova B");
        UUID grupoB = inserirGrupo(provaB, "Conhecimentos");
        UUID materiaDaProvaB = inserirMateriaDaProva(grupoB, materiaComTopico, 1);
        UUID editalB = inserirEdital(concursoB, "Edital B");
        UUID itemB = inserirItem(editalB, materiaDaProvaB, "Constitucional", 1);
        inserirMapeamento(itemB, topico);
        banco.update("UPDATE concursos SET ativo = FALSE WHERE identificador = ?",
                concursoA);
        banco.update("UPDATE concursos SET ativo = TRUE WHERE identificador = ?",
                concursoB);

        api.perform(get("/api/v1/dashboard").session(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concursoAtivo.nome").value("Concurso B"))
                .andExpect(jsonPath("$.quantidadeDeTopicosExigidos").value(1))
                .andExpect(jsonPath("$.quantidadeDeTopicosComEstudo").value(1))
                .andExpect(jsonPath("$.tempoEstudadoNaSemanaEmMinutos").value(60));
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

    private UUID usuario(String email) {
        return banco.queryForObject(
                "SELECT identificador FROM usuarios WHERE email = ?",
                UUID.class, email);
    }

    private UUID inserirConcurso(UUID usuario, String nome, boolean ativo) {
        UUID identificador = UUID.randomUUID();
        banco.update("""
                INSERT INTO concursos (
                    identificador, usuario_id, nome, nome_normalizado, situacao,
                    ativo, criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, ?, 'EM_ANDAMENTO', ?, ?, ?, 0)
                """, identificador, usuario, nome, nome.toLowerCase(), ativo,
                agora(), agora());
        return identificador;
    }

    private UUID inserirCargo(UUID concurso, String nome, boolean selecionado) {
        UUID identificador = UUID.randomUUID();
        banco.update("""
                INSERT INTO cargos_do_concurso (
                    identificador, concurso_id, nome, nome_normalizado,
                    nivel_de_escolaridade, selecionado, ordem,
                    criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, ?, 'SUPERIOR', ?, 1, ?, ?, 0)
                """, identificador, concurso, nome, nome.toLowerCase(),
                selecionado, agora(), agora());
        return identificador;
    }

    private UUID inserirProva(UUID cargo, String nome) {
        UUID identificador = UUID.randomUUID();
        OffsetDateTime data = LocalDate.now(FUSO_HORARIO).plusDays(3)
                .atTime(10, 0).atZone(FUSO_HORARIO).toOffsetDateTime();
        banco.update("""
                INSERT INTO provas (
                    identificador, cargo_id, nome, nome_normalizado, tipo, carater,
                    ordem, data_hora_prevista, criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, ?, 'OBJETIVA', 'ELIMINATORIO', 1, ?, ?, ?, 0)
                """, identificador, cargo, nome, nome.toLowerCase(),
                data, agora(), agora());
        return identificador;
    }

    private UUID inserirGrupo(UUID prova, String nome) {
        UUID identificador = UUID.randomUUID();
        banco.update("""
                INSERT INTO grupos_de_conteudo (
                    identificador, prova_id, nome, nome_normalizado, ordem,
                    criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, ?, 1, ?, ?, 0)
                """, identificador, prova, nome, nome.toLowerCase(), agora(), agora());
        return identificador;
    }

    private UUID inserirMateria(UUID usuario, String nome) {
        UUID identificador = UUID.randomUUID();
        banco.update("""
                INSERT INTO materias (
                    identificador, usuario_id, nome, nome_normalizado, arquivada,
                    criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, ?, FALSE, ?, ?, 0)
                """, identificador, usuario, nome, nome.toLowerCase(), agora(), agora());
        return identificador;
    }

    private UUID inserirTopico(UUID materia, String nome) {
        UUID identificador = UUID.randomUUID();
        banco.update("""
                INSERT INTO topicos_da_materia (
                    identificador, materia_id, nome, nome_normalizado, ordem,
                    arquivado, criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, ?, 1, FALSE, ?, ?, 0)
                """, identificador, materia, nome, nome.toLowerCase(), agora(), agora());
        return identificador;
    }

    private UUID inserirMateriaDaProva(UUID grupo, UUID materia, int ordem) {
        UUID identificador = UUID.randomUUID();
        banco.update("""
                INSERT INTO materias_da_prova (
                    identificador, grupo_de_conteudo_id, materia_id, ordem,
                    criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, ?, ?, ?, 0)
                """, identificador, grupo, materia, ordem, agora(), agora());
        return identificador;
    }

    private UUID inserirEdital(UUID concurso, String titulo) {
        UUID identificador = UUID.randomUUID();
        banco.update("""
                INSERT INTO editais (
                    identificador, concurso_id, titulo, principal,
                    criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, TRUE, ?, ?, 0)
                """, identificador, concurso, titulo, agora(), agora());
        return identificador;
    }

    private UUID inserirItem(
            UUID edital, UUID materiaDaProva, String descricao, int ordem) {
        UUID identificador = UUID.randomUUID();
        banco.update("""
                INSERT INTO itens_do_edital (
                    identificador, edital_id, materia_da_prova_id,
                    descricao_original, ordem, criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                """, identificador, edital, materiaDaProva, descricao, ordem,
                agora(), agora());
        return identificador;
    }

    private void inserirMapeamento(UUID item, UUID topico) {
        banco.update("""
                INSERT INTO mapeamentos_de_itens_do_edital (
                    identificador, item_do_edital_id, topico_da_materia_id,
                    confirmado, criado_em
                ) VALUES (?, ?, ?, TRUE, ?)
                """, UUID.randomUUID(), item, topico, agora());
    }

    private void inserirEstudo(UUID topico, int duracao, String situacao) {
        banco.update("""
                INSERT INTO registros_de_estudo (
                    identificador, topico_id, data_hora, duracao_em_minutos,
                    situacao, criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                """, UUID.randomUUID(), topico, agora(), duracao, situacao,
                agora(), agora());
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(FUSO_HORARIO);
    }
}
