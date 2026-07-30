package br.com.trilhaaprovacao.revisoes.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "debug=false")
@AutoConfigureMockMvc
@Testcontainers
class RevisoesEspacadasIntegracaoTest {
    private static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate REFERENCIA = LocalDate.of(2026, 7, 21);

    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_revisoes")
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
    @Autowired ObjectMapper mapeador;

    @BeforeEach
    void limparBanco() {
        banco.execute("""
                TRUNCATE TABLE ocorrencias_de_padrao_de_erro, padroes_de_erro,
                    evidencias_de_aprendizagem, execucoes_de_bloco,
                    blocos_de_estudo, disponibilidades_do_dia, planos_semanais,
                    registros_de_estudo, coberturas_de_topicos_por_material,
                    materiais_de_estudo, mapeamentos_de_itens_do_edital,
                    itens_do_edital, materias_da_prova, grupos_de_conteudo,
                    provas, cargos_do_concurso, editais, concursos,
                    topicos_da_materia, materias, usuarios CASCADE
                """);
    }

    @Test
    void deveExigirSessaoContextoPeriodoValidoEIdentidadeExistente()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("revisoes.contexto@example.com");

        api.perform(consulta(REFERENCIA, REFERENCIA.plusDays(7)))
                .andExpect(status().isUnauthorized());

        api.perform(consulta(REFERENCIA, REFERENCIA.plusDays(7)).session(sessao))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONTEXTO_DE_REVISOES_INCOMPLETO"));

        UUID usuario = usuario("revisoes.contexto@example.com");
        UUID materia = inserirMateria(usuario, "Direito");
        inserirContextoOficial(usuario, materia);
        api.perform(get("/api/v1/revisoes-espacadas").session(sessao)
                        .param("dataDeReferencia", REFERENCIA.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ENTRADA_INVALIDA"));
        api.perform(consulta(REFERENCIA, REFERENCIA.minusDays(1)).session(sessao))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PERIODO_DE_REVISOES_INVALIDO"));

        banco.update("UPDATE usuarios SET email = ? WHERE identificador = ?",
                "revisoes.renomeada@example.com", usuario);
        api.perform(consulta(REFERENCIA, REFERENCIA.plusDays(7)).session(sessao))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo")
                        .value("USUARIO_DA_SESSAO_NAO_ENCONTRADO"));
    }

    @Test
    void deveCalcularAgendaTemporalSituacoesHorizonteEBlocoAberto()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("revisoes.calculo@example.com");
        UUID usuario = usuario("revisoes.calculo@example.com");
        UUID materia = inserirMateria(usuario, "Direito Administrativo");
        Contexto contexto = inserirContextoOficial(usuario, materia);

        UUID vencida = topicoMapeado(contexto, materia, "Vencida", 1);
        UUID devida = topicoMapeado(contexto, materia, "Devida hoje", 2);
        UUID futura = topicoMapeado(contexto, materia, "Futura", 3);
        UUID progressao = topicoMapeado(contexto, materia, "Progressao diaria", 4);
        UUID fatosInvalidos = topicoMapeado(contexto, materia, "Fatos invalidos", 5);
        UUID semEvidencia = topicoMapeado(contexto, materia, "Sem evidencia", 6);
        UUID foraDoHorizonte = topicoMapeado(contexto, materia, "Fora do horizonte", 7);
        UUID planejada = topicoMapeado(contexto, materia, "Planejada", 8);
        UUID revisaoParcial = topicoMapeado(contexto, materia, "Revisao parcial", 9);

        inserirFato(vencida, instante(REFERENCIA.minusDays(5), 8),
                "TEORIA", "ATIVO", null, 3);
        inserirFato(devida, instante(REFERENCIA.minusDays(1), 8),
                "TEORIA", "ATIVO", null, 3);
        inserirFato(futura, instante(REFERENCIA, 8),
                "TEORIA", "ATIVO", null, 3);

