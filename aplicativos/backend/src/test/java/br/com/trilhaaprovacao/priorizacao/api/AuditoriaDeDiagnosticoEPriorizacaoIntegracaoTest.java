package br.com.trilhaaprovacao.priorizacao.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.trilhaaprovacao.evidencias.aplicacao.ConsultaDeDiagnosticoDeTopicos;
import br.com.trilhaaprovacao.evidencias.aplicacao.DiagnosticoDeTopico;
import br.com.trilhaaprovacao.priorizacao.aplicacao.ConsultaDePriorizacaoDeTopicos;
import br.com.trilhaaprovacao.priorizacao.aplicacao.ResultadoDaPriorizacaoDeTopicos;
import br.com.trilhaaprovacao.priorizacao.dominio.FaixaDePriorizacao;
import br.com.trilhaaprovacao.priorizacao.dominio.JustificativaDaPriorizacao;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "debug=false")
@Testcontainers
class AuditoriaDeDiagnosticoEPriorizacaoIntegracaoTest {
    private static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate REFERENCIA = LocalDate.of(2026, 7, 21);
    private static final LocalDate INICIO_DA_JANELA = REFERENCIA.minusDays(29);

    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_auditoria_priorizacao")
                    .withUsername("teste")
                    .withPassword("teste");

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        propriedades.add("spring.datasource.username", POSTGRESQL::getUsername);
        propriedades.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired JdbcTemplate banco;
    @Autowired ConsultaDeDiagnosticoDeTopicos diagnostico;
    @Autowired ConsultaDePriorizacaoDeTopicos priorizacao;

    @BeforeEach
    void limparBanco() {
        banco.execute("""
                TRUNCATE TABLE ocorrencias_de_padrao_de_erro, padroes_de_erro,
                    evidencias_de_aprendizagem, execucoes_de_bloco,
                    blocos_de_estudo, disponibilidades_do_dia,
                    prioridades_de_materias_no_plano, planos_semanais,
                    registros_de_estudo, coberturas_de_topicos_por_material,
                    materiais_de_estudo, mapeamentos_de_itens_do_edital,
                    itens_do_edital, materias_da_prova, grupos_de_conteudo,
                    provas, cargos_do_concurso, editais, concursos,
                    topicos_da_materia, materias, usuarios CASCADE
                """);
    }

