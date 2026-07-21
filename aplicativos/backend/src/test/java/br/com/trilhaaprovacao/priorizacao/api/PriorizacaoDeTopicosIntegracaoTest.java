package br.com.trilhaaprovacao.priorizacao.api;

import static org.assertj.core.api.Assertions.assertThat;
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
class PriorizacaoDeTopicosIntegracaoTest {
    private static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate REFERENCIA = LocalDate.of(2026, 7, 21);

    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_priorizacao")
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
    void deveExigirSessaoEContextoOficialCompleto() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("sem.contexto@example.com");

        api.perform(get("/api/v1/priorizacao-de-topicos")
                        .param("dataDeReferencia", REFERENCIA.toString()))
                .andExpect(status().isUnauthorized());

        api.perform(get("/api/v1/priorizacao-de-topicos").session(sessao)
                        .param("dataDeReferencia", REFERENCIA.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo")
                        .value("CONTEXTO_DE_PRIORIZACAO_INCOMPLETO"));
    }

    @Test
    void deveCruzarEditalPrincipalCoberturaEvidenciasEIsolarUsuarios()
            throws Exception {
        MockHttpSession sessaoA = criarContaEEntrar("priorizacao.a@example.com");
        MockHttpSession sessaoB = criarContaEEntrar("priorizacao.b@example.com");
        UUID usuarioA = usuario("priorizacao.a@example.com");
        UUID usuarioB = usuario("priorizacao.b@example.com");

        UUID materia = inserirMateria(usuarioA, "Direito Administrativo");
        UUID semEstudo = inserirTopico(materia, "Atos administrativos", 1);
        UUID semEvidencia = inserirTopico(materia, "Poderes administrativos", 2);
        UUID reforco = inserirTopico(materia, "Licitacoes", 3);
        UUID reforcoSemPadrao = inserirTopico(materia, "Responsabilidade civil", 4);
        UUID consolidado = inserirTopico(materia, "Servicos publicos", 5);
        Contexto contexto = inserirContextoOficial(usuarioA, materia);
        mapearItem(contexto, semEstudo, "Atos", 1);
        mapearItem(contexto, semEvidencia, "Poderes", 2);
        mapearItem(contexto, reforco, "Licitacoes", 3);
        mapearItem(contexto, reforcoSemPadrao, "Responsabilidade", 4);
        mapearItem(contexto, consolidado, "Servicos", 5);
        inserirItem(contexto.edital(), contexto.materiaDaProva(),
                "Agentes publicos", 6);

        inserirEstudo(semEvidencia, REFERENCIA.minusDays(2), "TEORIA", "ATIVO");
        UUID estudoReforcoUm = inserirEstudo(
                reforco, REFERENCIA.minusDays(2), "QUESTOES", "ATIVO");
        UUID estudoReforcoDois = inserirEstudo(
                reforco, REFERENCIA.minusDays(1), "QUESTOES", "ATIVO");
        UUID evidenciaUm = inserirEvidencia(estudoReforcoUm, 10, 6, null, 4);
        UUID evidenciaDois = inserirEvidencia(estudoReforcoDois, 10, 7, null, 4);
        inserirPadraoRepetido(usuarioA, reforco, evidenciaUm, evidenciaDois);
        UUID estudoReforcoSemPadrao = inserirEstudo(
                reforcoSemPadrao, REFERENCIA, "QUESTOES", "ATIVO");
        inserirEvidencia(estudoReforcoSemPadrao, 20, 13, null, 5);

        UUID estudoConsolidado = inserirEstudo(
                consolidado, REFERENCIA, "QUESTOES", "ATIVO");
        inserirEvidencia(estudoConsolidado, 20, 17, null, 2);
        inserirMaterialComCobertura(usuarioA, consolidado);

        UUID estudoCancelado = inserirEstudo(
                consolidado, REFERENCIA, "QUESTOES", "CANCELADO");
        inserirEvidencia(estudoCancelado, 20, 0, null, 5);
        UUID estudoFuturo = inserirEstudo(
                consolidado, REFERENCIA.plusDays(1), "QUESTOES", "ATIVO");
        inserirEvidencia(estudoFuturo, 20, 0, null, 5);

        inserirConteudoForaDoContexto(materia, semEstudo, contexto.concurso());
        long estudosAntes = contar("registros_de_estudo");
        long evidenciasAntes = contar("evidencias_de_aprendizagem");

        String primeiraResposta = api.perform(
                        get("/api/v1/priorizacao-de-topicos").session(sessaoA)
                        .param("dataDeReferencia", REFERENCIA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contexto.concurso.nome").value("Concurso A"))
                .andExpect(jsonPath("$.contexto.cargo.nome").value("Auditor"))
                .andExpect(jsonPath("$.contexto.edital.nome").value("Edital principal"))
                .andExpect(jsonPath("$.contexto.inicioJanelaRecente").value("2026-06-22"))
                .andExpect(jsonPath("$.resumo.itensOficiais").value(6))
                .andExpect(jsonPath("$.resumo.itensSemMapeamento").value(1))
                .andExpect(jsonPath("$.resumo.topicosExigidos").value(5))
                .andExpect(jsonPath("$.resumo.lacunas").value(2))
                .andExpect(jsonPath("$.resumo.fraquezas").value(2))
                .andExpect(jsonPath("$.resumo.consolidados").value(1))
                .andExpect(jsonPath("$.itensSemMapeamento[0].descricao")
                        .value("Agentes publicos"))
                .andExpect(jsonPath("$.materias[0].topicos[0].nome")
                        .value("Atos administrativos"))
                .andExpect(jsonPath("$.materias[0].topicos[0].faixa")
                        .value("SEM_ESTUDO"))
                .andExpect(jsonPath("$.materias[0].topicos[0].acaoSugerida")
                        .value("TEORIA"))
                .andExpect(jsonPath("$.materias[0].topicos[0].possuiMaterial")
                        .value(false))
                .andExpect(jsonPath("$.materias[0].topicos[0].posicaoNoGrupo")
                        .value(1))
                .andExpect(jsonPath("$.materias[0].topicos[1].faixa")
                        .value("SEM_EVIDENCIA"))
                .andExpect(jsonPath("$.materias[0].topicos[1].posicaoNoGrupo")
                        .value(2))
                .andExpect(jsonPath("$.materias[0].topicos[2].nome")
                        .value("Licitacoes"))
                .andExpect(jsonPath("$.materias[0].topicos[2].faixa")
                        .value("PRECISA_REFORCO"))
                .andExpect(jsonPath("$.materias[0].topicos[2].indicadores.questoesRecentes")
                        .value(20))
                .andExpect(jsonPath("$.materias[0].topicos[2].indicadores.percentual")
                        .value(65.00))
                .andExpect(jsonPath("$.materias[0].topicos[2].indicadores.quantidadePadroesRepetidos")
                        .value(1))
                .andExpect(jsonPath("$.materias[0].topicos[2].posicaoNoGrupo")
                        .value(1))
                .andExpect(jsonPath("$.materias[0].topicos[3].nome")
                        .value("Responsabilidade civil"))
                .andExpect(jsonPath("$.materias[0].topicos[3].faixa")
                        .value("PRECISA_REFORCO"))
                .andExpect(jsonPath("$.materias[0].topicos[3].posicaoNoGrupo")
                        .value(2))
                .andExpect(jsonPath("$.materias[0].topicos[4].nome")
                        .value("Servicos publicos"))
                .andExpect(jsonPath("$.materias[0].topicos[4].faixa")
                        .value("CONSOLIDADO"))
                .andExpect(jsonPath("$.materias[0].topicos[4].possuiMaterial")
                        .value(true))
                .andExpect(jsonPath("$.materias[0].topicos[4].indicadores.questoesRecentes")
                        .value(20))
                .andExpect(jsonPath("$.materias[0].topicos[4].posicaoNoGrupo")
                        .value(1))
                .andReturn().getResponse().getContentAsString();

        String segundaResposta = api.perform(
                        get("/api/v1/priorizacao-de-topicos").session(sessaoA)
                                .param("dataDeReferencia", REFERENCIA.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(segundaResposta).isEqualTo(primeiraResposta);

        api.perform(get("/api/v1/priorizacao-de-topicos").session(sessaoA)
                        .param("dataDeReferencia", REFERENCIA.toString())
                        .param("identificadorDaMateria", materia.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materias.length()").value(1));

        UUID materiaDoUsuarioB = inserirMateria(usuarioB, "Materia B");
        UUID topicoDoUsuarioB = inserirTopico(materiaDoUsuarioB, "Topico B", 1);
        Contexto contextoB = inserirContextoOficial(usuarioB, materiaDoUsuarioB);
        mapearItem(contextoB, topicoDoUsuarioB, "Item B", 1);
        api.perform(get("/api/v1/priorizacao-de-topicos").session(sessaoA)
                        .param("dataDeReferencia", REFERENCIA.toString())
                        .param("identificadorDaMateria", materiaDoUsuarioB.toString()))
                .andExpect(status().isNotFound());
        api.perform(get("/api/v1/priorizacao-de-topicos").session(sessaoB)
                        .param("dataDeReferencia", REFERENCIA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contexto.concurso.identificador")
                        .value(contextoB.concurso().toString()))
                .andExpect(jsonPath("$.materias[0].topicos[0].nome").value("Topico B"))
                .andExpect(jsonPath("$.materias[0].topicos.length()").value(1));
        api.perform(get("/api/v1/priorizacao-de-topicos").session(sessaoB)
                        .param("dataDeReferencia", REFERENCIA.toString())
                        .param("identificadorDaMateria", materia.toString()))
                .andExpect(status().isNotFound());

        assertThat(contar("registros_de_estudo")).isEqualTo(estudosAntes);
        assertThat(contar("evidencias_de_aprendizagem")).isEqualTo(evidenciasAntes);
    }

    @Test
    void deveRespeitarRevisaoBordasCorrecaoEFiltroEntreMaterias()
            throws Exception {
        MockHttpSession sessao = criarContaEEntrar("bordas.priorizacao@example.com");
        UUID usuario = usuario("bordas.priorizacao@example.com");
        UUID materiaA = inserirMateria(usuario, "Direito A");
        UUID revisaoBaixa = inserirTopico(materiaA, "Revisao baixa", 1);
        UUID limiteSetenta = inserirTopico(materiaA, "Limite setenta", 2);
        UUID inicioDaJanela = inserirTopico(materiaA, "Inicio da janela", 3);
        UUID comCorrecao = inserirTopico(materiaA, "Com correcao", 4);
        Contexto contexto = inserirContextoOficial(usuario, materiaA);
        mapearItem(contexto, revisaoBaixa, "Revisao", 1);
        mapearItem(contexto, limiteSetenta, "Setenta por cento", 2);
        mapearItem(contexto, inicioDaJanela, "Borda da janela", 3);
        mapearItem(contexto, comCorrecao, "Fato corrigido", 4);

        UUID estudoDeRevisao = inserirEstudo(
                revisaoBaixa, REFERENCIA, "REVISAO", "ATIVO");
        inserirEvidencia(estudoDeRevisao, null, null, 2, 3);
        UUID estudoNoLimite = inserirEstudo(
                limiteSetenta, REFERENCIA, "QUESTOES", "ATIVO");
        inserirEvidencia(estudoNoLimite, 20, 14, null, null);
        UUID estudoNaBorda = inserirEstudo(
                inicioDaJanela, REFERENCIA.minusDays(29), "QUESTOES", "ATIVO");
        inserirEvidencia(estudoNaBorda, 20, 17, null, null);
        UUID estudoAtivo = inserirEstudo(
                comCorrecao, REFERENCIA.minusDays(1), "QUESTOES", "ATIVO");
        inserirEvidencia(estudoAtivo, 20, 17, null, null);
        UUID estudoCorrigido = inserirEstudo(
                comCorrecao, REFERENCIA, "QUESTOES", "CORRIGIDO");
        inserirEvidencia(estudoCorrigido, 20, 0, null, null);

        UUID materiaB = inserirMateria(usuario, "Direito B");
        UUID topicoB = inserirTopico(materiaB, "Topico da segunda materia", 1);
        UUID grupo = banco.queryForObject("""
                SELECT g.identificador
                FROM grupos_de_conteudo g
                JOIN provas p ON p.identificador = g.prova_id
                WHERE p.cargo_id = ?
                """, UUID.class, contexto.cargo());
        UUID materiaDaProvaB = inserirMateriaDaProva(grupo, materiaB);
        Contexto contextoB = new Contexto(contexto.concurso(), contexto.cargo(),
                contexto.edital(), materiaDaProvaB);
        mapearItem(contextoB, topicoB, "Segunda materia", 1);

        api.perform(get("/api/v1/priorizacao-de-topicos").session(sessao)
                        .param("dataDeReferencia", REFERENCIA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materias.length()").value(2))
                .andExpect(jsonPath("$.materias[0].id").value(materiaA.toString()))
                .andExpect(jsonPath("$.materias[0].topicos[0].nome")
                        .value("Revisao baixa"))
                .andExpect(jsonPath("$.materias[0].topicos[0].faixa")
                        .value("PRECISA_REFORCO"))
                .andExpect(jsonPath("$.materias[0].topicos[1].nome")
                        .value("Limite setenta"))
                .andExpect(jsonPath("$.materias[0].topicos[1].faixa")
                        .value("DESEMPENHO_PARCIAL"))
                .andExpect(jsonPath("$.materias[0].topicos[1].indicadores.percentual")
                        .value(70.00))
                .andExpect(jsonPath("$.materias[0].topicos[2].nome")
                        .value("Inicio da janela"))
                .andExpect(jsonPath("$.materias[0].topicos[2].faixa")
                        .value("CONSOLIDADO"))
                .andExpect(jsonPath("$.materias[0].topicos[2].indicadores.questoesRecentes")
                        .value(20))
                .andExpect(jsonPath("$.materias[0].topicos[3].nome")
                        .value("Com correcao"))
                .andExpect(jsonPath("$.materias[0].topicos[3].faixa")
                        .value("CONSOLIDADO"))
                .andExpect(jsonPath("$.materias[0].topicos[3].indicadores.questoesRecentes")
                        .value(20))
                .andExpect(jsonPath("$.materias[0].topicos[3].indicadores.percentual")
                        .value(85.00))
                .andExpect(jsonPath("$.materias[1].id").value(materiaB.toString()))
                .andExpect(jsonPath("$.materias[1].topicos[0].faixa")
                        .value("SEM_ESTUDO"));

        api.perform(get("/api/v1/priorizacao-de-topicos").session(sessao)
                        .param("dataDeReferencia", REFERENCIA.toString())
                        .param("identificadorDaMateria", materiaB.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.itensOficiais").value(1))
                .andExpect(jsonPath("$.resumo.topicosExigidos").value(1))
                .andExpect(jsonPath("$.materias.length()").value(1))
                .andExpect(jsonPath("$.materias[0].id").value(materiaB.toString()));
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

    private long contar(String tabela) {
        Long quantidade = banco.queryForObject("SELECT COUNT(*) FROM " + tabela, Long.class);
        return quantidade == null ? 0 : quantidade;
    }

    private UUID inserirMateria(UUID usuario, String nome) {
        UUID id = UUID.randomUUID();
        banco.update("""
                INSERT INTO materias (identificador, usuario_id, nome, nome_normalizado,
                  arquivada, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, FALSE, ?, ?, 0)
                """, id, usuario, nome, nome.toLowerCase(), agora(), agora());
        return id;
    }

    private UUID inserirTopico(UUID materia, String nome, int ordem) {
        UUID id = UUID.randomUUID();
        banco.update("""
                INSERT INTO topicos_da_materia (identificador, materia_id, nome,
                  nome_normalizado, ordem, arquivado, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, ?, FALSE, ?, ?, 0)
                """, id, materia, nome, nome.toLowerCase(), ordem, agora(), agora());
        return id;
    }

    private Contexto inserirContextoOficial(UUID usuario, UUID materia) {
        UUID concurso = UUID.randomUUID();
        banco.update("""
                INSERT INTO concursos (identificador, usuario_id, nome, nome_normalizado,
                  situacao, ativo, criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Concurso A', 'concurso a', 'EM_ANDAMENTO', TRUE, ?, ?, 0)
                """, concurso, usuario, agora(), agora());
        UUID cargo = inserirCargo(concurso, "Auditor", true);
        UUID prova = inserirProva(cargo, "Prova principal");
        UUID grupo = inserirGrupo(prova);
        UUID materiaDaProva = inserirMateriaDaProva(grupo, materia);
        UUID edital = UUID.randomUUID();
        banco.update("""
                INSERT INTO editais (identificador, concurso_id, titulo, principal,
                  criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Edital principal', TRUE, ?, ?, 0)
                """, edital, concurso, agora(), agora());
        return new Contexto(concurso, cargo, edital, materiaDaProva);
    }

    private UUID inserirCargo(UUID concurso, String nome, boolean selecionado) {
        UUID id = UUID.randomUUID();
        banco.update("""
                INSERT INTO cargos_do_concurso (identificador, concurso_id, nome,
                  nome_normalizado, nivel_de_escolaridade, selecionado, ordem,
                  criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, 'SUPERIOR', ?, 1, ?, ?, 0)
                """, id, concurso, nome, nome.toLowerCase(), selecionado, agora(), agora());
        return id;
    }

    private UUID inserirProva(UUID cargo, String nome) {
        UUID id = UUID.randomUUID();
        banco.update("""
                INSERT INTO provas (identificador, cargo_id, nome, nome_normalizado,
                  tipo, carater, ordem, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, 'OBJETIVA', 'CLASSIFICATORIO', 1, ?, ?, 0)
                """, id, cargo, nome, nome.toLowerCase(), agora(), agora());
        return id;
    }

    private UUID inserirGrupo(UUID prova) {
        UUID id = UUID.randomUUID();
        banco.update("""
                INSERT INTO grupos_de_conteudo (identificador, prova_id, nome,
                  nome_normalizado, ordem, criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Conhecimentos', 'conhecimentos', 1, ?, ?, 0)
                """, id, prova, agora(), agora());
        return id;
    }

    private UUID inserirMateriaDaProva(UUID grupo, UUID materia) {
        UUID id = UUID.randomUUID();
        banco.update("""
                INSERT INTO materias_da_prova (identificador, grupo_de_conteudo_id,
                  materia_id, ordem, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, 1, ?, ?, 0)
                """, id, grupo, materia, agora(), agora());
        return id;
    }

    private UUID inserirItem(UUID edital, UUID materiaDaProva,
            String descricao, int ordem) {
        UUID id = UUID.randomUUID();
        banco.update("""
                INSERT INTO itens_do_edital (identificador, edital_id,
                  materia_da_prova_id, descricao_original, ordem, criado_em,
                  atualizado_em, versao)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                """, id, edital, materiaDaProva, descricao, ordem, agora(), agora());
        return id;
    }

    private void mapearItem(Contexto contexto, UUID topico,
            String descricao, int ordem) {
        UUID item = inserirItem(
                contexto.edital(), contexto.materiaDaProva(), descricao, ordem);
        banco.update("""
                INSERT INTO mapeamentos_de_itens_do_edital (identificador,
                  item_do_edital_id, topico_da_materia_id, confirmado, criado_em)
                VALUES (?, ?, ?, TRUE, ?)
                """, UUID.randomUUID(), item, topico, agora());
    }

    private UUID inserirEstudo(UUID topico, LocalDate data,
            String tipo, String situacao) {
        UUID id = UUID.randomUUID();
        OffsetDateTime instante = data.atTime(10, 0).atZone(FUSO_HORARIO)
                .toOffsetDateTime();
        banco.update("""
                INSERT INTO registros_de_estudo (identificador, topico_id,
                  data_hora, duracao_em_minutos, situacao, tipo_de_estudo,
                  criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, 60, ?, ?, ?, ?, 0)
                """, id, topico, instante, situacao, tipo, instante, instante);
        return id;
    }

    private UUID inserirEvidencia(UUID estudo, Integer questoes,
            Integer acertos, Integer recordacao, Integer dificuldade) {
        UUID id = UUID.randomUUID();
        banco.update("""
                INSERT INTO evidencias_de_aprendizagem (identificador,
                  registro_de_estudo_id, quantidade_de_questoes,
                  quantidade_de_acertos, nivel_de_recordacao,
                  dificuldade_percebida, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, id, estudo, questoes, acertos, recordacao, dificuldade,
                agora(), agora());
        return id;
    }

    private void inserirPadraoRepetido(UUID usuario, UUID topico,
            UUID evidenciaUm, UUID evidenciaDois) {
        UUID padrao = UUID.randomUUID();
        banco.update("""
                INSERT INTO padroes_de_erro (identificador, usuario_id, topico_id,
                  descricao, descricao_normalizada, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, 'Confusao conceitual', 'confusao conceitual', ?, ?, 0)
                """, padrao, usuario, topico, agora(), agora());
        inserirOcorrencia(padrao, evidenciaUm);
        inserirOcorrencia(padrao, evidenciaDois);
    }

    private void inserirOcorrencia(UUID padrao, UUID evidencia) {
        banco.update("""
                INSERT INTO ocorrencias_de_padrao_de_erro (identificador,
                  evidencia_id, padrao_de_erro_id, quantidade_de_ocorrencias, criado_em)
                VALUES (?, ?, ?, 1, ?)
                """, UUID.randomUUID(), evidencia, padrao, agora());
    }

    private void inserirMaterialComCobertura(UUID usuario, UUID topico) {
        UUID material = UUID.randomUUID();
        banco.update("""
                INSERT INTO materiais_de_estudo (identificador, usuario_id, titulo,
                  tipo, arquivado, criado_em, atualizado_em, versao)
                VALUES (?, ?, 'PDF', 'PDF', FALSE, ?, ?, 0)
                """, material, usuario, agora(), agora());
        banco.update("""
                INSERT INTO coberturas_de_topicos_por_material (identificador,
                  material_id, topico_id, criado_em) VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), material, topico, agora());
    }

    private void inserirConteudoForaDoContexto(
            UUID materia, UUID topico, UUID concurso) {
        UUID cargo = inserirCargo(concurso, "Outro cargo", false);
        UUID prova = inserirProva(cargo, "Outra prova");
        UUID grupo = inserirGrupo(prova);
        UUID materiaDaProva = inserirMateriaDaProva(grupo, materia);
        UUID edital = UUID.randomUUID();
        banco.update("""
                INSERT INTO editais (identificador, concurso_id, titulo, principal,
                  criado_em, atualizado_em, versao)
                VALUES (?, ?, 'Edital secundario', FALSE, ?, ?, 0)
                """, edital, concurso, agora(), agora());
        UUID item = inserirItem(edital, materiaDaProva, "Fora do contexto", 1);
        banco.update("""
                INSERT INTO mapeamentos_de_itens_do_edital (identificador,
                  item_do_edital_id, topico_da_materia_id, confirmado, criado_em)
                VALUES (?, ?, ?, TRUE, ?)
                """, UUID.randomUUID(), item, topico, agora());
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(FUSO_HORARIO);
    }

    private record Contexto(
            UUID concurso, UUID cargo, UUID edital, UUID materiaDaProva) {
    }
}
