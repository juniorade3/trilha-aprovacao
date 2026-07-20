package br.com.trilhaaprovacao.planejamento.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
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
    void deveAplicarAteV13EmPostgresqlVazioComTodasAsGarantias() {
        var resultado = Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword())
                .load()
                .migrate();
        JdbcTemplate banco = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword()));

        assertThat(resultado.success).isTrue();
        assertThat(resultado.targetSchemaVersion).isEqualTo("13");
        assertThat(banco.queryForObject("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE success = TRUE
                """, Integer.class)).isEqualTo(13);
        assertThat(banco.queryForObject("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE version IN ('11', '12') AND success = TRUE
                """, Integer.class)).isEqualTo(2);

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
    }

    private int quantidadeDeRestricoes(JdbcTemplate banco, String nome, String tipo) {
        return banco.queryForObject("""
                SELECT count(*)
                FROM pg_constraint
                WHERE conname = ? AND contype = ?::"char"
                """, Integer.class, nome, tipo);
    }
}