    @Test
    void deveCompararElegibilidadeMateriaisArquivamentoEIsolamentoNosMesmosFatos() {
        UUID usuarioA = inserirUsuario("auditoria.priorizacao.a@example.com");
        UUID usuarioB = inserirUsuario("auditoria.priorizacao.b@example.com");
        ContextoOficial contextoA = inserirContextoOficial(usuarioA, "A");
        MateriaOficial materiaA = inserirMateriaOficial(
                usuarioA, contextoA, "Direito Administrativo", 1);

        UUID semEstudo = inserirTopico(materiaA.materia(), "Sem estudo", 1);
        UUID semEvidencia = inserirTopico(materiaA.materia(), "Sem evidencia", 2);
        UUID comMaterialAtivo = inserirTopico(
                materiaA.materia(), "Com material ativo", 3);
        UUID comMaterialArquivado = inserirTopico(
                materiaA.materia(), "Com material arquivado", 4);
        UUID semMaterial = inserirTopico(materiaA.materia(), "Sem material", 5);
        UUID mapeamentoNaoConfirmado = inserirTopico(
                materiaA.materia(), "Mapeamento nao confirmado", 6);
        UUID topicoArquivado = inserirTopico(materiaA.materia(), "Topico arquivado", 7);

        mapear(contextoA, materiaA, semEstudo, "Item sem estudo", 1, true);
        mapear(contextoA, materiaA, semEvidencia, "Item sem evidencia", 2, true);
        mapear(contextoA, materiaA, comMaterialAtivo, "Item material ativo", 3, true);
        mapear(contextoA, materiaA, comMaterialArquivado,
                "Item material arquivado", 4, true);
        mapear(contextoA, materiaA, semMaterial, "Item sem material", 5, true);
        UUID itemNaoConfirmado = mapear(contextoA, materiaA, mapeamentoNaoConfirmado,
                "Item nao confirmado", 6, false);
        UUID itemDoTopicoArquivado = mapear(contextoA, materiaA, topicoArquivado,
                "Item do topico arquivado", 7, true);

        inserirEstudo(semEvidencia, local(REFERENCIA.minusDays(1), 10),
                "TEORIA", "ATIVO");
        inserirQuestoes(comMaterialAtivo, local(REFERENCIA, 10),
                20, 17, null, "ATIVO");
        inserirQuestoes(comMaterialArquivado, local(REFERENCIA, 11),
                20, 17, null, "ATIVO");
        inserirQuestoes(semMaterial, local(REFERENCIA, 12),
                20, 17, null, "ATIVO");
        inserirMaterial(usuarioA, comMaterialAtivo, false);
        inserirMaterial(usuarioA, comMaterialArquivado, true);
        banco.update("UPDATE topicos_da_materia SET arquivado = TRUE WHERE identificador = ?",
                topicoArquivado);

        MateriaOficial materiaArquivada = inserirMateriaOficial(
                usuarioA, contextoA, "Materia arquivada", 2);
        UUID topicoDaMateriaArquivada = inserirTopico(
                materiaArquivada.materia(), "Topico da materia arquivada", 1);
        mapear(contextoA, materiaArquivada, topicoDaMateriaArquivada,
                "Item da materia arquivada", 1, true);
        banco.update("UPDATE materias SET arquivada = TRUE WHERE identificador = ?",
                materiaArquivada.materia());

        ContextoOficial contextoB = inserirContextoOficial(usuarioB, "B");
        MateriaOficial materiaB = inserirMateriaOficial(
                usuarioB, contextoB, "Direito Administrativo", 1);
        UUID topicoB = inserirTopico(materiaB.materia(), "Sem estudo", 1);
        mapear(contextoB, materiaB, topicoB, "Item B", 1, true);
        inserirQuestoes(topicoB, local(REFERENCIA, 10),
                100, 0, null, "ATIVO");

        Map<UUID, DiagnosticoDeTopico> diagnosticosA = diagnosticos(usuarioA, false);
        assertThat(diagnosticosA.keySet()).containsExactlyInAnyOrder(
                semEstudo, semEvidencia, comMaterialAtivo, comMaterialArquivado,
                semMaterial, mapeamentoNaoConfirmado);
        assertThat(diagnosticosA).doesNotContainKeys(
                topicoArquivado, topicoDaMateriaArquivada, topicoB);
        assertThat(diagnosticosA.get(semEvidencia).quantidadeDeEvidencias()).isZero();
        assertThat(diagnosticosA.get(mapeamentoNaoConfirmado).exigidoNoConcursoAtivo())
                .isFalse();

        Map<UUID, DiagnosticoDeTopico> diagnosticosOficiaisA =
                diagnosticos(usuarioA, true);
        assertThat(diagnosticosOficiaisA.keySet()).containsExactlyInAnyOrder(
                semEstudo, semEvidencia, comMaterialAtivo,
                comMaterialArquivado, semMaterial);

        ResultadoDaPriorizacaoDeTopicos resultadoA =
                priorizacao.consultar(usuarioA, REFERENCIA, null);
        Map<UUID, ResultadoDaPriorizacaoDeTopicos.TopicoPriorizado> prioridadesA =
                prioridades(resultadoA);
        assertThat(prioridadesA.keySet()).containsExactlyInAnyOrder(
                semEstudo, semEvidencia, comMaterialAtivo,
                comMaterialArquivado, semMaterial);
        assertThat(prioridadesA).doesNotContainKeys(
                mapeamentoNaoConfirmado, topicoArquivado,
                topicoDaMateriaArquivada, topicoB);
        assertThat(prioridadesA.get(semEstudo).faixa())
                .isEqualTo(FaixaDePriorizacao.SEM_ESTUDO);
        assertThat(prioridadesA.get(semEvidencia).faixa())
                .isEqualTo(FaixaDePriorizacao.SEM_EVIDENCIA);
        assertThat(prioridadesA.get(semEvidencia).indicadores().estudos()).isEqualTo(1);
        assertThat(prioridadesA.get(semEvidencia).indicadores().evidencias()).isZero();
        assertThat(prioridadesA.get(comMaterialAtivo).possuiMaterial()).isTrue();
        assertThat(prioridadesA.get(comMaterialArquivado).possuiMaterial()).isFalse();
        assertThat(prioridadesA.get(semMaterial).possuiMaterial()).isFalse();
        assertThat(prioridadesA.get(comMaterialArquivado).justificativas())
                .contains(JustificativaDaPriorizacao.SEM_MATERIAL_ATIVO.mensagem());
        assertThat(prioridadesA.get(comMaterialAtivo).justificativas())
                .doesNotContain(JustificativaDaPriorizacao.SEM_MATERIAL_ATIVO.mensagem());
        assertThat(resultadoA.itensSemMapeamento())
                .extracting(ResultadoDaPriorizacaoDeTopicos.ItemSemMapeamento::identificador)
                .containsExactlyInAnyOrder(itemNaoConfirmado, itemDoTopicoArquivado);

        assertThat(diagnosticos(usuarioB, false).keySet()).containsExactly(topicoB);
        assertThat(prioridades(priorizacao.consultar(usuarioB, REFERENCIA, null)).keySet())
                .containsExactly(topicoB);
    }

