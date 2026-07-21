package br.com.trilhaaprovacao.planejamento.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
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
    void devePercorrerGeracaoHojeExecucaoEHistoricoDeEstudos() throws Exception {
        String email = "fluxo.geracao@example.com";
        MockHttpSession sessao = criarContaEEntrar(email);
        String materiaA = criarMateria(sessao, "Banco de dados");
        String topicoA = criarTopico(sessao, materiaA, "Modelagem relacional");
        String materiaB = criarMateria(sessao, "Redes");
        String materiaC = criarMateria(sessao, "Seguranca");
        String plano = criarPlanoAtivo(sessao);
        criarEstruturaElegivel(email, List.of(materiaA, materiaB, materiaC), materiaA);
        String manual = adicionarBloco(
                sessao, plano, "Leitura manual", null, null, 1);
        api.perform(put("/api/v1/planos-semanais/{id}/prioridades-de-materias", plano)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prioridades":[
                                  {"identificadorDaMateria":"%s","prioridade":"ALTA"},
                                  {"identificadorDaMateria":"%s","prioridade":"NORMAL"},
                                  {"identificadorDaMateria":"%s","prioridade":"BAIXA"}
                                ]}
                                """.formatted(materiaA, materiaB, materiaC)))
                .andExpect(status().isOk());

        api.perform(post("/api/v1/planos-semanais/{id}/geracao-deterministica/previa", plano)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(configuracaoDaPrevia()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias[0].blocosPreservados.length()").value(1))
                .andExpect(jsonPath("$.dias[0].blocosSugeridos.length()").value(4))
                .andExpect(jsonPath("$.dias[0].capacidade.minutosDisponiveis").value(180))
                .andExpect(jsonPath("$.dias[0].capacidade.minutosPreservados").value(60))
                .andExpect(jsonPath("$.dias[0].capacidade.minutosSugeridos").value(120))
                .andExpect(jsonPath("$.dias[0].capacidade.minutosLivres").value(0));
        api.perform(post("/api/v1/planos-semanais/{id}/geracao-deterministica", plano)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(configuracaoDaAplicacao(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.quantidadeDeBlocosCriados").value(4))
                .andExpect(jsonPath("$.resumo.quantidadeDeBlocosPreservados").value(1));

        String ajustado = banco.queryForObject("""
                SELECT identificador::text
                FROM blocos_de_estudo
                WHERE plano_id = ?::uuid
                  AND materia_id = ?::uuid
                  AND origem = 'GERADO_DETERMINISTICAMENTE'
                ORDER BY data, ordem
                LIMIT 1
                """, String.class, plano, materiaA);
        Integer ordemDoAjustado = banco.queryForObject("""
                SELECT ordem FROM blocos_de_estudo WHERE identificador = ?::uuid
                """, Integer.class, ajustado);
        String justificativaOriginal = banco.queryForObject("""
                SELECT justificativa_da_geracao
                FROM blocos_de_estudo
                WHERE identificador = ?::uuid
                """, String.class, ajustado);
        api.perform(put("/api/v1/blocos-de-estudo/{id}", ajustado)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identificadorDaMateria":"%s",
                                 "identificadorDoTopico":"%s",
                                 "titulo":"Questoes de modelagem",
                                 "tipoDeAtividade":"QUESTOES",
                                 "data":"%s","duracaoPrevistaEmMinutos":45,
                                 "ordem":%d,"observacao":"Ajustado antes de regenerar"}
                                """.formatted(materiaA, topicoA, SEGUNDA, ordemDoAjustado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origem").value("GERADO_AJUSTADO_MANUALMENTE"))
                .andExpect(jsonPath("$.tipoDeAtividade").value("QUESTOES"))
                .andExpect(jsonPath("$.duracaoPrevistaEmMinutos").value(45))
                .andExpect(jsonPath("$.identificadorDoTopico").value(topicoA))
                .andExpect(jsonPath("$.justificativaDaGeracao")
                        .value(justificativaOriginal));
        List<String> geradosPurosAnteriores = banco.queryForList("""
                SELECT identificador::text
                FROM blocos_de_estudo
                WHERE plano_id = ?::uuid
                  AND origem = 'GERADO_DETERMINISTICAMENTE'
                ORDER BY identificador
                """, String.class, plano);

        api.perform(post("/api/v1/planos-semanais/{id}/geracao-deterministica", plano)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(configuracaoDaAplicacao(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.quantidadeDeBlocosSubstituidos")
                        .value(geradosPurosAnteriores.size()))
                .andExpect(jsonPath("$.resumo.quantidadeDeBlocosPreservados").value(2));
        org.assertj.core.api.Assertions.assertThat(banco.queryForObject("""
                SELECT count(*)
                FROM blocos_de_estudo
                WHERE identificador IN (?::uuid, ?::uuid)
                """, Integer.class, manual, ajustado)).isEqualTo(2);
        for (String geradoAnterior : geradosPurosAnteriores) {
            org.assertj.core.api.Assertions.assertThat(banco.queryForObject("""
                    SELECT count(*) FROM blocos_de_estudo
                    WHERE identificador = ?::uuid
                    """, Integer.class, geradoAnterior)).isZero();
        }

        ativarPlano(sessao, plano);
        api.perform(post("/api/v1/planos-semanais/{id}/geracao-deterministica", plano)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(configuracaoDaAplicacao(true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo")
                        .value("PLANO_SEMANAL_NAO_ESTA_EM_RASCUNHO"));

        api.perform(get("/api/v1/planejamento/hoje")
                        .param("data", SEGUNDA.toString()).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DIA_PLANEJADO"))
                .andExpect(jsonPath("$.identificadorDoPlano").value(plano))
                .andExpect(jsonPath("$.quantidadeDeBlocos").value(5))
                .andExpect(jsonPath("$.minutosPlanejados").value(180));

        iniciar(sessao, ajustado);
        String conclusao = concluir(sessao, ajustado, 45, topicoA);
        org.assertj.core.api.Assertions.assertThat(json.readTree(conclusao)
                .get("evidencia").get("quantidadeDeErros").asInt()).isEqualTo(2);
        String registro = json.readTree(conclusao).get("estudo")
                .get("identificador").asString();
        String repeticao = concluir(sessao, ajustado, 45, topicoA);
        org.assertj.core.api.Assertions.assertThat(json.readTree(repeticao).get("estudo")
                .get("identificador").asString()).isEqualTo(registro);
        assertEquals(1, quantidadeDeRegistros());
        assertEquals(1, banco.queryForObject(
                "SELECT COUNT(*) FROM evidencias_de_aprendizagem", Integer.class));
        api.perform(post("/api/v1/blocos-de-estudo/{id}/conclusao", ajustado)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"duracaoExecutadaEmMinutos":45,
                                 "observacao":"Execução concluída",
                                 "identificadorDoTopico":"%s",
                                 "evidencia":{"resultadoDeQuestoes":{
                                 "quantidadeDeQuestoes":10,"quantidadeDeAcertos":7}}}
                                """.formatted(topicoA)))
                .andExpect(status().isConflict());
        api.perform(post("/api/v1/blocos-de-estudo/{id}/conclusao", ajustado)
                        .session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"duracaoExecutadaEmMinutos":45,
                                 "observacao":"Execução concluída",
                                 "identificadorDoTopico":"%s",
                                 "evidencia":{"resultadoDeQuestoes":{
                                   "quantidadeDeQuestoes":10,"quantidadeDeAcertos":8},
                                   "padroesDeErro":[
                                     {"descricao":"Erro de sinal",
                                      "quantidadeDeOcorrencias":1},
                                     {"descricao":" erro  de sinal ",
                                      "quantidadeDeOcorrencias":1}]}}
                                """.formatted(topicoA)))
                .andExpect(status().isConflict());

        api.perform(get("/api/v1/estudos").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeItens").value(1))
                .andExpect(jsonPath("$.itens[0].identificador").value(registro))
                .andExpect(jsonPath("$.itens[0].identificadorDoTopico").value(topicoA))
                .andExpect(jsonPath("$.itens[0].duracaoEmMinutos").value(45));
        api.perform(get("/api/v1/planejamento/hoje")
                        .param("data", SEGUNDA.toString()).session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizados[0].identificador").value(ajustado));

        MockHttpSession sessaoB = criarContaEEntrar("fluxo.geracao.b@example.com");
        api.perform(get("/api/v1/blocos-de-estudo/{id}/execucao", ajustado)
                        .session(sessaoB))
                .andExpect(status().isNotFound());
        api.perform(get("/api/v1/estudos/{id}", registro).session(sessaoB))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveCriarUmUnicoEstudoAoConcluirBlocoComTopico() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("integracao@example.com");
        String materia = criarMateria(sessao, "Auditoria");
        String topico = criarTopico(sessao, materia, "Risco de auditoria");
        String outroTopico = criarTopico(sessao, materia, "Evidência de auditoria");
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
                                  "observacao":"Execução concluída",
                                  "evidencia":{"resultadoDeQuestoes":{
                                    "quantidadeDeQuestoes":10,
                                    "quantidadeDeAcertos":8
                                  }}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execucao.identificadorDoRegistroDeEstudo")
                        .value(registro))
                .andExpect(jsonPath("$.estudo.identificadorDoTopico").value(topico));

        api.perform(post("/api/v1/blocos-de-estudo/{id}/conclusao", bloco)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"duracaoExecutadaEmMinutos":45,
                                 "observacao":"Execução concluída",
                                 "identificadorDoTopico":"%s",
                                 "evidencia":{"resultadoDeQuestoes":{
                                   "quantidadeDeQuestoes":10,
                                   "quantidadeDeAcertos":8}}}
                                """.formatted(outroTopico)))
                .andExpect(status().isConflict());

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
        String resposta = concluirSemEvidencia(sessaoA, bloco, 30, null);
        String execucao = json.readTree(resposta).get("execucao")
                .get("identificador").asString();

        assertEquals(0, quantidadeDeRegistros());

        api.perform(post("/api/v1/blocos-de-estudo/{id}/conclusao", bloco)
                        .session(sessaoA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"duracaoExecutadaEmMinutos":30,
                                 "observacao":"Execução concluída",
                                 "identificadorDoTopico":"%s"}
                                """.formatted(topico)))
                .andExpect(status().isConflict());

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
    void deveCriarEstudoEEvidenciaAoCorrigirInterrupcaoSemTopico() throws Exception {
        MockHttpSession sessao = criarContaEEntrar("correcao.sem.topico@example.com");
        String materia = criarMateria(sessao, "Contabilidade");
        String topico = criarTopico(sessao, materia, "Balanço patrimonial");
        String plano = criarPlanoAtivo(sessao);
        String bloco = adicionarBloco(
                sessao, plano, "Resolver balanços", materia, null, 1, "QUESTOES");
        ativarPlano(sessao, plano);
        iniciar(sessao, bloco);
        api.perform(post("/api/v1/blocos-de-estudo/{id}/interrupcao", bloco)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"duracaoExecutadaEmMinutos":10,
                                 "observacao":"Resultado incompleto",
                                 "evidencia":{"resultadoDeQuestoes":{
                                   "quantidadeDeQuestoes":12}}}
                                """))
                .andExpect(status().isBadRequest());
        String interrompida = api.perform(
                        post("/api/v1/blocos-de-estudo/{id}/interrupcao", bloco)
                                .session(sessao).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"duracaoExecutadaEmMinutos":10,
                                         "observacao":"Interrompida"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estudo").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String execucao = json.readTree(interrompida).get("execucao")
                .get("identificador").asString();

        api.perform(put("/api/v1/execucoes-de-bloco/{id}/correcao", execucao)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resultado":"CONCLUIDO",
                                 "duracaoExecutadaEmMinutos":25,
                                 "observacao":"Correção sem tópico",
                                 "evidencia":{"resultadoDeQuestoes":{
                                   "quantidadeDeQuestoes":12,
                                   "quantidadeDeAcertos":9}}}
                                """))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(0, quantidadeDeRegistros());

        api.perform(put("/api/v1/execucoes-de-bloco/{id}/correcao", execucao)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resultado":"CONCLUIDO",
                                 "duracaoExecutadaEmMinutos":25,
                                 "observacao":"Correção concluída",
                                 "identificadorDoTopico":"%s",
                                 "evidencia":{"resultadoDeQuestoes":{
                                   "quantidadeDeQuestoes":12,
                                   "quantidadeDeAcertos":9}}}
                                """.formatted(topico)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloco.estado").value("CONCLUIDO"))
                .andExpect(jsonPath("$.estudo.identificadorDoTopico").value(topico))
                .andExpect(jsonPath("$.evidencia.quantidadeDeErros").value(3));

        assertEquals(1, quantidadeDeRegistros());
        assertEquals(1, banco.queryForObject(
                "SELECT count(*) FROM evidencias_de_aprendizagem", Integer.class));
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
                                + campoDoTopico
                                + ",\"evidencia\":{\"resultadoDeQuestoes\":{"
                                + "\"quantidadeDeQuestoes\":10,"
                                + "\"quantidadeDeAcertos\":8}}}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String concluirSemEvidencia(MockHttpSession sessao, String bloco, int duracao,
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
        return adicionarBloco(sessao, plano, titulo, materia, topico, ordem, "TEORIA");
    }

    private String adicionarBloco(MockHttpSession sessao, String plano, String titulo,
            String materia, String topico, int ordem, String tipo) throws Exception {
        String referencias = materia == null ? "" :
                ",\"identificadorDaMateria\":\"" + materia + "\""
                        + (topico == null ? "" :
                        ",\"identificadorDoTopico\":\"" + topico + "\"");
        String corpo = api.perform(post("/api/v1/planos-semanais/{id}/blocos", plano)
                        .session(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"" + titulo
                                + "\",\"tipoDeAtividade\":\"" + tipo + "\""
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

    private String configuracaoDaPrevia() {
        return """
                {"duracaoPadraoDoBlocoPrincipalEmMinutos":50,
                 "duracaoDoBlocoDeRevisaoEmMinutos":20}
                """;
    }

    private String configuracaoDaAplicacao(boolean substituir) {
        return """
                {"duracaoPadraoDoBlocoPrincipalEmMinutos":50,
                 "duracaoDoBlocoDeRevisaoEmMinutos":20,
                 "substituirBlocosGerados":%s}
                """.formatted(substituir);
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