        inserirFato(progressao, instante(REFERENCIA.minusDays(10), 8),
                "TEORIA", "ATIVO", null, 3);
        inserirFato(progressao, instante(REFERENCIA.minusDays(3), 9),
                "REVISAO", "ATIVO", 5, null);
        inserirFato(progressao, instante(REFERENCIA.minusDays(3), 11),
                "REVISAO", "ATIVO", 4, null);
        inserirFato(progressao, instante(REFERENCIA.minusDays(1), 12),
                "QUESTOES", "ATIVO", null, 4);

        inserirFato(fatosInvalidos, instante(REFERENCIA.minusDays(1), 8),
                "TEORIA", "ATIVO", null, 3);
        inserirFato(fatosInvalidos, instante(REFERENCIA, 8),
                "REVISAO", "CORRIGIDO", 5, null);
        inserirFato(fatosInvalidos, instante(REFERENCIA, 9),
                "REVISAO", "CANCELADO", 5, null);
        inserirFato(fatosInvalidos, instante(REFERENCIA.plusDays(1), 8),
                "REVISAO", "ATIVO", 5, null);

        inserirEstudo(semEvidencia, instante(REFERENCIA.minusDays(1), 8),
                "TEORIA", "ATIVO");
        inserirFato(foraDoHorizonte, instante(REFERENCIA, 8),
                "REVISAO", "ATIVO", 5, null);
        inserirFato(planejada, instante(REFERENCIA.minusDays(1), 8),
                "TEORIA", "ATIVO", null, 3);
        inserirFato(revisaoParcial, instante(REFERENCIA.minusDays(1), 8),
                "TEORIA", "ATIVO", null, 3);
        inserirFato(revisaoParcial, instante(REFERENCIA, 8),
                "REVISAO", "ATIVO", null, 4);

        Bloco bloco = inserirBlocoAberto(usuario, materia, planejada,
                REFERENCIA.plusDays(2), "RASCUNHO", "PLANEJADO");
        inserirBloco(usuario, materia, devida, REFERENCIA, "CANCELADO", "PLANEJADO");

        String resposta = api.perform(consulta(REFERENCIA, REFERENCIA.plusDays(3))
                        .session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataDeReferencia").value(REFERENCIA.toString()))
                .andExpect(jsonPath("$.ate").value(REFERENCIA.plusDays(3).toString()))
                .andExpect(jsonPath("$.capacidadeDaFila.limiteDePrioridades").value(3))
                .andExpect(jsonPath("$.capacidadeDaFila.duracaoEstimadaPorRevisaoEmMinutos")
                        .value(20))
                .andExpect(jsonPath("$.revisoes.length()").value(7))
                .andReturn().getResponse().getContentAsString();
        JsonNode agenda = mapeador.readTree(resposta);

        JsonNode itemVencido = revisao(agenda, "Vencida");
        assertThat(itemVencido.path("situacao").asString()).isEqualTo("VENCIDA");
        assertThat(itemVencido.path("dataDevida").asString())
                .isEqualTo(REFERENCIA.minusDays(4).toString());
        assertThat(itemVencido.path("diasEmAtraso").asLong()).isEqualTo(4);

        JsonNode itemDevido = revisao(agenda, "Devida hoje");
        assertThat(itemDevido.path("situacao").asString()).isEqualTo("DEVIDA_HOJE");
        assertThat(itemDevido.path("etapa").asInt()).isZero();

        JsonNode itemFuturo = revisao(agenda, "Futura");
        assertThat(itemFuturo.path("situacao").asString()).isEqualTo("FUTURA");
        assertThat(itemFuturo.path("dataDevida").asString())
                .isEqualTo(REFERENCIA.plusDays(1).toString());

        JsonNode itemProgressao = revisao(agenda, "Progressao diaria");
        assertThat(itemProgressao.path("etapa").asInt()).isEqualTo(1);
        assertThat(itemProgressao.path("intervaloEmDias").asInt()).isEqualTo(3);
        assertThat(itemProgressao.path("ultimaRevisao").asString())
                .isEqualTo(REFERENCIA.minusDays(3).toString());
        assertThat(itemProgressao.path("ultimaRecordacao").asInt()).isEqualTo(4);
        assertThat(itemProgressao.path("dataDevida").asString())
                .isEqualTo(REFERENCIA.toString());

        JsonNode itemComFatosInvalidos = revisao(agenda, "Fatos invalidos");
        assertThat(itemComFatosInvalidos.path("etapa").asInt()).isZero();
        assertThat(itemComFatosInvalidos.path("ultimaRevisao").isNull()).isTrue();
        assertThat(itemComFatosInvalidos.path("dataDevida").asString())
                .isEqualTo(REFERENCIA.toString());

        JsonNode itemPlanejado = revisao(agenda, "Planejada");
        assertThat(itemPlanejado.path("situacao").asString()).isEqualTo("JA_PLANEJADA");
        assertThat(itemPlanejado.at("/blocoAberto/identificador").asString())
                .isEqualTo(bloco.identificador().toString());
        assertThat(itemPlanejado.at("/blocoAberto/identificadorDoPlano").asString())
                .isEqualTo(bloco.identificadorDoPlano().toString());
        assertThat(itemPlanejado.at("/blocoAberto/dataInicialDoPlano").asString())
                .isEqualTo(bloco.dataInicialDoPlano().toString());
        assertThat(itemPlanejado.at("/blocoAberto/data").asString())
                .isEqualTo(REFERENCIA.plusDays(2).toString());
        assertThat(itemPlanejado.at("/blocoAberto/estado").asString())
                .isEqualTo("PLANEJADO");

        JsonNode itemParcial = revisao(agenda, "Revisao parcial");
        assertThat(itemParcial.path("etapa").asInt()).isZero();
        assertThat(itemParcial.path("ultimaRevisao").isNull()).isTrue();
        assertThat(nomes(agenda)).doesNotContain("Sem evidencia", "Fora do horizonte");
    }