    @Test
    void deveAplicarMesmoInvarianteDeMateriaAoMapeamentoConfirmado() {
        UUID usuario = inserirUsuario("auditoria.mapeamento@example.com");
        ContextoOficial contexto = inserirContextoOficial(usuario, "invariante");
        MateriaOficial materiaOficial = inserirMateriaOficial(
                usuario, contexto, "Materia oficial", 1);
        UUID materiaPessoalDiferente = inserirMateria(usuario, "Materia diferente");
        UUID topicoDeOutraMateria = inserirTopico(
                materiaPessoalDiferente, "Topico de outra materia", 1);

        UUID itemInvalido = mapear(contexto, materiaOficial, topicoDeOutraMateria,
                "Mapeamento confirmado inconsistente", 1, true);

        assertThat(diagnosticos(usuario, false).get(topicoDeOutraMateria)
                .exigidoNoConcursoAtivo()).isFalse();
        assertThat(diagnosticos(usuario, true)).doesNotContainKey(topicoDeOutraMateria);

        ResultadoDaPriorizacaoDeTopicos resultado =
                priorizacao.consultar(usuario, REFERENCIA, null);
        assertThat(prioridades(resultado)).doesNotContainKey(topicoDeOutraMateria);
        assertThat(resultado.itensSemMapeamento())
                .extracting(ResultadoDaPriorizacaoDeTopicos.ItemSemMapeamento::identificador)
                .containsExactly(itemInvalido);
    }

    @Test
    void deveCompararJanelaCivilFusoHorarioCorrecoesECancelamentos() {
        UUID usuario = inserirUsuario("auditoria.janela@example.com");
        ContextoOficial contexto = inserirContextoOficial(usuario, "janela");
        MateriaOficial materia = inserirMateriaOficial(
                usuario, contexto, "Estatistica", 1);
        UUID topico = inserirTopico(materia.materia(), "Probabilidade", 1);
        mapear(contexto, materia, topico, "Probabilidade", 1, true);

        OffsetDateTime foraPorUmSegundoEmUtc =
                OffsetDateTime.parse("2026-06-22T02:59:59Z");
        OffsetDateTime inicioInclusivo =
                OffsetDateTime.parse("2026-06-22T00:00:00-03:00");
        OffsetDateTime fimInclusivo =
                OffsetDateTime.parse("2026-07-21T23:59:59-03:00");
        OffsetDateTime futuroLocal =
                OffsetDateTime.parse("2026-07-22T00:00:00-03:00");

        inserirQuestoes(topico, foraPorUmSegundoEmUtc,
                11, 0, null, "ATIVO");
        inserirQuestoes(topico, inicioInclusivo,
                9, 6, null, "ATIVO");
        inserirQuestoes(topico, fimInclusivo,
                10, 7, null, "ATIVO");
        inserirQuestoes(topico, futuroLocal,
                100, 0, null, "ATIVO");
        inserirQuestoes(topico, local(REFERENCIA.minusDays(2), 10),
                100, 0, null, "CORRIGIDO");
        inserirQuestoes(topico, local(REFERENCIA.minusDays(1), 10),
                100, 0, null, "CANCELADO");

        DiagnosticoDeTopico itemDoDiagnostico = diagnosticos(usuario, false).get(topico);
        assertThat(itemDoDiagnostico.quantidadeDeEvidencias()).isEqualTo(3);
        assertThat(itemDoDiagnostico.totaisHistoricos())
                .isEqualTo(new DiagnosticoDeTopico.TotaisDeQuestoes(30, 13, 17));
        assertThat(itemDoDiagnostico.totaisDosUltimosTrintaDias())
                .isEqualTo(new DiagnosticoDeTopico.TotaisDeQuestoes(19, 13, 6));
        assertThat(itemDoDiagnostico.percentualRecenteDeAcertos())
                .isEqualByComparingTo("68.42");
        assertThat(itemDoDiagnostico.ultimaEvidenciaEm().toInstant())
                .isEqualTo(fimInclusivo.toInstant());

        ResultadoDaPriorizacaoDeTopicos.TopicoPriorizado itemDaPriorizacao =
                prioridades(priorizacao.consultar(usuario, REFERENCIA, null)).get(topico);
        assertThat(itemDaPriorizacao.faixa())
                .isEqualTo(FaixaDePriorizacao.DADOS_INSUFICIENTES);
        assertThat(itemDaPriorizacao.indicadores().estudos()).isEqualTo(3);
        assertThat(itemDaPriorizacao.indicadores().evidencias()).isEqualTo(3);
        assertThat(itemDaPriorizacao.indicadores().questoesRecentes()).isEqualTo(19);
        assertThat(itemDaPriorizacao.indicadores().acertosRecentes()).isEqualTo(13);
        assertThat(itemDaPriorizacao.indicadores().errosRecentes()).isEqualTo(6);
        assertThat(itemDaPriorizacao.indicadores().percentual())
                .isEqualByComparingTo("68.42");
        assertThat(itemDaPriorizacao.indicadores().ultimaEvidencia().toInstant())
                .isEqualTo(fimInclusivo.toInstant());
    }

