package br.com.trilhaaprovacao.planejamento.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.trilhaaprovacao.concursos.aplicacao.ConsultaDeMateriasElegiveisParaPlanejamento;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeMaterias;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
    @MockitoSpyBean ConsultaDeMateriasElegiveisParaPlanejamento materiasElegiveis;
    @MockitoSpyBean ServicoDeMaterias servicoDeMaterias;

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
        org.junit.jupiter.api.Assertions.assertEquals("MANUAL", banco.queryForObject("""
                SELECT origem FROM blocos_de_estudo WHERE identificador = ?::uuid
                """, String.class, bloco));
        assertThrows(DataAccessException.class, () -> banco.update("""
                UPDATE blocos_de_estudo SET origem = 'DESCONHECIDA'
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
        Integer indiceDeOrigem = banco.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE tablename = 'blocos_de_estudo'
                  AND indexname = 'idx_blocos_plano_origem'
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, indices);
        org.junit.jupiter.api.Assertions.assertEquals(1, indiceDeOrigem);
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

    @Test
    void devePersistirPrioridadesGerarPreviaDeterministicaSemCriarBlocosEIsolarUsuario()
            throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("geracao.a@example.com");
        String planoA = criarPlano(sessaoA, SEGUNDA);
        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", planoA)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades(170)))
                .andExpect(status().isOk());
        String bancoDeDados = criarMateria(sessaoA, "Banco de dados");
        String redes = criarMateria(sessaoA, "Redes");
        String seguranca = criarMateria(sessaoA, "Seguranca");
        criarEstruturaElegivel("geracao.a@example.com",
                List.of(bancoDeDados, redes, seguranca), bancoDeDados);

        api.perform(get("/api/v1/planos-semanais/{id}/materias-para-geracao", planoA)
                        .session(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materias.length()").value(3))
                .andExpect(jsonPath("$.materias[0].prioridade").value("NORMAL"));

        String prioridades = """
                {"prioridades":[
                  {"identificadorDaMateria":"%s","prioridade":"ALTA"},
                  {"identificadorDaMateria":"%s","prioridade":"NORMAL"},
                  {"identificadorDaMateria":"%s","prioridade":"BAIXA"}
                ]}
                """.formatted(bancoDeDados, redes, seguranca);
        api.perform(put("/api/v1/planos-semanais/{id}/prioridades-de-materias", planoA)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(prioridades))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materias[0].prioridade").value("ALTA"));
        assertThatQuantidade("prioridades_de_materias_no_plano", 2);
        String prioridadesEditadas = """
                {"prioridades":[
                  {"identificadorDaMateria":"%s","prioridade":"BAIXA"},
                  {"identificadorDaMateria":"%s","prioridade":"ALTA"},
                  {"identificadorDaMateria":"%s","prioridade":"NORMAL"}
                ]}
                """.formatted(bancoDeDados, redes, seguranca);
        api.perform(put("/api/v1/planos-semanais/{id}/prioridades-de-materias", planoA)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(prioridadesEditadas))
                .andExpect(status().isOk());
        api.perform(get("/api/v1/planos-semanais/{id}/materias-para-geracao", planoA)
                        .session(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materias[0].prioridade").value("BAIXA"))
                .andExpect(jsonPath("$.materias[1].prioridade").value("ALTA"));
        assertThatQuantidade("prioridades_de_materias_no_plano", 2);

        String configuracao = """
                {"duracaoPadraoDoBlocoPrincipalEmMinutos":50,
                 "duracaoDoBlocoDeRevisaoEmMinutos":20}
                """;
        String primeira = api.perform(post(
                        "/api/v1/planos-semanais/{id}/geracao-deterministica/previa", planoA)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(configuracao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias.length()").value(7))
                .andExpect(jsonPath("$.dias[0].blocosSugeridos.length()").value(4))
                .andExpect(jsonPath("$.dias[0].capacidade.minutosSugeridos").value(170))
                .andExpect(jsonPath("$.aplicada").value(false))
                .andReturn().getResponse().getContentAsString();
        String segunda = api.perform(post(
                        "/api/v1/planos-semanais/{id}/geracao-deterministica/previa", planoA)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(configuracao))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(segunda).isEqualTo(primeira);
        assertThatQuantidade("blocos_de_estudo", 0);

        api.perform(post("/api/v1/planos-semanais/{id}/geracao-deterministica/previa", planoA)
                        .session(sessaoA).contentType(MediaType.APPLICATION_JSON)
                        .content(configuracao))
                .andExpect(status().isForbidden());

        MockHttpSession sessaoB = criarContaEEntrar("geracao.b@example.com");
        api.perform(get("/api/v1/planos-semanais/{id}/materias-para-geracao", planoA)
                        .session(sessaoB))
                .andExpect(status().isNotFound());
        String planoB = criarPlano(sessaoB, SEGUNDA);
        api.perform(get("/api/v1/planos-semanais/{id}/materias-para-geracao", planoB)
                        .session(sessaoB))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONCURSO_ATIVO_NAO_ENCONTRADO"));

        banco.update("UPDATE planos_semanais SET estado = 'ATIVO' WHERE identificador = ?::uuid",
                planoA);
        api.perform(post("/api/v1/planos-semanais/{id}/geracao-deterministica/previa", planoA)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(configuracao))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PLANO_SEMANAL_NAO_ESTA_EM_RASCUNHO"));
    }

    @Test
    void deveConsultarElegibilidadeUmaVezECarregarNomesPreservadosEmLote()
            throws Exception {
        String email = "consultas.geracao@example.com";
        MockHttpSession sessao = criarContaEEntrar(email);
        String plano = criarPlano(sessao, SEGUNDA);
        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades(260)))
                .andExpect(status().isOk());
        String materiaA = criarMateria(sessao, "Banco de dados");
        String materiaB = criarMateria(sessao, "Redes");
        String materiaC = criarMateria(sessao, "Seguranca");
        String materiaManualA = criarMateria(sessao, "Materia manual A");
        String materiaManualB = criarMateria(sessao, "Materia manual B");
        criarEstruturaElegivel(email, List.of(materiaA, materiaB, materiaC), materiaA);
        criarBloco(sessao, plano, "Manual A", SEGUNDA, 30, 1, materiaManualA, null);
        criarBloco(sessao, plano, "Manual B", SEGUNDA, 30, 2, materiaManualB, null);
        UUID usuario = banco.queryForObject(
                "SELECT identificador FROM usuarios WHERE email = ?", UUID.class, email);

        clearInvocations(materiasElegiveis, servicoDeMaterias);

        api.perform(post("/api/v1/planos-semanais/{id}/geracao-deterministica/previa", plano)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"duracaoPadraoDoBlocoPrincipalEmMinutos":50,
                                 "duracaoDoBlocoDeRevisaoEmMinutos":20}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias[0].blocosPreservados[0].nomeDaMateria")
                        .value("Materia manual A"))
                .andExpect(jsonPath("$.dias[0].blocosPreservados[1].nomeDaMateria")
                        .value("Materia manual B"));

        verify(materiasElegiveis, times(1)).consultar(usuario);
        verify(servicoDeMaterias, atMost(1)).obterNomes(eq(usuario), any());
        verify(servicoDeMaterias, never()).obter(eq(usuario), any(UUID.class));
    }

    @Test
    void deveIsolarAsQuatroRotasDaGeracaoEntreUsuarios() throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("isolamento.geracao.a@example.com");
        String plano = criarPlano(sessaoA, SEGUNDA);
        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades(170)))
                .andExpect(status().isOk());
        String materiaA = criarMateria(sessaoA, "Banco de dados");
        String materiaB = criarMateria(sessaoA, "Redes");
        String materiaC = criarMateria(sessaoA, "Seguranca");
        criarEstruturaElegivel("isolamento.geracao.a@example.com",
                List.of(materiaA, materiaB, materiaC), materiaA);
        MockHttpSession sessaoB = criarContaEEntrar("isolamento.geracao.b@example.com");
        String prioridades = """
                {"prioridades":[
                  {"identificadorDaMateria":"%s","prioridade":"ALTA"},
                  {"identificadorDaMateria":"%s","prioridade":"NORMAL"},
                  {"identificadorDaMateria":"%s","prioridade":"BAIXA"}
                ]}
                """.formatted(materiaA, materiaB, materiaC);
        String configuracao = """
                {"duracaoPadraoDoBlocoPrincipalEmMinutos":50,
                 "duracaoDoBlocoDeRevisaoEmMinutos":20}
                """;

        api.perform(get("/api/v1/planos-semanais/{id}/materias-para-geracao", plano)
                        .session(sessaoB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLANO_SEMANAL_NAO_ENCONTRADO"));
        api.perform(put("/api/v1/planos-semanais/{id}/prioridades-de-materias", plano)
                        .session(sessaoB).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(prioridades))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLANO_SEMANAL_NAO_ENCONTRADO"));
        api.perform(post("/api/v1/planos-semanais/{id}/geracao-deterministica/previa", plano)
                        .session(sessaoB).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(configuracao))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLANO_SEMANAL_NAO_ENCONTRADO"));
        api.perform(post("/api/v1/planos-semanais/{id}/geracao-deterministica", plano)
                        .session(sessaoB).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaAplicacao(true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLANO_SEMANAL_NAO_ENCONTRADO"));

        assertThatQuantidade("prioridades_de_materias_no_plano", 0);
        assertThatQuantidade("blocos_de_estudo", 0);
    }

    @Test
    void deveSerializarDuasAplicacoesConcorrentesSemDuplicarPosicoes()
            throws Exception {
        String email = "concorrencia.geracao@example.com";
        MockHttpSession primeiraSessao = criarContaEEntrar(email);
        MockHttpSession segundaSessao = entrar(email);
        String plano = criarPlano(primeiraSessao, SEGUNDA);
        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(primeiraSessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades(170)))
                .andExpect(status().isOk());
        String materiaA = criarMateria(primeiraSessao, "Banco de dados");
        String materiaB = criarMateria(primeiraSessao, "Redes");
        String materiaC = criarMateria(primeiraSessao, "Seguranca");
        criarEstruturaElegivel(email, List.of(materiaA, materiaB, materiaC), materiaA);
        api.perform(post("/api/v1/planos-semanais/{id}/geracao-deterministica", plano)
                        .session(primeiraSessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaAplicacao(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.quantidadeDeBlocosCriados").value(4));
        String manual = criarBloco(primeiraSessao, plano, "Leitura manual",
                SEGUNDA, 30, 1, null, null);
        String ajustado = banco.queryForObject("""
                SELECT identificador::text FROM blocos_de_estudo
                WHERE plano_id = ?::uuid
                  AND origem = 'GERADO_DETERMINISTICAMENTE'
                  AND materia_id IS NOT NULL
                ORDER BY ordem LIMIT 1
                """, String.class, plano);
        String materiaDoAjustado = banco.queryForObject("""
                SELECT materia_id::text FROM blocos_de_estudo
                WHERE identificador = ?::uuid
                """, String.class, ajustado);
        Integer ordemDoAjustado = banco.queryForObject("""
                SELECT ordem FROM blocos_de_estudo WHERE identificador = ?::uuid
                """, Integer.class, ajustado);
        api.perform(put("/api/v1/blocos-de-estudo/{id}", ajustado)
                        .session(primeiraSessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoBloco("Ajustado antes da concorrencia", SEGUNDA, 45,
                                ordemDoAjustado, materiaDoAjustado, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origem").value("GERADO_AJUSTADO_MANUALMENTE"));
        List<String> geradosPurosAnteriores = banco.queryForList("""
                SELECT identificador::text FROM blocos_de_estudo
                WHERE plano_id = ?::uuid
                  AND origem = 'GERADO_DETERMINISTICAMENTE'
                ORDER BY identificador
                """, String.class, plano);

        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> primeira = executor.submit(() -> aplicarAposLargada(
                    primeiraSessao, plano, prontas, largada));
            Future<Integer> segunda = executor.submit(() -> aplicarAposLargada(
                    segundaSessao, plano, prontas, largada));
            org.assertj.core.api.Assertions.assertThat(
                    prontas.await(5, TimeUnit.SECONDS)).isTrue();
            largada.countDown();

            org.assertj.core.api.Assertions.assertThat(List.of(
                            primeira.get(15, TimeUnit.SECONDS),
                            segunda.get(15, TimeUnit.SECONDS)))
                    .containsExactly(200, 200);
        } finally {
            largada.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        org.assertj.core.api.Assertions.assertThat(banco.queryForObject("""
                SELECT count(*) FROM blocos_de_estudo
                WHERE plano_id = ?::uuid
                  AND origem = 'GERADO_DETERMINISTICAMENTE'
                """, Integer.class, plano)).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(banco.queryForObject("""
                SELECT count(*) FROM blocos_de_estudo
                WHERE identificador IN (?::uuid, ?::uuid)
                  AND origem IN ('MANUAL', 'GERADO_AJUSTADO_MANUALMENTE')
                """, Integer.class, manual, ajustado)).isEqualTo(2);
        for (String geradoAnterior : geradosPurosAnteriores) {
            org.assertj.core.api.Assertions.assertThat(banco.queryForObject("""
                    SELECT count(*) FROM blocos_de_estudo
                    WHERE identificador = ?::uuid
                    """, Integer.class, geradoAnterior)).isZero();
        }
        org.assertj.core.api.Assertions.assertThat(banco.queryForObject("""
                SELECT count(*) FROM blocos_de_estudo
                WHERE plano_id = ?::uuid
                """, Integer.class, plano)).isEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(banco.queryForObject("""
                SELECT count(*) FROM (
                    SELECT data, ordem
                    FROM blocos_de_estudo
                    WHERE plano_id = ?::uuid
                    GROUP BY data, ordem
                    HAVING count(*) > 1
                ) posicoes_duplicadas
                """, Integer.class, plano)).isZero();
        org.assertj.core.api.Assertions.assertThat(banco.queryForObject("""
                SELECT count(*) FROM blocos_de_estudo
                WHERE plano_id = ?::uuid
                  AND (origem IS NULL OR estado IS NULL OR ordem IS NULL)
                """, Integer.class, plano)).isZero();
    }

    @Test
    void deveAplicarEditarERegenerarPreservandoBlocosManuaisEAjustados()
            throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("aplicacao.a@example.com");
        String plano = criarPlano(sessaoA, SEGUNDA);
        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades(170)))
                .andExpect(status().isOk());
        String materiaA = criarMateria(sessaoA, "Banco de dados");
        String materiaB = criarMateria(sessaoA, "Redes");
        String materiaC = criarMateria(sessaoA, "Seguranca");
        criarEstruturaElegivel("aplicacao.a@example.com",
                List.of(materiaA, materiaB, materiaC), materiaA);
        String manual = criarBloco(sessaoA, plano, "Leitura manual",
                SEGUNDA, 30, 1, null, null);

        String primeiraAplicacao = api.perform(post(
                        "/api/v1/planos-semanais/{id}/geracao-deterministica", plano)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaAplicacao(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.quantidadeDeBlocosCriados").value(4))
                .andExpect(jsonPath("$.resumo.quantidadeDeBlocosSubstituidos").value(0))
                .andExpect(jsonPath("$.resumo.quantidadeDeBlocosPreservados").value(1))
                .andExpect(jsonPath("$.plano.blocos[0].origem").value("MANUAL"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(
                json.readTree(primeiraAplicacao).get("plano").get("blocos").valueStream()
                        .filter(item -> item.get("origem").asString()
                                .equals("GERADO_DETERMINISTICAMENTE"))
                        .count()).isEqualTo(4);

        api.perform(post("/api/v1/planos-semanais/{id}/geracao-deterministica", plano)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaAplicacao(false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("GERACAO_DETERMINISTICA_JA_APLICADA"));
        assertThatQuantidade("blocos_de_estudo", 5);
        api.perform(post("/api/v1/planos-semanais/{id}/geracao-deterministica", plano)
                        .session(sessaoA).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaAplicacao(true)))
                .andExpect(status().isForbidden());

        String geradoEditavel = banco.queryForObject("""
                SELECT identificador::text FROM blocos_de_estudo
                WHERE plano_id = ?::uuid
                  AND origem = 'GERADO_DETERMINISTICAMENTE'
                  AND materia_id IS NOT NULL
                ORDER BY ordem LIMIT 1
                """, String.class, plano);
        String materiaDoGerado = banco.queryForObject("""
                SELECT materia_id::text FROM blocos_de_estudo
                WHERE identificador = ?::uuid
                """, String.class, geradoEditavel);
        Integer ordemDoGerado = banco.queryForObject("""
                SELECT ordem FROM blocos_de_estudo WHERE identificador = ?::uuid
                """, Integer.class, geradoEditavel);
        api.perform(put("/api/v1/blocos-de-estudo/{id}", geradoEditavel)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoBloco("Bloco ajustado", SEGUNDA, 45,
                                ordemDoGerado, materiaDoGerado, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origem").value("GERADO_AJUSTADO_MANUALMENTE"))
                .andExpect(jsonPath("$.justificativaDaGeracao").isNotEmpty());

        List<String> geradosPurosAnteriores = banco.queryForList("""
                SELECT identificador::text FROM blocos_de_estudo
                WHERE plano_id = ?::uuid AND origem = 'GERADO_DETERMINISTICAMENTE'
                """, String.class, plano);
        api.perform(post("/api/v1/planos-semanais/{id}/geracao-deterministica", plano)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaAplicacao(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.quantidadeDeBlocosSubstituidos")
                        .value(geradosPurosAnteriores.size()))
                .andExpect(jsonPath("$.resumo.quantidadeDeBlocosPreservados").value(2));
        org.assertj.core.api.Assertions.assertThat(banco.queryForObject("""
                SELECT count(*) FROM blocos_de_estudo
                WHERE identificador IN (?::uuid, ?::uuid)
                """, Integer.class, manual, geradoEditavel)).isEqualTo(2);
        for (String substituido : geradosPurosAnteriores) {
            org.assertj.core.api.Assertions.assertThat(banco.queryForObject("""
                    SELECT count(*) FROM blocos_de_estudo WHERE identificador = ?::uuid
                    """, Integer.class, substituido)).isZero();
        }

        MockHttpSession sessaoB = criarContaEEntrar("aplicacao.b@example.com");
        api.perform(post("/api/v1/planos-semanais/{id}/geracao-deterministica", plano)
                        .session(sessaoB).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaAplicacao(true)))
                .andExpect(status().isNotFound());
        api.perform(post("/api/v1/planos-semanais/{id}/ativacao", plano)
                        .session(sessaoA).with(csrf()))
                .andExpect(status().isOk());
        api.perform(post("/api/v1/planos-semanais/{id}/geracao-deterministica", plano)
                        .session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaAplicacao(true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PLANO_SEMANAL_NAO_ESTA_EM_RASCUNHO"));
    }

    @Test
    void deveReverterSubstituicaoQuandoPersistenciaDaNovaGeracaoFalha()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("rollback.geracao@example.com");
        String plano = criarPlano(sessao, SEGUNDA);
        api.perform(put("/api/v1/planos-semanais/{id}/disponibilidades", plano)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(disponibilidades(170)))
                .andExpect(status().isOk());
        String materiaA = criarMateria(sessao, "Banco de dados");
        String materiaB = criarMateria(sessao, "Redes");
        String materiaC = criarMateria(sessao, "Seguranca");
        criarEstruturaElegivel("rollback.geracao@example.com",
                List.of(materiaA, materiaB, materiaC), materiaA);
        api.perform(post("/api/v1/planos-semanais/{id}/geracao-deterministica", plano)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaAplicacao(false)))
                .andExpect(status().isOk());
        List<String> identificadoresOriginais = banco.queryForList("""
                SELECT identificador::text FROM blocos_de_estudo
                WHERE plano_id = ?::uuid ORDER BY identificador
                """, String.class, plano);

        banco.execute("""
                ALTER TABLE blocos_de_estudo
                ADD CONSTRAINT ck_rejeita_nova_geracao
                CHECK (origem <> 'GERADO_DETERMINISTICAMENTE') NOT VALID
                """);
        try {
            api.perform(post("/api/v1/planos-semanais/{id}/geracao-deterministica", plano)
                            .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(corpoDaAplicacao(true)))
                    .andExpect(status().isConflict());
        } finally {
            banco.execute("""
                    ALTER TABLE blocos_de_estudo
                    DROP CONSTRAINT IF EXISTS ck_rejeita_nova_geracao
                    """);
        }
        List<String> identificadoresDepoisDaFalha = banco.queryForList("""
                SELECT identificador::text FROM blocos_de_estudo
                WHERE plano_id = ?::uuid ORDER BY identificador
                """, String.class, plano);
        org.assertj.core.api.Assertions.assertThat(identificadoresDepoisDaFalha)
                .containsExactlyElementsOf(identificadoresOriginais);
    }

    private MockHttpSession criarContaEEntrar(String email) throws Exception {
        api.perform(post("/api/v1/autenticacao/cadastro").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Pessoa","email":"%s","senha":"senha-segura-123"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return entrar(email);
    }

    private MockHttpSession entrar(String email) throws Exception {
        return (MockHttpSession) api.perform(post("/api/v1/autenticacao/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"senha-segura-123"}
                                """.formatted(email)))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private int aplicarAposLargada(MockHttpSession sessao, String plano,
            CountDownLatch prontas, CountDownLatch largada) throws Exception {
        prontas.countDown();
        if (!largada.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("As aplicacoes concorrentes nao ficaram prontas.");
        }
        return api.perform(post("/api/v1/planos-semanais/{id}/geracao-deterministica", plano)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDaAplicacao(true)))
                .andReturn().getResponse().getStatus();
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

    private void criarEstruturaElegivel(String email, List<String> materias,
            String materiaRepetida) {
        UUID usuario = banco.queryForObject(
                "SELECT identificador FROM usuarios WHERE email = ?", UUID.class, email);
        UUID concurso = UUID.randomUUID();
        UUID cargo = UUID.randomUUID();
        UUID prova = UUID.randomUUID();
        UUID grupoA = UUID.randomUUID();
        UUID grupoB = UUID.randomUUID();
        banco.update("""
                INSERT INTO concursos (identificador, usuario_id, nome, nome_normalizado,
                    situacao, ativo, criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Concurso ativo', 'concurso ativo', 'PLANEJADO', TRUE,
                    now(), now(), 0)
                """, concurso, usuario);
        banco.update("""
                INSERT INTO cargos_do_concurso (identificador, concurso_id, nome,
                    nome_normalizado, nivel_de_escolaridade, selecionado, ordem,
                    criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Auditor', 'auditor', 'SUPERIOR', TRUE, 1, now(), now(), 0)
                """, cargo, concurso);
        banco.update("""
                INSERT INTO provas (identificador, cargo_id, nome, nome_normalizado,
                    tipo, carater, ordem, criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Objetiva', 'objetiva', 'OBJETIVA', 'ELIMINATORIO', 1,
                    now(), now(), 0)
                """, prova, cargo);
        banco.update("""
                INSERT INTO grupos_de_conteudo (identificador, prova_id, nome,
                    nome_normalizado, ordem, criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Basicos', 'basicos', 1, now(), now(), 0),
                       (?, ?, 'Especificos', 'especificos', 2, now(), now(), 0)
                """, grupoA, prova, grupoB, prova);
        int ordem = 1;
        for (String materia : materias) {
            banco.update("""
                    INSERT INTO materias_da_prova (identificador, grupo_de_conteudo_id,
                        materia_id, ordem, criado_em, atualizado_em, versao)
                    VALUES (?, ?, ?::uuid, ?, now(), now(), 0)
                    """, UUID.randomUUID(), grupoA, materia, ordem++);
        }
        banco.update("""
                INSERT INTO materias_da_prova (identificador, grupo_de_conteudo_id,
                    materia_id, ordem, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?::uuid, 1, now(), now(), 0)
                """, UUID.randomUUID(), grupoB, materiaRepetida);
    }

    private void assertThatQuantidade(String tabela, int esperada) {
        Integer quantidade = banco.queryForObject(
                "SELECT count(*) FROM " + tabela, Integer.class);
        org.assertj.core.api.Assertions.assertThat(quantidade).isEqualTo(esperada);
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

    private String corpoDaAplicacao(boolean substituir) {
        return """
                {"duracaoPadraoDoBlocoPrincipalEmMinutos":50,
                 "duracaoDoBlocoDeRevisaoEmMinutos":20,
                 "substituirBlocosGerados":%s}
                """.formatted(substituir);
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
