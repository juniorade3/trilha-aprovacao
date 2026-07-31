package br.com.trilhaaprovacao.planejamento.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.trilhaaprovacao.planejamento.dominio.ConfiguracaoDaGeracaoDeterministica;
import br.com.trilhaaprovacao.planejamento.dominio.PreviaDaGeracaoDaSemana;
import br.com.trilhaaprovacao.planejamento.dominio.TipoDeAtividade;
import br.com.trilhaaprovacao.revisoes.aplicacao.ConsultaDeRevisoesEspacadas;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class GeracaoComRevisoesIntegracaoTest {
    private static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 7, 20);
    private static final ConfiguracaoDaGeracaoDeterministica CONFIGURACAO =
            new ConfiguracaoDaGeracaoDeterministica(50, 1);

    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_geracao_revisoes")
                    .withUsername("teste")
                    .withPassword("teste");

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        propriedades.add("spring.datasource.username", POSTGRESQL::getUsername);
        propriedades.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired JdbcTemplate banco;
    @Autowired ServicoDeGeracaoDeterministica geracao;
    @Autowired ConsultaDeRevisoesEspacadas revisoes;

    @BeforeEach
    void limparBanco() {
        banco.execute("TRUNCATE TABLE usuarios CASCADE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONCLUIDO", "PARCIALMENTE_CONCLUIDO", "CANCELADO"})
    void revisaoQueNaoEstaAbertaDeveLiberarNovoBloco(String estado) {
        Cenario cenario = criarCenario(true);
        inserirBlocoDeRevisao(cenario, estado);

        PreviaDaGeracaoDaSemana previa = gerarPrevia(cenario);

        assertThat(quantidadeDeRevisoes(previa)).isEqualTo(1);
    }

    @Test
    void revisaoEmAndamentoDeveBloquearNovoBloco() {
        Cenario cenario = criarCenario(true);
        inserirBlocoDeRevisao(cenario, "EM_ANDAMENTO");

        PreviaDaGeracaoDaSemana previa = gerarPrevia(cenario);

        assertThat(quantidadeDeRevisoes(previa)).isZero();
    }

    @Test
    void regeneracaoDeveSubstituirARevisaoGeradaSemDuplicar() {
        Cenario cenario = criarCenario(true);
        ResultadoDaPreviaDaGeracao primeiraPrevia = geracao.gerarPrevia(
                cenario.usuario(), cenario.plano(), SEGUNDA, CONFIGURACAO);
        geracao.aplicar(cenario.usuario(), cenario.plano(), SEGUNDA,
                CONFIGURACAO, false, primeiraPrevia.assinaturaDaPrevia());
        UUID primeiraRevisao = identificadorDaRevisaoGerada(cenario.plano());

        ResultadoDaPreviaDaGeracao segundaPrevia = geracao.gerarPrevia(
                cenario.usuario(), cenario.plano(), SEGUNDA, CONFIGURACAO);
        assertThat(quantidadeDeRevisoes(segundaPrevia.previa())).isEqualTo(1);
        geracao.aplicar(cenario.usuario(), cenario.plano(), SEGUNDA,
                CONFIGURACAO, true, segundaPrevia.assinaturaDaPrevia());

        assertThat(quantidadeDeRevisoesPersistidas(cenario.plano())).isEqualTo(1);
        assertThat(identificadorDaRevisaoGerada(cenario.plano()))
                .isNotEqualTo(primeiraRevisao);
    }

    @Test
    void mapeamentoNaoConfirmadoEMaterialArquivadoNaoDevemInvalidarPrevia() {
        Cenario cenario = criarCenario(true);
        ResultadoDaPreviaDaGeracao previa = geracao.gerarPrevia(
                cenario.usuario(), cenario.plano(), SEGUNDA, CONFIGURACAO);

        UUID outroTopico = inserirTopico(cenario.materia(), "Topico sugerido", 2);
        banco.update("""
                INSERT INTO mapeamentos_de_itens_do_edital (
                    identificador, item_do_edital_id, topico_da_materia_id,
                    confirmado, criado_em
                ) VALUES (?, ?, ?, FALSE, ?)
                """, UUID.randomUUID(), cenario.item(), outroTopico, agora());
        banco.update("""
                INSERT INTO materiais_de_estudo (
                    identificador, usuario_id, titulo, tipo, arquivado,
                    criado_em, atualizado_em, versao
                ) VALUES (?, ?, 'Material arquivado', 'PDF', TRUE, ?, ?, 0)
                """, UUID.randomUUID(), cenario.usuario(), agora(), agora());

        geracao.aplicar(cenario.usuario(), cenario.plano(), SEGUNDA,
                CONFIGURACAO, false, previa.assinaturaDaPrevia());

        assertThat(quantidadeDeRevisoesPersistidas(cenario.plano())).isEqualTo(1);
    }

    @Test
    void corteDaDataDeReferenciaDeveUsarMeiaNoiteDeSaoPaulo() {
        Cenario cenario = criarCenario(false);
        inserirEvidencia(cenario.topico(),
                OffsetDateTime.parse("2026-07-21T02:59:00Z"),
                "TEORIA", null);
        inserirEvidencia(cenario.topico(),
                OffsetDateTime.parse("2026-07-21T03:00:00Z"),
                "REVISAO", 5);

        var antesDaMeiaNoite = revisoes.consultar(
                cenario.usuario(), SEGUNDA, SEGUNDA.plusDays(10))
                .revisoes().getFirst();
        var depoisDaMeiaNoite = revisoes.consultar(
                cenario.usuario(), SEGUNDA.plusDays(1), SEGUNDA.plusDays(10))
                .revisoes().getFirst();

        assertThat(antesDaMeiaNoite.etapa()).isZero();
        assertThat(antesDaMeiaNoite.dataDevida()).isEqualTo(SEGUNDA.plusDays(1));
        assertThat(depoisDaMeiaNoite.etapa()).isEqualTo(2);
        assertThat(depoisDaMeiaNoite.dataDevida()).isEqualTo(SEGUNDA.plusDays(8));
    }

    private PreviaDaGeracaoDaSemana gerarPrevia(Cenario cenario) {
        return geracao.gerarPrevia(cenario.usuario(), cenario.plano(),
                SEGUNDA, CONFIGURACAO).previa();
    }

    private long quantidadeDeRevisoes(PreviaDaGeracaoDaSemana previa) {
        return previa.dias().stream()
                .flatMap(dia -> dia.blocosSugeridos().stream())
                .filter(bloco -> bloco.tipoDeAtividade() == TipoDeAtividade.REVISAO)
                .count();
    }

    private int quantidadeDeRevisoesPersistidas(UUID plano) {
        return banco.queryForObject("""
                SELECT COUNT(*) FROM blocos_de_estudo
                WHERE plano_id = ? AND tipo_de_atividade = 'REVISAO'
                  AND estado <> 'CANCELADO'
                """, Integer.class, plano);
    }

    private UUID identificadorDaRevisaoGerada(UUID plano) {
        return banco.queryForObject("""
                SELECT identificador FROM blocos_de_estudo
                WHERE plano_id = ? AND tipo_de_atividade = 'REVISAO'
                  AND estado <> 'CANCELADO'
                """, UUID.class, plano);
    }

    private void inserirBlocoDeRevisao(Cenario cenario, String estado) {
        UUID planoAnterior = UUID.randomUUID();
        banco.update("""
                INSERT INTO planos_semanais (
                    identificador, usuario_id, data_inicial, estado,
                    criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, 'ATIVO', ?, ?, 0)
                """, planoAnterior, cenario.usuario(), SEGUNDA.minusWeeks(1),
                agora(), agora());
        banco.update("""
                INSERT INTO blocos_de_estudo (
                    identificador, plano_id, materia_id, topico_id, titulo,
                    tipo_de_atividade, data, duracao_prevista_em_minutos,
                    ordem, origem, estado, quantidade_de_reagendamentos,
                    criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, ?, 'Revisao anterior', 'REVISAO', ?,
                    20, 1, 'MANUAL', ?, 0, ?, ?, 0)
                """, UUID.randomUUID(), planoAnterior, cenario.materia(),
                cenario.topico(), SEGUNDA.minusDays(1), estado, agora(), agora());
    }

    private Cenario criarCenario(boolean comEvidencia) {
        OffsetDateTime agora = agora();
        UUID usuario = UUID.randomUUID();
        banco.update("""
                INSERT INTO usuarios (
                    identificador, nome, email, senha_hash, situacao,
                    criado_em, atualizado_em, versao
                ) VALUES (?, 'Pessoa', ?, 'hash', 'ATIVO', ?, ?, 0)
                """, usuario, usuario + "@example.com", agora, agora);

        UUID materia = UUID.randomUUID();
        banco.update("""
                INSERT INTO materias (
                    identificador, usuario_id, nome, nome_normalizado,
                    arquivada, criado_em, atualizado_em, versao
                ) VALUES (?, ?, 'Direito', 'direito', FALSE, ?, ?, 0)
                """, materia, usuario, agora, agora);
        UUID topico = inserirTopico(materia, "Atos administrativos", 1);

        UUID concurso = UUID.randomUUID();
        banco.update("""
                INSERT INTO concursos (
                    identificador, usuario_id, nome, nome_normalizado,
                    situacao, ativo, criado_em, atualizado_em, versao
                ) VALUES (?, ?, 'Concurso', 'concurso', 'EM_ANDAMENTO',
                    TRUE, ?, ?, 0)
                """, concurso, usuario, agora, agora);
        UUID edital = UUID.randomUUID();
        banco.update("""
                INSERT INTO editais (
                    identificador, concurso_id, titulo, principal,
                    criado_em, atualizado_em, versao
                ) VALUES (?, ?, 'Edital principal', TRUE, ?, ?, 0)
                """, edital, concurso, agora, agora);
        UUID cargo = UUID.randomUUID();
        banco.update("""
                INSERT INTO cargos_do_concurso (
                    identificador, concurso_id, nome, nome_normalizado,
                    nivel_de_escolaridade, selecionado, ordem,
                    criado_em, atualizado_em, versao
                ) VALUES (?, ?, 'Cargo', 'cargo', 'SUPERIOR', TRUE, 1, ?, ?, 0)
                """, cargo, concurso, agora, agora);
        UUID prova = UUID.randomUUID();
        banco.update("""
                INSERT INTO provas (
                    identificador, cargo_id, nome, nome_normalizado,
                    tipo, carater, ordem, criado_em, atualizado_em, versao
                ) VALUES (?, ?, 'Prova', 'prova', 'OBJETIVA',
                    'CLASSIFICATORIO', 1, ?, ?, 0)
                """, prova, cargo, agora, agora);
        UUID grupo = UUID.randomUUID();
        banco.update("""
                INSERT INTO grupos_de_conteudo (
                    identificador, prova_id, nome, nome_normalizado,
                    ordem, criado_em, atualizado_em, versao
                ) VALUES (?, ?, 'Conhecimentos', 'conhecimentos', 1, ?, ?, 0)
                """, grupo, prova, agora, agora);
        UUID materiaDaProva = UUID.randomUUID();
        banco.update("""
                INSERT INTO materias_da_prova (
                    identificador, grupo_de_conteudo_id, materia_id, ordem,
                    criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, 1, ?, ?, 0)
                """, materiaDaProva, grupo, materia, agora, agora);
        UUID item = UUID.randomUUID();
        banco.update("""
                INSERT INTO itens_do_edital (
                    identificador, edital_id, materia_da_prova_id,
                    descricao_original, ordem, criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, 'Atos administrativos', 1, ?, ?, 0)
                """, item, edital, materiaDaProva, agora, agora);
        banco.update("""
                INSERT INTO mapeamentos_de_itens_do_edital (
                    identificador, item_do_edital_id, topico_da_materia_id,
                    confirmado, criado_em
                ) VALUES (?, ?, ?, TRUE, ?)
                """, UUID.randomUUID(), item, topico, agora);

        UUID plano = UUID.randomUUID();
        banco.update("""
                INSERT INTO planos_semanais (
                    identificador, usuario_id, data_inicial, estado,
                    criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, 'RASCUNHO', ?, ?, 0)
                """, plano, usuario, SEGUNDA, agora, agora);
        for (int indice = 0; indice < 7; indice++) {
            banco.update("""
                    INSERT INTO disponibilidades_do_dia (
                        identificador, plano_id, data, minutos_disponiveis,
                        criado_em, atualizado_em, versao
                    ) VALUES (?, ?, ?, ?, ?, ?, 0)
                    """, UUID.randomUUID(), plano, SEGUNDA.plusDays(indice),
                    indice == 0 ? 70 : 0, agora, agora);
        }
        if (comEvidencia) {
            inserirEvidencia(topico,
                    SEGUNDA.minusDays(4).atTime(12, 0)
                            .atZone(FUSO_HORARIO).toOffsetDateTime(),
                    "TEORIA", null);
        }
        return new Cenario(usuario, materia, topico, edital, item, plano);
    }

    private UUID inserirTopico(UUID materia, String nome, int ordem) {
        UUID topico = UUID.randomUUID();
        banco.update("""
                INSERT INTO topicos_da_materia (
                    identificador, materia_id, nome, nome_normalizado, ordem,
                    arquivado, criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, ?, ?, FALSE, ?, ?, 0)
                """, topico, materia, nome, nome.toLowerCase(), ordem, agora(), agora());
        return topico;
    }

    private void inserirEvidencia(UUID topico, OffsetDateTime instante,
            String tipoDeEstudo, Integer recordacao) {
        UUID registro = UUID.randomUUID();
        banco.update("""
                INSERT INTO registros_de_estudo (
                    identificador, topico_id, data_hora, duracao_em_minutos,
                    situacao, tipo_de_estudo, criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, 40, 'ATIVO', ?, ?, ?, 0)
                """, registro, topico, instante, tipoDeEstudo, instante, instante);
        banco.update("""
                INSERT INTO evidencias_de_aprendizagem (
                    identificador, registro_de_estudo_id, nivel_de_recordacao,
                    dificuldade_percebida, criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, ?, ?, ?, 0)
                """, UUID.randomUUID(), registro, recordacao,
                recordacao == null ? 3 : null, instante, instante);
    }

    private OffsetDateTime agora() {
        return SEGUNDA.atTime(12, 0).atZone(FUSO_HORARIO).toOffsetDateTime();
    }

    private record Cenario(
            UUID usuario,
            UUID materia,
            UUID topico,
            UUID edital,
            UUID item,
            UUID plano) {
    }
}