    @Test
    void deveCaracterizarLimitesRecordacoesDificuldadeEExplicacoes() {
        UUID usuario = inserirUsuario("auditoria.limites@example.com");
        ContextoOficial contexto = inserirContextoOficial(usuario, "limites");
        MateriaOficial materia = inserirMateriaOficial(
                usuario, contexto, "Direito Constitucional", 1);
        int ordem = 0;
        Map<UUID, LimiteEsperado> limites = new LinkedHashMap<>();
        limites.put(topicoComQuestoes(contexto, materia, "Dezenove questoes", ++ordem,
                        19, 19, null),
                new LimiteEsperado("100.00", FaixaDePriorizacao.DADOS_INSUFICIENTES,
                        JustificativaDaPriorizacao.QUESTOES_RECENTES_INSUFICIENTES));
        limites.put(topicoComQuestoes(contexto, materia, "Vinte questoes", ++ordem,
                        20, 13, null),
                new LimiteEsperado("65.00", FaixaDePriorizacao.PRECISA_REFORCO,
                        JustificativaDaPriorizacao.PERCENTUAL_RECENTE_ABAIXO_DE_SETENTA));
        limites.put(topicoComQuestoes(contexto, materia, "Sessenta e nove virgula noventa e nove",
                        ++ordem, 10_000, 6_999, null),
                new LimiteEsperado("69.99", FaixaDePriorizacao.PRECISA_REFORCO,
                        JustificativaDaPriorizacao.PERCENTUAL_RECENTE_ABAIXO_DE_SETENTA));
        limites.put(topicoComQuestoes(contexto, materia, "Setenta", ++ordem,
                        100, 70, null),
                new LimiteEsperado("70.00", FaixaDePriorizacao.DESEMPENHO_PARCIAL,
                        JustificativaDaPriorizacao
                                .PERCENTUAL_RECENTE_ENTRE_SETENTA_E_OITENTA_E_CINCO));
        limites.put(topicoComQuestoes(contexto, materia, "Oitenta e quatro virgula noventa e nove",
                        ++ordem, 10_000, 8_499, null),
                new LimiteEsperado("84.99", FaixaDePriorizacao.DESEMPENHO_PARCIAL,
                        JustificativaDaPriorizacao
                                .PERCENTUAL_RECENTE_ENTRE_SETENTA_E_OITENTA_E_CINCO));
        limites.put(topicoComQuestoes(contexto, materia, "Oitenta e cinco", ++ordem,
                        100, 85, null),
                new LimiteEsperado("85.00", FaixaDePriorizacao.CONSOLIDADO,
                        JustificativaDaPriorizacao
                                .PERCENTUAL_RECENTE_A_PARTIR_DE_OITENTA_E_CINCO));

        Map<Integer, UUID> topicosPorRecordacao = new LinkedHashMap<>();
        for (int recordacao = 1; recordacao <= 5; recordacao++) {
            UUID topico = inserirTopico(materia.materia(),
                    "Recordacao " + recordacao, ++ordem);
            mapear(contexto, materia, topico,
                    "Item recordacao " + recordacao, ordem, true);
            UUID estudo = inserirEstudo(topico, local(REFERENCIA, 14),
                    "REVISAO", "ATIVO");
            inserirEvidencia(estudo, null, null, recordacao, null);
            topicosPorRecordacao.put(recordacao, topico);
        }

        UUID dificuldadeQuatro = topicoComQuestoes(contexto, materia,
                "Dificuldade quatro", ++ordem, 20, 18, 4);
        UUID dificuldadeCinco = topicoComQuestoes(contexto, materia,
                "Dificuldade cinco", ++ordem, 20, 18, 5);

        Map<UUID, DiagnosticoDeTopico> diagnosticos = diagnosticos(usuario, true);
        Map<UUID, ResultadoDaPriorizacaoDeTopicos.TopicoPriorizado> prioridades =
                prioridades(priorizacao.consultar(usuario, REFERENCIA, null));
        limites.forEach((topico, esperado) -> {
            assertThat(diagnosticos.get(topico).percentualRecenteDeAcertos())
                    .isEqualByComparingTo(esperado.percentual());
            var prioridade = prioridades.get(topico);
            assertThat(prioridade.indicadores().percentual())
                    .isEqualByComparingTo(esperado.percentual());
            assertThat(prioridade.faixa()).isEqualTo(esperado.faixa());
            assertThat(prioridade.justificativas())
                    .contains(esperado.justificativa().mensagem());
        });

        Map<Integer, FaixaDePriorizacao> faixasPorRecordacao = Map.of(
                1, FaixaDePriorizacao.PRECISA_REFORCO,
                2, FaixaDePriorizacao.PRECISA_REFORCO,
                3, FaixaDePriorizacao.DESEMPENHO_PARCIAL,
                4, FaixaDePriorizacao.CONSOLIDADO,
                5, FaixaDePriorizacao.CONSOLIDADO);
        topicosPorRecordacao.forEach((recordacao, topico) -> {
            assertThat(diagnosticos.get(topico).ultimaRecordacao()).isEqualTo(recordacao);
            var prioridade = prioridades.get(topico);
            assertThat(prioridade.indicadores().ultimaRecordacao()).isEqualTo(recordacao);
            assertThat(prioridade.indicadores().questoesRecentes()).isZero();
            assertThat(prioridade.faixa()).isEqualTo(faixasPorRecordacao.get(recordacao));
            assertThat(prioridade.justificativas()).contains(
                    JustificativaDaPriorizacao.QUESTOES_RECENTES_INSUFICIENTES.mensagem());
        });
        assertThat(prioridades.get(topicosPorRecordacao.get(1)).justificativas())
                .contains(JustificativaDaPriorizacao.RECORDACAO_RECENTE_BAIXA.mensagem());
        assertThat(prioridades.get(topicosPorRecordacao.get(3)).justificativas())
                .contains(JustificativaDaPriorizacao.RECORDACAO_RECENTE_PARCIAL.mensagem());
        assertThat(prioridades.get(topicosPorRecordacao.get(5)).justificativas())
                .contains(JustificativaDaPriorizacao.RECORDACAO_RECENTE_ALTA.mensagem());

        assertThat(prioridades.get(dificuldadeQuatro).faixa())
                .isEqualTo(FaixaDePriorizacao.CONSOLIDADO);
        assertThat(prioridades.get(dificuldadeCinco).faixa())
                .isEqualTo(FaixaDePriorizacao.CONSOLIDADO);
        assertThat(prioridades.get(dificuldadeQuatro).justificativas())
                .contains(JustificativaDaPriorizacao.DIFICULDADE_PERCEBIDA_ALTA.mensagem());
        assertThat(prioridades.get(dificuldadeCinco).posicaoNoGrupo())
                .isLessThan(prioridades.get(dificuldadeQuatro).posicaoNoGrupo());
    }

