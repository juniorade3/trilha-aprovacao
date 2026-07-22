package br.com.trilhaaprovacao.automacao.infraestrutura;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("automacao")
@ConditionalOnProperty(prefix = "trilha.automacao", name = "habilitada",
        havingValue = "true")
public class SaudeDaAutomacao implements HealthIndicator {
    private final JdbcTemplate banco;

    public SaudeDaAutomacao(JdbcTemplate banco) { this.banco = banco; }

    @Override
    public Health health() {
        try {
            Long vinculos = banco.queryForObject("""
                    SELECT COUNT(*) FROM vinculos_de_canal
                     WHERE canal = 'TELEGRAM' AND estado = 'ATIVO'
                    """, Long.class);
            Long pendentes = banco.queryForObject("""
                    SELECT COUNT(*) FROM operacoes_assistidas
                     WHERE estado IN ('PREPARADA', 'AGUARDANDO_CONFIRMACAO',
                                      'CONFIRMADA')
                    """, Long.class);
            return Health.up().withDetail("vinculosAtivos", vinculos)
                    .withDetail("operacoesPendentes", pendentes).build();
        } catch (RuntimeException excecao) {
            return Health.down().withException(excecao).build();
        }
    }
}
