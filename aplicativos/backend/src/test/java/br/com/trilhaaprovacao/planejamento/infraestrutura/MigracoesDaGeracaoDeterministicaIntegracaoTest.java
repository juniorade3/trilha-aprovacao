package br.com.trilhaaprovacao.planejamento.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class MigracoesDaGeracaoDeterministicaIntegracaoTest {
    @Container
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("trilha_aprovacao_migracoes_geracao")
                    .withUsername("teste")
                    .withPassword("teste");

    @Test
    void deveAplicarAteV19EmPostgresqlVazioComTodasAsGarantias() {
        var resultado = Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword())
                .load()
                .migrate();
        JdbcTemplate banco = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword()));

        assertThat(resultado.success).isTrue();
        assertThat(resultado.targetSchemaVersion).isEqualTo("19");
        assertThat(banco.queryForObject("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE success = TRUE
                """, Integer.class)).isEqualTo(21);
        assertThat(banco.queryForObject("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE version IN ('11', '12') AND success = TRUE
                """, Integer.class)).isEqualTo(2);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN (
                    'importacoes_de_edital',
                    'versoes_da_extracao_do_edital',
                    'relatorios_da_importacao_do_edital',
                    'proveniencias_da_importacao_do_edital')
                """, Integer.class)).isEqualTo(4);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'itens_do_edital'
                  AND column_name IN ('numero_oficial',
                      'descricao_normalizada', 'importacao_de_edital_id',
                      'importacao_de_edital_usuario_id')
                """, Integer.class)).isEqualTo(4);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'topicos_da_materia'
                  AND column_name = 'numero_oficial'
                """, Integer.class)).isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM pg_constraint
                 WHERE conname IN (
                    'ck_importacoes_de_edital_versao_e_hash',
                    'ck_importacoes_de_edital_estado_e_extracao',
                    'ck_importacoes_de_edital_destino',
                    'ck_importacoes_de_edital_operacao',
                    'ck_importacoes_de_edital_aplicacao')
                   AND connamespace = 'public'::regnamespace
                """, Integer.class)).isEqualTo(5);

        assertThat(banco.queryForObject("""
                SELECT to_regclass('public.prioridades_de_materias_no_plano')::text
                """, String.class)).isEqualTo("prioridades_de_materias_no_plano");
        assertThat(quantidadeDeRestricoes(banco,
                "uk_prioridades_plano_materia", "u")).isEqualTo(1);
        assertThat(quantidadeDeRestricoes(banco,
                "ck_prioridades_valor", "c")).isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'prioridades_de_materias_no_plano'
                  AND indexname = 'idx_prioridades_plano'
                """, Integer.class)).isEqualTo(1);

        assertThat(banco.queryForObject("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'blocos_de_estudo'
                  AND column_name = 'origem'
                """, String.class)).isEqualTo("NO");
        assertThat(banco.queryForObject("""
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'blocos_de_estudo'
                  AND column_name = 'justificativa_da_geracao'
                """, Integer.class)).isEqualTo(2000);
        assertThat(quantidadeDeRestricoes(banco, "ck_blocos_origem", "c"))
                .isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'blocos_de_estudo'
                  AND indexname = 'idx_blocos_plano_origem'
                """, Integer.class)).isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'blocos_de_estudo'
                  AND indexname = 'idx_blocos_plano_data_ordem'
                """, String.class)).contains("(plano_id, data, ordem)");
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN (
                    'blocos_originais_dos_planos', 'replanejamentos',
                    'itens_de_replanejamento', 'fragmentos_de_replanejamento')
                """, Integer.class)).isEqualTo(4);
        assertThat(quantidadeDeRestricoes(banco,
                "uk_itens_replanejamento_bloco", "u")).isEqualTo(1);
        assertThat(quantidadeDeRestricoes(banco,
                "uk_fragmentos_replanejamento_bloco", "u")).isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT pg_get_expr(indpred, indrelid)
                FROM pg_index
                WHERE indexrelid =
                    'uk_planos_semanais_usuario_data_nao_cancelado'::regclass
                """, String.class)).contains("CANCELADO");
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN (
                    'evidencias_de_aprendizagem', 'padroes_de_erro',
                    'ocorrencias_de_padrao_de_erro')
                """, Integer.class)).isEqualTo(3);
        assertThat(banco.queryForObject("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'registros_de_estudo'
                  AND column_name = 'tipo_de_estudo'
                """, String.class)).isEqualTo("NO");
        assertThat(quantidadeDeRestricoes(banco, "ck_registros_tipo_de_estudo", "c"))
                .isEqualTo(1);
        assertThat(quantidadeDeRestricoes(banco, "uk_evidencias_registro", "u"))
                .isEqualTo(1);
        assertThat(quantidadeDeRestricoes(
                banco, "uk_padroes_usuario_topico_descricao", "u")).isEqualTo(1);
        assertThat(quantidadeDeRestricoes(
                banco, "uk_ocorrencias_evidencia_padrao", "u")).isEqualTo(1);
        assertThat(banco.queryForObject("""
                SELECT count(*) FROM pg_indexes
                WHERE schemaname = 'public' AND indexname IN (
                    'idx_registros_topico_data_situacao',
                    'idx_ocorrencias_padrao_evidencia')
                """, Integer.class)).isEqualTo(2);
        assertThat(banco.queryForObject("""
                SELECT count(*)
                FROM information_schema.referential_constraints
                WHERE constraint_schema = 'public'
                  AND constraint_name IN (
                    'fk_evidencias_registro', 'fk_padroes_usuario',
                    'fk_padroes_topico', 'fk_ocorrencias_evidencia',
                    'fk_ocorrencias_padrao')
                  AND delete_rule = 'NO ACTION'
                """, Integer.class)).isEqualTo(5);

        validarRestricoesDaV15(banco);
    }

    @Test
    void deveRecuperarTipoDaCadeiaDeCorrecoesNaV15() {
        String esquema = "teste_backfill_v15";
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword())
                .schemas(esquema).defaultSchema(esquema).target("14")
                .load().migrate();
        JdbcTemplate banco = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword()));
        UUID usuario = UUID.randomUUID();
        UUID materia = UUID.randomUUID();
        UUID topico = UUID.randomUUID();
        UUID plano = UUID.randomUUID();
        UUID bloco = UUID.randomUUID();
        UUID original = UUID.randomUUID();
        UUID correcao = UUID.randomUUID();
        UUID correcaoPosterior = UUID.randomUUID();
        UUID manual = UUID.randomUUID();

        banco.update("""
                INSERT INTO teste_backfill_v15.usuarios
                    (identificador, nome, email, senha_hash, situacao, criado_em, atualizado_em)
                VALUES (?, 'Teste', ?, 'hash', 'ATIVO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, usuario, usuario + "@example.com");
        banco.update("""
                INSERT INTO teste_backfill_v15.materias
                    (identificador, usuario_id, nome, nome_normalizado,
                     arquivada, criado_em, atualizado_em)
                VALUES (?, ?, 'Direito', 'direito', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, materia, usuario);
        banco.update("""
                INSERT INTO teste_backfill_v15.topicos_da_materia
                    (identificador, materia_id, nome, nome_normalizado, ordem,
                     arquivado, criado_em, atualizado_em)
                VALUES (?, ?, 'Atos', 'atos', 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, topico, materia);
        banco.update("""
                INSERT INTO teste_backfill_v15.planos_semanais
                    (identificador, usuario_id, data_inicial, estado, criado_em, atualizado_em)
                VALUES (?, ?, DATE '2026-07-20', 'RASCUNHO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, plano, usuario);
        banco.update("""
                INSERT INTO teste_backfill_v15.blocos_de_estudo
                    (identificador, plano_id, materia_id, topico_id, titulo,
                     tipo_de_atividade, data, duracao_prevista_em_minutos,
                     ordem, estado, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, 'Questões', 'QUESTOES', DATE '2026-07-20',
                        30, 1, 'CONCLUIDO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, bloco, plano, materia, topico);
        banco.update("""
                INSERT INTO teste_backfill_v15.registros_de_estudo
                    (identificador, topico_id, data_hora, duracao_em_minutos,
                     situacao, criado_em, atualizado_em)
                VALUES (?, ?, CURRENT_TIMESTAMP, 20, 'CORRIGIDO',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, original, topico);
        banco.update("""
                INSERT INTO teste_backfill_v15.registros_de_estudo
                    (identificador, topico_id, registro_de_origem_id, data_hora,
                     duracao_em_minutos, situacao, criado_em, atualizado_em)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, 25, 'ATIVO',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, correcao, topico, original);
        banco.update("""
                INSERT INTO teste_backfill_v15.registros_de_estudo
                    (identificador, topico_id, registro_de_origem_id, data_hora,
                     duracao_em_minutos, situacao, criado_em, atualizado_em)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, 25, 'ATIVO',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, correcaoPosterior, topico, correcao);
        banco.update("""
                INSERT INTO teste_backfill_v15.registros_de_estudo
                    (identificador, topico_id, data_hora, duracao_em_minutos,
                     situacao, criado_em, atualizado_em)
                VALUES (?, ?, CURRENT_TIMESTAMP, 15, 'ATIVO',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, manual, topico);
        banco.update("""
                INSERT INTO teste_backfill_v15.execucoes_de_bloco
                    (identificador, usuario_id, bloco_id, iniciada_em, encerrada_em,
                     duracao_executada_em_minutos, resultado, registro_de_estudo_id,
                     criado_em, atualizado_em)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP - INTERVAL '30 minutes',
                        CURRENT_TIMESTAMP, 25, 'CONCLUIDO', ?,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), usuario, bloco, correcao);

        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword())
                .schemas(esquema).defaultSchema(esquema)
                .load().migrate();

        assertThat(banco.queryForList("""
                SELECT tipo_de_estudo
                FROM teste_backfill_v15.registros_de_estudo
                WHERE identificador IN (?, ?, ?)
                ORDER BY identificador
                """, String.class, original, correcao, correcaoPosterior))
                .containsExactly("QUESTOES", "QUESTOES", "QUESTOES");
        assertThat(banco.queryForObject("""
                SELECT tipo_de_estudo
                FROM teste_backfill_v15.registros_de_estudo
                WHERE identificador = ?
                """, String.class, manual)).isEqualTo("OUTRA");
    }

    private int quantidadeDeRestricoes(JdbcTemplate banco, String nome, String tipo) {
        return banco.queryForObject("""
                SELECT count(*)
                FROM pg_constraint
                WHERE conname = ? AND contype = ?::"char"
                  AND connamespace = 'public'::regnamespace
                """, Integer.class, nome, tipo);
    }

    private void validarRestricoesDaV15(JdbcTemplate banco) {
        UUID usuario = UUID.randomUUID();
        UUID materia = UUID.randomUUID();
        UUID topico = UUID.randomUUID();
        UUID registro = UUID.randomUUID();
        banco.update("""
                INSERT INTO usuarios
                    (identificador, nome, email, senha_hash, situacao, criado_em, atualizado_em)
                VALUES (?, 'Restricoes', ?, 'hash', 'ATIVO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, usuario, usuario + "@example.com");
        banco.update("""
                INSERT INTO materias
                    (identificador, usuario_id, nome, nome_normalizado,
                     arquivada, criado_em, atualizado_em)
                VALUES (?, ?, 'Materia', 'materia', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, materia, usuario);
        banco.update("""
                INSERT INTO topicos_da_materia
                    (identificador, materia_id, nome, nome_normalizado, ordem,
                     arquivado, criado_em, atualizado_em)
                VALUES (?, ?, 'Topico', 'topico', 1, FALSE,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, topico, materia);
        banco.update("""
                INSERT INTO registros_de_estudo
                    (identificador, topico_id, tipo_de_estudo, data_hora,
                     duracao_em_minutos, situacao, criado_em, atualizado_em)
                VALUES (?, ?, 'QUESTOES', CURRENT_TIMESTAMP, 30, 'ATIVO',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, registro, topico);

        assertThatFalhaPorRestricao(() -> banco.update("""
                INSERT INTO evidencias_de_aprendizagem
                    (identificador, registro_de_estudo_id,
                     quantidade_de_questoes, quantidade_de_acertos,
                     criado_em, atualizado_em)
                VALUES (?, ?, 5, 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), registro));
        assertThatFalhaPorRestricao(() -> banco.update("""
                INSERT INTO evidencias_de_aprendizagem
                    (identificador, registro_de_estudo_id,
                     nivel_de_recordacao, criado_em, atualizado_em)
                VALUES (?, ?, 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), registro));
        assertThatFalhaPorRestricao(() -> banco.update("""
                INSERT INTO evidencias_de_aprendizagem
                    (identificador, registro_de_estudo_id,
                     dificuldade_percebida, criado_em, atualizado_em)
                VALUES (?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), registro));
        assertThatFalhaPorRestricao(() -> banco.update("""
                INSERT INTO evidencias_de_aprendizagem
                    (identificador, registro_de_estudo_id, criado_em, atualizado_em)
                VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), registro));

        UUID evidencia = UUID.randomUUID();
        banco.update("""
                INSERT INTO evidencias_de_aprendizagem
                    (identificador, registro_de_estudo_id,
                     quantidade_de_questoes, quantidade_de_acertos,
                     criado_em, atualizado_em)
                VALUES (?, ?, 10, 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, evidencia, registro);
        assertThatFalhaPorRestricao(() -> banco.update("""
                INSERT INTO evidencias_de_aprendizagem
                    (identificador, registro_de_estudo_id,
                     quantidade_de_questoes, quantidade_de_acertos,
                     criado_em, atualizado_em)
                VALUES (?, ?, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), registro));

        UUID padrao = UUID.randomUUID();
        banco.update("""
                INSERT INTO padroes_de_erro
                    (identificador, usuario_id, topico_id, descricao,
                     descricao_normalizada, criado_em, atualizado_em)
                VALUES (?, ?, ?, 'Erro', 'erro', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, padrao, usuario, topico);
        assertThatFalhaPorRestricao(() -> banco.update("""
                INSERT INTO padroes_de_erro
                    (identificador, usuario_id, topico_id, descricao,
                     descricao_normalizada, criado_em, atualizado_em)
                VALUES (?, ?, ?, 'ERRO', 'erro', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), usuario, topico));
        assertThatFalhaPorRestricao(() -> banco.update("""
                INSERT INTO ocorrencias_de_padrao_de_erro
                    (identificador, evidencia_id, padrao_de_erro_id,
                     quantidade_de_ocorrencias, criado_em)
                VALUES (?, ?, ?, 0, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), evidencia, padrao));
        banco.update("""
                INSERT INTO ocorrencias_de_padrao_de_erro
                    (identificador, evidencia_id, padrao_de_erro_id,
                     quantidade_de_ocorrencias, criado_em)
                VALUES (?, ?, ?, 1, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), evidencia, padrao);
        assertThatFalhaPorRestricao(() -> banco.update("""
                INSERT INTO ocorrencias_de_padrao_de_erro
                    (identificador, evidencia_id, padrao_de_erro_id,
                     quantidade_de_ocorrencias, criado_em)
                VALUES (?, ?, ?, 1, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), evidencia, padrao));
    }

    private void assertThatFalhaPorRestricao(Runnable operacao) {
        assertThatThrownBy(operacao::run)
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