    @Test
    void deveCaracterizarPadraoHistoricamenteRepetidoComOcorrenciaRecente() {
        UUID usuario = inserirUsuario("auditoria.padroes@example.com");
        ContextoOficial contexto = inserirContextoOficial(usuario, "padroes");
        MateriaOficial materia = inserirMateriaOficial(
                usuario, contexto, "Matematica", 1);
        UUID repetido = inserirTopico(materia.materia(), "Padrao repetido", 1);
        UUID apenasUmaOcorrenciaAtiva = inserirTopico(
                materia.materia(), "Padrao sem repeticao ativa", 2);
        mapear(contexto, materia, repetido, "Item repetido", 1, true);
        mapear(contexto, materia, apenasUmaOcorrenciaAtiva,
                "Item sem repeticao ativa", 2, true);

        UUID evidenciaAntiga = inserirQuestoes(repetido,
                local(INICIO_DA_JANELA.minusDays(1), 10),
                10, 10, null, "ATIVO");
        UUID evidenciaRecente = inserirQuestoes(repetido,
                local(REFERENCIA, 10), 10, 10, null, "ATIVO");
        UUID padraoRepetido = inserirPadrao(usuario, repetido, "Erro de sinal");
        inserirOcorrencia(padraoRepetido, evidenciaAntiga, 2);
        inserirOcorrencia(padraoRepetido, evidenciaRecente, 3);

        UUID evidenciaAtiva = inserirQuestoes(apenasUmaOcorrenciaAtiva,
                local(REFERENCIA, 11), 10, 10, null, "ATIVO");
        UUID evidenciaCancelada = inserirQuestoes(apenasUmaOcorrenciaAtiva,
                local(REFERENCIA, 12), 10, 0, null, "CANCELADO");
        UUID padraoSemRepeticaoAtiva = inserirPadrao(
                usuario, apenasUmaOcorrenciaAtiva, "Erro de leitura");
        inserirOcorrencia(padraoSemRepeticaoAtiva, evidenciaAtiva, 1);
        inserirOcorrencia(padraoSemRepeticaoAtiva, evidenciaCancelada, 1);

        Map<UUID, DiagnosticoDeTopico> diagnosticos = diagnosticos(usuario, true);
        DiagnosticoDeTopico.PadraoRepetido padraoDoDiagnostico =
                diagnosticos.get(repetido).padroesDeErroRepetidos().getFirst();
        assertThat(padraoDoDiagnostico.quantidadeDeEvidencias()).isEqualTo(2);
        assertThat(padraoDoDiagnostico.quantidadeDeOcorrencias()).isEqualTo(5);
        assertThat(diagnosticos.get(apenasUmaOcorrenciaAtiva).padroesDeErroRepetidos())
                .isEmpty();

        Map<UUID, ResultadoDaPriorizacaoDeTopicos.TopicoPriorizado> prioridades =
                prioridades(priorizacao.consultar(usuario, REFERENCIA, null));
        assertThat(prioridades.get(repetido).faixa())
                .isEqualTo(FaixaDePriorizacao.PRECISA_REFORCO);
        assertThat(prioridades.get(repetido).indicadores().questoesRecentes())
                .isEqualTo(10);
        assertThat(prioridades.get(repetido).indicadores().quantidadeDePadroesRepetidos())
                .isEqualTo(1);
        assertThat(prioridades.get(repetido).justificativas())
                .contains(JustificativaDaPriorizacao.PADRAO_DE_ERRO_REPETIDO.mensagem());
        assertThat(prioridades.get(apenasUmaOcorrenciaAtiva)
                .indicadores().quantidadeDePadroesRepetidos()).isZero();
        assertThat(prioridades.get(apenasUmaOcorrenciaAtiva).faixa())
                .isEqualTo(FaixaDePriorizacao.DADOS_INSUFICIENTES);
    }