    @Test
    void deveLimitarAConteudoOficialAtivoConfirmadoIsolarUsuariosENaoEscrever()
            throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("revisoes.a@example.com");
        MockHttpSession sessaoB = criarContaEEntrar("revisoes.b@example.com");
        UUID usuarioA = usuario("revisoes.a@example.com");
        UUID usuarioB = usuario("revisoes.b@example.com");

        UUID materiaA = inserirMateria(usuarioA, "Materia A");
        Contexto contextoA = inserirContextoOficial(usuarioA, materiaA);
        UUID oficialA = topicoMapeado(contextoA, materiaA, "Oficial A", 1);
        UUID naoConfirmado = inserirTopico(materiaA, "Nao confirmado", 2, false);
        mapearItem(contextoA, naoConfirmado, "Nao confirmado", 2, false);
        UUID arquivado = topicoMapeado(contextoA, materiaA, "Arquivado", 3);
        banco.update("UPDATE topicos_da_materia SET arquivado = TRUE WHERE identificador = ?",
                arquivado);
        inserirFato(oficialA, instante(REFERENCIA.minusDays(1), 8),
                "TEORIA", "ATIVO", null, 3);
        inserirFato(naoConfirmado, instante(REFERENCIA.minusDays(1), 8),
                "TEORIA", "ATIVO", null, 3);
        inserirFato(arquivado, instante(REFERENCIA.minusDays(1), 8),
                "TEORIA", "ATIVO", null, 3);

        UUID materiaB = inserirMateria(usuarioB, "Materia B");
        Contexto contextoB = inserirContextoOficial(usuarioB, materiaB);
        UUID oficialB = topicoMapeado(contextoB, materiaB, "Oficial B", 1);
        inserirFato(oficialB, instante(REFERENCIA.minusDays(1), 8),
                "TEORIA", "ATIVO", null, 3);
        inserirBloco(usuarioB, materiaA, oficialA, REFERENCIA,
                "RASCUNHO", "PLANEJADO");

