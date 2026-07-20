package br.com.trilhaaprovacao.planejamento.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessException;
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
class PlanejamentoIntegracaoTest {
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 7, 20);

    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_planejamento")
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
                TRUNCATE TABLE execucoes_de_bloco, blocos_de_estudo, disponibilidades_do_dia, planos_semanais,
                    registros_de_estudo, coberturas_de_topicos_por_material,
                    materiais_de_estudo, mapeamentos_de_itens_do_edital,
                    itens_do_edital, materias_da_prova, grupos_de_conteudo,
                    provas, cargos_do_concurso, editais, concursos,
                    topicos_da_materia, materias, usuarios CASCADE
                """);
    }

    @Test
    void deveCriarObterEImpedirPlanoDuplicado() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");

        String resposta = api.perform(post("/api/v1/planos-semanais")
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataInicial\":\"" + SEGUNDA + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("/api/v1/planos-semanais/")))
                .andExpect(jsonPath("$.estado").value("RASCUNHO"))
                .andExpect(jsonPath("$.dataFinal").value(SEGUNDA.plusDays(6).toString()))
                .andExpect(jsonPath("$.disponibilidades.length()").value(7))
                .andExpect(jsonPath("$.totalDeMinutosDisponiveis").value(0))
                .andReturn().getResponse().getContentAsString();
        String identificador = json.readTree(resposta).get("identificador").asString();

        api.perform(get("/api/v1/planos-semanais")
                        .param("dataInicial", SEGUNDA.toString()).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificador").value(identificador))
                .andExpect(jsonPath("$.disponibilidades[6].data")
                        .value(SEGUNDA.plusDays(6).toString()));
        api.perform(post("/api/v1/planos-semanais").session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataInicial\":\"" + SEGUNDA + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PLANO_SEMANAL_JA_EXISTE"));
    }

    @Test
    void deveAlterarSeteDiasEPersistirDepoisDeNovaConsulta() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        String plano = criarPlano(sessao, SEGUNDA);

        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades(180)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponibilidades[0].minutosDisponiveis").value(180))
                .andExpect(jsonPath("$.totalDeMinutosDisponiveis").value(180));

        api.perform(get("/api/v1/planos-semanais")
                        .param("dataInicial", SEGUNDA.toString()).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponibilidades[0].minutosDisponiveis").value(180))
                .andExpect(jsonPath("$.totalDeMinutosDisponiveis").value(180));
    }

    @Test
    void deveValidarInicioMinutosQuantidadeDeDiasECsrf() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        api.perform(post("/api/v1/planos-semanais").session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataInicial\":\"" + SEGUNDA.plusDays(1) + "\"}"))
                .andExpect(status().isUnprocessableEntity());
        String plano = criarPlano(sessao, SEGUNDA);

        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disponibilidades\":[]}"))
                .andExpect(status().isBadRequest());
        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades(1441)))
                .andExpect(status().isBadRequest());
        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades(60)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveIsolarPlanosPorUsuarioEExigirAutenticacao() throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("pessoa.a@example.com");
        String plano = criarPlano(sessaoA, SEGUNDA);
        MockHttpSession sessaoB = criarContaEEntrar("pessoa.b@example.com");

        api.perform(get("/api/v1/planos-semanais")
                        .param("dataInicial", SEGUNDA.toString()).session(sessaoB))
                .andExpect(status().isNotFound());
        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(sessaoB).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades(60)))
                .andExpect(status().isNotFound());
        api.perform(get("/api/v1/planos-semanais")
                        .param("dataInicial", SEGUNDA.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveAplicarConstraintsDeUnicidadeEMinutosNoPostgresql() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        String plano = criarPlano(sessao, SEGUNDA);
        UUID usuario = banco.queryForObject(
                "SELECT usuario_id FROM planos_semanais WHERE identificador = ?::uuid",
                UUID.class, plano);

        assertThrows(DataAccessException.class, () -> banco.update("""
                INSERT INTO planos_semanais (
                    identificador, usuario_id, data_inicial, estado,
                    criado_em, atualizado_em, versao
                ) VALUES (?::uuid, ?, ?, 'RASCUNHO', now(), now(), 0)
                """, UUID.randomUUID().toString(), usuario, SEGUNDA));
        assertThrows(DataAccessException.class, () -> banco.update("""
                UPDATE disponibilidades_do_dia
                SET minutos_disponiveis = 1441
                WHERE plano_id = ?::uuid
                """, plano));
        assertThrows(DataAccessException.class, () -> banco.update("""
                INSERT INTO disponibilidades_do_dia (
                    identificador, plano_id, data, minutos_disponiveis,
                    criado_em, atualizado_em, versao
                ) VALUES (?::uuid, ?::uuid, ?, 0, now(), now(), 0)
                """, UUID.randomUUID().toString(), plano, SEGUNDA));
    }

    @Test
    void deveExecutarCrudLivreNormalizarOrdemEManterExcesso() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        String plano = criarPlano(sessao, SEGUNDA);
        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades(180)))
                .andExpect(status().isOk());
        String primeiro = criarBloco(sessao, plano, "Primeiro", SEGUNDA, 100, 1, null, null);
        String segundo = criarBloco(sessao, plano, "Segundo", SEGUNDA, 100, 2, null, null);
        String terceiro = criarBloco(sessao, plano, "Terceiro", SEGUNDA, 30, 3, null, null);

        api.perform(put("/api/v1/planos-semanais/{id}/ordem-dos-blocos", plano)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":"%s","identificadoresOrdenados":["%s","%s","%s"]}
                                """.formatted(SEGUNDA, primeiro, terceiro, segundo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocos[1].identificador").value(terceiro))
                .andExpect(jsonPath("$.blocos[1].ordem").value(2));
        api.perform(put("/api/v1/blocos-de-estudo/{id}", terceiro)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoBloco("Terceiro alterado", SEGUNDA.plusDays(1),
                                45, 1, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Terceiro alterado"))
                .andExpect(jsonPath("$.data").value(SEGUNDA.plusDays(1).toString()));
        api.perform(delete("/api/v1/blocos-de-estudo/{id}", primeiro)
                        .session(sessao).with(csrf()))
                .andExpect(status().isNoContent());
        api.perform(get("/api/v1/planos-semanais")
                        .param("dataInicial", SEGUNDA.toString()).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocos.length()").value(2))
                .andExpect(jsonPath("$.blocos[0].identificador").value(segundo))
                .andExpect(jsonPath("$.blocos[0].ordem").value(1));
    }

    @Test
    void deveValidarMateriaTopicoEscopoEPlanoEditavel() throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("pessoa.a@example.com");
        String planoA = criarPlano(sessaoA, SEGUNDA);
        String materiaA = criarMateria(sessaoA, "Direito");
        String outraMateriaA = criarMateria(sessaoA, "Portugues");
        String topicoA = criarTopico(sessaoA, materiaA, "Constitucional");

        api.perform(post("/api/v1/planos-semanais/{id}/blocos", planoA)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoBloco("Incompativel", SEGUNDA, 60, 1,
                                outraMateriaA, topicoA)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TOPICO_INCOMPATIVEL_COM_MATERIA"));
        criarBloco(sessaoA, planoA, "Com topico", SEGUNDA, 60, 1, materiaA, topicoA);

        MockHttpSession sessaoB = criarContaEEntrar("pessoa.b@example.com");
        String planoB = criarPlano(sessaoB, SEGUNDA);
        api.perform(post("/api/v1/planos-semanais/{id}/blocos", planoB)
                        .session(sessaoB).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoBloco("De outro usuario", SEGUNDA, 60, 1,
                                materiaA, null)))
                .andExpect(status().isNotFound());

        banco.update("UPDATE planos_semanais SET estado = 'ENCERRADO' WHERE identificador = ?::uuid",
                planoA);
        api.perform(post("/api/v1/planos-semanais/{id}/blocos", planoA)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoBloco("Bloqueado", SEGUNDA, 60, 2, null, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void deveExigirCsrfIsolarBlocoEValidarContrato() throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("pessoa.a@example.com");
        String plano = criarPlano(sessaoA, SEGUNDA);
        String bloco = criarBloco(sessaoA, plano, "Livre", SEGUNDA, 60, 1, null, null);
        MockHttpSession sessaoB = criarContaEEntrar("pessoa.b@example.com");

        api.perform(put("/api/v1/blocos-de-estudo/{id}", bloco)
                        .session(sessaoB).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoBloco("Invasao", SEGUNDA, 60, 1, null, null)))
                .andExpect(status().isNotFound());
        api.perform(delete("/api/v1/blocos-de-estudo/{id}", bloco).session(sessaoA))
                .andExpect(status().isForbidden());
        api.perform(post("/api/v1/planos-semanais/{id}/blocos", plano)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoBloco("Fora", SEGUNDA.minusDays(1), 60, 1, null, null)))
                .andExpect(status().isUnprocessableEntity());
        api.perform(post("/api/v1/planos-semanais/{id}/blocos", plano)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoBloco("Duração", SEGUNDA, 1441, 1, null, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveAplicarConstraintsEIndiceDosBlocosNoPostgresql() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pessoa.a@example.com");
        String plano = criarPlano(sessao, SEGUNDA);
        String bloco = criarBloco(sessao, plano, "Livre", SEGUNDA, 60, 1, null, null);

        assertThrows(DataAccessException.class, () -> banco.update("""
                UPDATE blocos_de_estudo SET duracao_prevista_em_minutos = 0
                WHERE identificador = ?::uuid
                """, bloco));
        Integer indices = banco.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE tablename = 'blocos_de_estudo'
                  AND indexname = 'idx_blocos_plano_data_ordem'
                """, Integer.class);
        Integer chavesEstrangeiras = banco.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                  AND table_name = 'blocos_de_estudo'
                  AND constraint_type = 'FOREIGN KEY'
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, indices);
        org.junit.jupiter.api.Assertions.assertEquals(3, chavesEstrangeiras);
    }

    @Test
    void deveAtivarPlanoValidoEPermitirAjusteSemReduzirAbaixoDaCarga() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("ativacao@example.com");
        String plano = criarPlano(sessao, SEGUNDA);
        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades(120)))
                .andExpect(status().isOk());
        String bloco = criarBloco(sessao, plano, "Teoria", SEGUNDA, 90, 1, null, null);

        api.perform(post("/api/v1/planos-semanais/{id}/ativacao", plano)
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATIVO"))
                .andExpect(jsonPath("$.totalDeMinutosPlanejados").value(90))
                .andExpect(jsonPath("$.quantidadeDeBlocos").value(1))
                .andExpect(jsonPath("$.possuiExcesso").value(false))
                .andExpect(jsonPath("$.resumosDosDias[0].saldoEmMinutos").value(30));
        api.perform(post("/api/v1/planos-semanais/{id}/ativacao", plano)
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATIVO"));
        api.perform(delete("/api/v1/blocos-de-estudo/{id}", bloco)
                        .session(sessao).with(csrf()))
                .andExpect(status().isConflict());
        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades(180)))
                .andExpect(status().isOk());
        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades(60)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo")
                        .value("DISPONIBILIDADE_ABAIXO_DA_CARGA_PLANEJADA"));
    }

    @Test
    void deveReagendarCancelarEEncerrarPlanoAtivoComSeguranca() throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("replanejamento.a@example.com");
        String plano = criarPlano(sessaoA, SEGUNDA);
        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades(240)))
                .andExpect(status().isOk());
        String primeiro = criarBloco(
                sessaoA, plano, "Primeiro", SEGUNDA, 60, 1, null, null);
        String segundo = criarBloco(
                sessaoA, plano, "Segundo", SEGUNDA, 60, 2, null, null);
        String terceiro = criarBloco(
                sessaoA, plano, "Terceiro", SEGUNDA, 60, 3, null, null);
        api.perform(post("/api/v1/planos-semanais/{id}/ativacao", plano)
                        .session(sessaoA).with(csrf()))
                .andExpect(status().isOk());

        api.perform(post("/api/v1/blocos-de-estudo/{id}/inicio", terceiro)
                        .session(sessaoA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataDeReferencia\":\"" + SEGUNDA + "\"}"))
                .andExpect(status().isOk());
        api.perform(post("/api/v1/planos-semanais/{id}/encerramento", plano)
                        .session(sessaoA).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo")
                        .value("PLANO_POSSUI_EXECUCAO_EM_ANDAMENTO"));
        api.perform(post("/api/v1/blocos-de-estudo/{id}/interrupcao", terceiro)
                        .session(sessaoA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duracaoExecutadaEmMinutos\":10}"))
                .andExpect(status().isOk());

        api.perform(post("/api/v1/blocos-de-estudo/{id}/reagendamento", primeiro)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"" + SEGUNDA.plusDays(1)
                                + "\",\"horarioPrevisto\":\"10:00\",\"ordem\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(SEGUNDA.plusDays(1).toString()))
                .andExpect(jsonPath("$.quantidadeDeReagendamentos").value(1));
        api.perform(post("/api/v1/blocos-de-estudo/{id}/cancelamento", segundo)
                        .session(sessaoA).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));

        MockHttpSession sessaoB = criarContaEEntrar("replanejamento.b@example.com");
        api.perform(post("/api/v1/blocos-de-estudo/{id}/reagendamento", primeiro)
                        .session(sessaoB).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"" + SEGUNDA
                                + "\",\"ordem\":1}"))
                .andExpect(status().isNotFound());
        api.perform(post("/api/v1/planos-semanais/{id}/encerramento", plano)
                        .session(sessaoA))
                .andExpect(status().isForbidden());
        api.perform(post("/api/v1/planos-semanais/{id}/encerramento", plano)
                        .session(sessaoA).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENCERRADO"));
        api.perform(get("/api/v1/planejamento/hoje")
                        .param("data", SEGUNDA.toString()).session(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PLANO_ENCERRADO"));

        LocalDate semanaSeguinte = SEGUNDA.plusWeeks(1);
        String outroPlano = criarPlano(sessaoA, semanaSeguinte);
        criarBloco(sessaoA, outroPlano, "Pendente", semanaSeguinte,
                30, 1, null, null);
        api.perform(post("/api/v1/planos-semanais/{id}/cancelamento", outroPlano)
                        .session(sessaoA).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.blocos[0].estado").value("CANCELADO"));
        api.perform(get("/api/v1/planejamento/hoje")
                        .param("data", semanaSeguinte.toString()).session(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PLANO_CANCELADO"));
    }

    @Test
    void deveRecusarAtivacaoSemDisponibilidadeSemBlocosEComExcesso() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("pendencias@example.com");
        String semDisponibilidade = criarPlano(sessao, SEGUNDA);
        criarBloco(sessao, semDisponibilidade, "Teoria", SEGUNDA, 60, 1, null, null);
        api.perform(post("/api/v1/planos-semanais/{id}/ativacao", semDisponibilidade)
                        .session(sessao).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLANO_SEMANAL_SEM_DISPONIBILIDADE"));

        LocalDate outraSemana = SEGUNDA.plusWeeks(1);
        String semBlocos = criarPlano(sessao, outraSemana);
        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", semBlocos)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidadesDaSemana(outraSemana, 60)))
                .andExpect(status().isOk());
        api.perform(post("/api/v1/planos-semanais/{id}/ativacao", semBlocos)
                        .session(sessao).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLANO_SEMANAL_SEM_BLOCOS"));

        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", semDisponibilidade)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades(30)))
                .andExpect(status().isOk());
        api.perform(post("/api/v1/planos-semanais/{id}/ativacao", semDisponibilidade)
                        .session(sessao).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLANO_SEMANAL_COM_EXCESSO"));
    }

    @Test
    void deveIsolarAtivacaoPorUsuarioEExigirCsrf() throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("ativacao.a@example.com");
        String plano = criarPlano(sessaoA, SEGUNDA);
        MockHttpSession sessaoB = criarContaEEntrar("ativacao.b@example.com");

        api.perform(post("/api/v1/planos-semanais/{id}/ativacao", plano)
                        .session(sessaoB).with(csrf()))
                .andExpect(status().isNotFound());
        api.perform(post("/api/v1/planos-semanais/{id}/ativacao", plano)
                        .session(sessaoA))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveConsultarEstadosDoPlanejamentoDeHojeSemAlterarDados() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("hoje@example.com");
        api.perform(get("/api/v1/planejamento/hoje")
                        .param("data", SEGUNDA.minusWeeks(1).toString()).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SEM_PLANO"));

        String plano = criarPlano(sessao, SEGUNDA);
        api.perform(get("/api/v1/planejamento/hoje")
                        .param("data", SEGUNDA.toString()).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PLANO_EM_RASCUNHO"))
                .andExpect(jsonPath("$.identificadorDoPlano").value(plano));

        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades(180)))
                .andExpect(status().isOk());
        criarBloco(sessao, plano, "Primeiro", SEGUNDA, 60, 1, null, null);
        criarBloco(sessao, plano, "Segundo", SEGUNDA, 45, 2, null, null);
        api.perform(post("/api/v1/planos-semanais/{id}/ativacao", plano)
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk());

        api.perform(get("/api/v1/planejamento/hoje")
                        .param("data", SEGUNDA.toString()).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DIA_PLANEJADO"))
                .andExpect(jsonPath("$.minutosDisponiveis").value(180))
                .andExpect(jsonPath("$.minutosPlanejados").value(105))
                .andExpect(jsonPath("$.quantidadeDeBlocos").value(2))
                .andExpect(jsonPath("$.proximoBloco.titulo").value("Primeiro"))
                .andExpect(jsonPath("$.sequencia[0].titulo").value("Segundo"));
        api.perform(get("/api/v1/planejamento/hoje")
                        .param("data", SEGUNDA.plusDays(1).toString()).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DIA_SEM_BLOCOS"))
                .andExpect(jsonPath("$.atrasados.length()").value(2))
                .andExpect(jsonPath("$.atrasados[0].titulo").value("Primeiro"));
    }

    @Test
    void deveValidarDataAutenticacaoEIsolarConsultaDeHoje() throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("hoje.a@example.com");
        criarPlano(sessaoA, SEGUNDA);
        MockHttpSession sessaoB = criarContaEEntrar("hoje.b@example.com");

        api.perform(get("/api/v1/planejamento/hoje")
                        .param("data", SEGUNDA.toString()).session(sessaoB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SEM_PLANO"));
        api.perform(get("/api/v1/planejamento/hoje")
                        .param("data", "data-invalida").session(sessaoA))
                .andExpect(status().isBadRequest());
        api.perform(get("/api/v1/planejamento/hoje")
                        .param("data", SEGUNDA.toString()))
                .andExpect(status().isUnauthorized());
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
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private String criarPlano(MockHttpSession sessao, LocalDate inicio) throws Exception {
        String corpo = api.perform(post("/api/v1/planos-semanais")
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataInicial\":\"" + inicio + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("identificador").asString();
    }

    private String criarMateria(MockHttpSession sessao, String nome) throws Exception {
        String corpo = api.perform(post("/api/v1/materias").session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("identificador").asString();
    }

    private String criarTopico(MockHttpSession sessao, String materia, String nome)
            throws Exception {
        String corpo = api.perform(post("/api/v1/materias/{id}/topicos", materia)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"ordem\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("identificador").asString();
    }

    private String criarBloco(MockHttpSession sessao, String plano, String titulo,
            LocalDate data, int duracao, int ordem, String materia, String topico)
            throws Exception {
        String corpo = api.perform(post("/api/v1/planos-semanais/{id}/blocos", plano)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoBloco(titulo, data, duracao, ordem, materia, topico)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("/api/v1/blocos-de-estudo/")))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("identificador").asString();
    }

    private String corpoDoBloco(String titulo, LocalDate data, int duracao,
            int ordem, String materia, String topico) {
        return """
                {"titulo":"%s","tipoDeAtividade":"TEORIA","data":"%s",
                 "duracaoPrevistaEmMinutos":%d,"ordem":%d,
                 "identificadorDaMateria":%s,"identificadorDoTopico":%s,
                 "horarioPrevisto":"08:30","observacao":"Planejado manualmente"}
                """.formatted(titulo, data, duracao, ordem,
                materia == null ? "null" : "\"" + materia + "\"",
                topico == null ? "null" : "\"" + topico + "\"");
    }

    private String disponibilidades(int minutosDaSegunda) {
        return disponibilidadesDaSemana(SEGUNDA, minutosDaSegunda);
    }

    private String disponibilidadesDaSemana(LocalDate inicio, int minutosDaSegunda) {
        StringBuilder corpo = new StringBuilder("{\"disponibilidades\":[");
        for (int indice = 0; indice < 7; indice++) {
            if (indice > 0) corpo.append(',');
            corpo.append("{\"data\":\"").append(inicio.plusDays(indice))
                    .append("\",\"minutosDisponiveis\":")
                    .append(indice == 0 ? minutosDaSegunda : 0).append('}');
        }
        return corpo.append("]}").toString();
    }
}