    private Map<UUID, DiagnosticoDeTopico> diagnosticos(
            UUID usuario, boolean somenteExigidos) {
        return diagnostico.consultar(usuario, REFERENCIA, null, somenteExigidos).stream()
                .collect(Collectors.toMap(DiagnosticoDeTopico::identificadorDoTopico,
                        Function.identity()));
    }

    private Map<UUID, ResultadoDaPriorizacaoDeTopicos.TopicoPriorizado> prioridades(
            ResultadoDaPriorizacaoDeTopicos resultado) {
        return resultado.materias().stream()
                .flatMap(materia -> materia.topicos().stream())
                .collect(Collectors.toMap(
                        ResultadoDaPriorizacaoDeTopicos.TopicoPriorizado::identificador,
                        Function.identity()));
    }

    private UUID topicoComQuestoes(ContextoOficial contexto, MateriaOficial materia,
            String nome, int ordem, int questoes, int acertos, Integer dificuldade) {
        UUID topico = inserirTopico(materia.materia(), nome, ordem);
        mapear(contexto, materia, topico, "Item " + nome, ordem, true);
        inserirQuestoes(topico, local(REFERENCIA, 10),
                questoes, acertos, dificuldade, "ATIVO");
        return topico;
    }

    private UUID inserirUsuario(String email) {
        UUID identificador = UUID.randomUUID();
        banco.update("""
                INSERT INTO usuarios (identificador, nome, email, senha_hash,
                  situacao, criado_em, atualizado_em, versao)
                VALUES (?, 'Pessoa', ?, 'hash-de-teste', 'ATIVO', ?, ?, 0)
                """, identificador, email, agora(), agora());
        return identificador;
    }