        Map<String, Long> antes = contagens();
        String respostaA = api.perform(consulta(REFERENCIA, REFERENCIA.plusDays(7))
                        .session(sessaoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisoes.length()").value(1))
                .andExpect(jsonPath("$.revisoes[0].identificadorDoTopico")
                        .value(oficialA.toString()))
                .andReturn().getResponse().getContentAsString();
        String respostaB = api.perform(consulta(REFERENCIA, REFERENCIA.plusDays(7))
                        .session(sessaoB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisoes.length()").value(1))
                .andExpect(jsonPath("$.revisoes[0].identificadorDoTopico")
                        .value(oficialB.toString()))
                .andReturn().getResponse().getContentAsString();

        assertThat(respostaA).doesNotContain("Oficial B", "Nao confirmado", "Arquivado");
        assertThat(respostaB).doesNotContain("Oficial A");
        assertThat(revisao(mapeador.readTree(respostaA), "Oficial A")
                .path("situacao").asString()).isEqualTo("DEVIDA_HOJE");
        assertThat(contagens()).isEqualTo(antes);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder consulta(
            LocalDate referencia, LocalDate ate) {
        return get("/api/v1/revisoes-espacadas")
                .param("dataDeReferencia", referencia.toString())
                .param("ate", ate.toString());
    }

    private JsonNode revisao(JsonNode agenda, String nome) {
        return agenda.path("revisoes").valueStream()
                .filter(item -> nome.equals(item.path("nomeDoTopico").asString()))
                .findFirst().orElseThrow();
    }

    private java.util.List<String> nomes(JsonNode agenda) {
        return agenda.path("revisoes").valueStream()
                .map(item -> item.path("nomeDoTopico").asString()).toList();
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
        return banco.queryForObject("SELECT identificador FROM usuarios WHERE email = ?",
                UUID.class, email);
    }

    private UUID inserirMateria(UUID usuario, String nome) {
        UUID identificador = UUID.randomUUID();
        banco.update("""
                INSERT INTO materias (identificador, usuario_id, nome, nome_normalizado,
                  arquivada, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, FALSE, ?, ?, 0)
                """, identificador, usuario, nome, nome.toLowerCase(), agora(), agora());
        return identificador;
    }

    private UUID inserirTopico(UUID materia, String nome, int ordem, boolean arquivado) {
        UUID identificador = UUID.randomUUID();
        banco.update("""
                INSERT INTO topicos_da_materia (identificador, materia_id, nome,
                  nome_normalizado, ordem, arquivado, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, identificador, materia, nome, nome.toLowerCase(), ordem, arquivado,
                agora(), agora());
        return identificador;
    }

    private UUID topicoMapeado(Contexto contexto, UUID materia, String nome, int ordem) {
        UUID topico = inserirTopico(materia, nome, ordem, false);
        mapearItem(contexto, topico, nome, ordem, true);
        return topico;
    }

    private Contexto inserirContextoOficial(UUID usuario, UUID materia) {
        UUID concurso = UUID.randomUUID();
        banco.update("""
                INSERT INTO concursos (identificador, usuario_id, nome, nome_normalizado,
                  situacao, ativo, criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Concurso', 'concurso', 'EM_ANDAMENTO', TRUE, ?, ?, 0)
                """, concurso, usuario, agora(), agora());
        UUID cargo = UUID.randomUUID();
        banco.update("""
                INSERT INTO cargos_do_concurso (identificador, concurso_id, nome,
                  nome_normalizado, nivel_de_escolaridade, selecionado, ordem,
                  criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Cargo', 'cargo', 'SUPERIOR', TRUE, 1, ?, ?, 0)
                """, cargo, concurso, agora(), agora());
        UUID prova = UUID.randomUUID();
        banco.update("""
                INSERT INTO provas (identificador, cargo_id, nome, nome_normalizado,
                  tipo, carater, ordem, criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Prova', 'prova', 'OBJETIVA', 'CLASSIFICATORIO', 1, ?, ?, 0)
                """, prova, cargo, agora(), agora());
        UUID grupo = UUID.randomUUID();
        banco.update("""
                INSERT INTO grupos_de_conteudo (identificador, prova_id, nome,
                  nome_normalizado, ordem, criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Conhecimentos', 'conhecimentos', 1, ?, ?, 0)
                """, grupo, prova, agora(), agora());
        UUID materiaDaProva = UUID.randomUUID();
        banco.update("""
                INSERT INTO materias_da_prova (identificador, grupo_de_conteudo_id,
                  materia_id, ordem, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, 1, ?, ?, 0)
                """, materiaDaProva, grupo, materia, agora(), agora());
        UUID edital = UUID.randomUUID();
        banco.update("""
                INSERT INTO editais (identificador, concurso_id, titulo, principal,
                  criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Edital principal', TRUE, ?, ?, 0)
                """, edital, concurso, agora(), agora());
        return new Contexto(cargo, edital, materiaDaProva);
    }

    private void mapearItem(Contexto contexto, UUID topico, String descricao,
            int ordem, boolean confirmado) {
        UUID item = UUID.randomUUID();
        banco.update("""
                INSERT INTO itens_do_edital (identificador, edital_id,
                  materia_da_prova_id, descricao_original, ordem, criado_em,
                  atualizado_em, versao)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                """, item, contexto.edital(), contexto.materiaDaProva(), descricao, ordem,
                agora(), agora());
        banco.update("""
                INSERT INTO mapeamentos_de_itens_do_edital (identificador,
                  item_do_edital_id, topico_da_materia_id, confirmado, criado_em)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), item, topico, confirmado, agora());
    }

    private UUID inserirEstudo(UUID topico, OffsetDateTime instante,
            String tipo, String situacao) {
        UUID estudo = UUID.randomUUID();
        banco.update("""
                INSERT INTO registros_de_estudo (identificador, topico_id,
                  data_hora, duracao_em_minutos, situacao, tipo_de_estudo,
                  criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, 45, ?, ?, ?, ?, 0)
                """, estudo, topico, instante, situacao, tipo, instante, instante);
        return estudo;
    }

    private UUID inserirFato(UUID topico, OffsetDateTime instante, String tipo,
            String situacao, Integer recordacao, Integer dificuldade) {
        UUID estudo = inserirEstudo(topico, instante, tipo, situacao);
        UUID evidencia = UUID.randomUUID();
        banco.update("""
                INSERT INTO evidencias_de_aprendizagem (identificador,
                  registro_de_estudo_id, nivel_de_recordacao, dificuldade_percebida,
                  criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, ?, ?, 0)
                """, evidencia, estudo, recordacao, dificuldade, instante, instante);
        return evidencia;
    }

    private Bloco inserirBlocoAberto(UUID usuario, UUID materia, UUID topico,
            LocalDate data, String estadoDoPlano, String estadoDoBloco) {
        return inserirBloco(usuario, materia, topico, data, estadoDoPlano, estadoDoBloco);
    }

    private Bloco inserirBloco(UUID usuario, UUID materia, UUID topico,
            LocalDate data, String estadoDoPlano, String estadoDoBloco) {
        UUID plano = UUID.randomUUID();
        LocalDate inicio = data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        banco.update("""
                INSERT INTO planos_semanais (identificador, usuario_id, data_inicial,
                  estado, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, ?, ?, 0)
                """, plano, usuario, inicio, estadoDoPlano, agora(), agora());
        UUID bloco = UUID.randomUUID();
        banco.update("""
                INSERT INTO blocos_de_estudo (identificador, plano_id, materia_id,
                  topico_id, titulo, tipo_de_atividade, data,
                  duracao_prevista_em_minutos, ordem, estado,
                  criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, 'Revisao', 'REVISAO', ?, 30, 1, ?, ?, ?, 0)
                """, bloco, plano, materia, topico, data, estadoDoBloco, agora(), agora());
        return new Bloco(bloco, plano, inicio);
    }

    private Map<String, Long> contagens() {
        Map<String, Long> contagens = new LinkedHashMap<>();
        for (String tabela : new String[]{"registros_de_estudo",
                "evidencias_de_aprendizagem", "planos_semanais", "blocos_de_estudo"}) {
            Long total = banco.queryForObject("SELECT COUNT(*) FROM " + tabela, Long.class);
            contagens.put(tabela, total == null ? 0 : total);
        }
        return contagens;
    }

    private OffsetDateTime instante(LocalDate data, int hora) {
        return data.atTime(hora, 0).atZone(FUSO_HORARIO).toOffsetDateTime();
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(FUSO_HORARIO);
    }

    private record Contexto(UUID cargo, UUID edital, UUID materiaDaProva) {
    }

    private record Bloco(
            UUID identificador, UUID identificadorDoPlano, LocalDate dataInicialDoPlano) {
    }
}