    private UUID inserirMateria(UUID usuario, String nome) {
        UUID identificador = UUID.randomUUID();
        banco.update("""
                INSERT INTO materias (identificador, usuario_id, nome, nome_normalizado,
                  arquivada, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, FALSE, ?, ?, 0)
                """, identificador, usuario, nome, normalizar(nome), agora(), agora());
        return identificador;
    }

    private UUID inserirTopico(UUID materia, String nome, int ordem) {
        UUID identificador = UUID.randomUUID();
        banco.update("""
                INSERT INTO topicos_da_materia (identificador, materia_id, nome,
                  nome_normalizado, ordem, arquivado, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, ?, FALSE, ?, ?, 0)
                """, identificador, materia, nome, normalizar(nome), ordem, agora(), agora());
        return identificador;
    }

    private ContextoOficial inserirContextoOficial(UUID usuario, String sufixo) {
        UUID concurso = UUID.randomUUID();
        banco.update("""
                INSERT INTO concursos (identificador, usuario_id, nome, nome_normalizado,
                  situacao, ativo, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, 'EM_ANDAMENTO', TRUE, ?, ?, 0)
                """, concurso, usuario, "Concurso " + sufixo,
                normalizar("Concurso " + sufixo), agora(), agora());
        UUID cargo = UUID.randomUUID();
        banco.update("""
                INSERT INTO cargos_do_concurso (identificador, concurso_id, nome,
                  nome_normalizado, nivel_de_escolaridade, selecionado, ordem,
                  criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, 'SUPERIOR', TRUE, 1, ?, ?, 0)
                """, cargo, concurso, "Cargo " + sufixo,
                normalizar("Cargo " + sufixo), agora(), agora());
        UUID prova = UUID.randomUUID();
        banco.update("""
                INSERT INTO provas (identificador, cargo_id, nome, nome_normalizado,
                  tipo, carater, ordem, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, 'OBJETIVA', 'CLASSIFICATORIO', 1, ?, ?, 0)
                """, prova, cargo, "Prova " + sufixo,
                normalizar("Prova " + sufixo), agora(), agora());
        UUID grupo = UUID.randomUUID();
        banco.update("""
                INSERT INTO grupos_de_conteudo (identificador, prova_id, nome,
                  nome_normalizado, ordem, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, 1, ?, ?, 0)
                """, grupo, prova, "Grupo " + sufixo,
                normalizar("Grupo " + sufixo), agora(), agora());
        UUID edital = UUID.randomUUID();
        banco.update("""
                INSERT INTO editais (identificador, concurso_id, titulo, principal,
                  criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, TRUE, ?, ?, 0)
                """, edital, concurso, "Edital " + sufixo, agora(), agora());
        return new ContextoOficial(concurso, cargo, edital, grupo);
    }

    private MateriaOficial inserirMateriaOficial(
            UUID usuario, ContextoOficial contexto, String nome, int ordem) {
        UUID materia = inserirMateria(usuario, nome);
        UUID materiaDaProva = UUID.randomUUID();
        banco.update("""
                INSERT INTO materias_da_prova (identificador, grupo_de_conteudo_id,
                  materia_id, ordem, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, ?, ?, 0)
                """, materiaDaProva, contexto.grupo(), materia, ordem, agora(), agora());
        return new MateriaOficial(materia, materiaDaProva);
    }

    private UUID mapear(ContextoOficial contexto, MateriaOficial materia,
            UUID topico, String descricao, int ordem, boolean confirmado) {
        UUID item = UUID.randomUUID();
        banco.update("""
                INSERT INTO itens_do_edital (identificador, edital_id,
                  materia_da_prova_id, descricao_original, ordem,
                  criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                """, item, contexto.edital(), materia.materiaDaProva(),
                descricao, ordem, agora(), agora());
        banco.update("""
                INSERT INTO mapeamentos_de_itens_do_edital (identificador,
                  item_do_edital_id, topico_da_materia_id, confirmado, criado_em)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), item, topico, confirmado, agora());
        return item;
    }

    private UUID inserirEstudo(UUID topico, OffsetDateTime dataHora,
            String tipo, String situacao) {
        UUID identificador = UUID.randomUUID();
        banco.update("""
                INSERT INTO registros_de_estudo (identificador, topico_id,
                  data_hora, duracao_em_minutos, situacao, tipo_de_estudo,
                  criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, 60, ?, ?, ?, ?, 0)
                """, identificador, topico, dataHora, situacao, tipo, agora(), agora());
        return identificador;
    }

    private UUID inserirQuestoes(UUID topico, OffsetDateTime dataHora,
            int questoes, int acertos, Integer dificuldade, String situacao) {
        UUID estudo = inserirEstudo(topico, dataHora, "QUESTOES", situacao);
        return inserirEvidencia(estudo, questoes, acertos, null, dificuldade);
    }

    private UUID inserirEvidencia(UUID estudo, Integer questoes,
            Integer acertos, Integer recordacao, Integer dificuldade) {
        UUID identificador = UUID.randomUUID();
        banco.update("""
                INSERT INTO evidencias_de_aprendizagem (identificador,
                  registro_de_estudo_id, quantidade_de_questoes,
                  quantidade_de_acertos, nivel_de_recordacao,
                  dificuldade_percebida, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, identificador, estudo, questoes, acertos, recordacao,
                dificuldade, agora(), agora());
        return identificador;
    }

    private void inserirMaterial(UUID usuario, UUID topico, boolean arquivado) {
        UUID material = UUID.randomUUID();
        banco.update("""
                INSERT INTO materiais_de_estudo (identificador, usuario_id, titulo,
                  tipo, arquivado, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, 'PDF', ?, ?, ?, 0)
                """, material, usuario, "Material " + material, arquivado, agora(), agora());
        banco.update("""
                INSERT INTO coberturas_de_topicos_por_material (identificador,
                  material_id, topico_id, criado_em)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), material, topico, agora());
    }

    private UUID inserirPadrao(UUID usuario, UUID topico, String descricao) {
        UUID identificador = UUID.randomUUID();
        banco.update("""
                INSERT INTO padroes_de_erro (identificador, usuario_id, topico_id,
                  descricao, descricao_normalizada, criado_em, atualizado_em, versao)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                """, identificador, usuario, topico, descricao,
                normalizar(descricao), agora(), agora());
        return identificador;
    }

    private void inserirOcorrencia(UUID padrao, UUID evidencia, int quantidade) {
        banco.update("""
                INSERT INTO ocorrencias_de_padrao_de_erro (identificador,
                  evidencia_id, padrao_de_erro_id, quantidade_de_ocorrencias, criado_em)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), evidencia, padrao, quantidade, agora());
    }

    private OffsetDateTime local(LocalDate data, int hora) {
        return data.atTime(hora, 0).atZone(FUSO_HORARIO).toOffsetDateTime();
    }

    private OffsetDateTime agora() {
        return REFERENCIA.atTime(12, 0).atZone(FUSO_HORARIO).toOffsetDateTime();
    }

    private String normalizar(String texto) {
        return texto.toLowerCase().replace(' ', '-');
    }

    private record ContextoOficial(UUID concurso, UUID cargo, UUID edital, UUID grupo) {
    }

    private record MateriaOficial(UUID materia, UUID materiaDaProva) {
    }

    private record LimiteEsperado(
            String percentual,
            FaixaDePriorizacao faixa,
            JustificativaDaPriorizacao justificativa) {
    }
}
