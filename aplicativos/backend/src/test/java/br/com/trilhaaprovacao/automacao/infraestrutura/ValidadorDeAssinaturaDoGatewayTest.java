package br.com.trilhaaprovacao.automacao.infraestrutura;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ValidadorDeAssinaturaDoGatewayTest {
    private static final String SEGREDO_FORTE = "s".repeat(32);

    @Test
    void deveAceitarLimitesSegurosERetencaoIgualATolerancia() {
        assertThatCode(() -> novo(SEGREDO_FORTE, Duration.ofSeconds(1),
                1, Duration.ofSeconds(1), true)).doesNotThrowAnyException();
        assertThatCode(() -> novo(SEGREDO_FORTE, Duration.ofMinutes(10),
                10_000, Duration.ofDays(30), true)).doesNotThrowAnyException();
    }

    @Test
    void deveRecusarRetencaoMenorQueATolerancia() {
        assertThatThrownBy(() -> novo(SEGREDO_FORTE, Duration.ofMinutes(2),
                60, Duration.ofMinutes(1), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RETENCAO_DE_NONCES_DO_GATEWAY")
                .hasMessageContaining("maior ou igual a tolerancia");
    }

    @Test
    void deveRecusarSegredoToleranciaRetencaoELimiteForaDasFaixas() {
        assertThatThrownBy(() -> novo("á".repeat(15), Duration.ofMinutes(2),
                60, Duration.ofDays(7), true))
                .hasMessageContaining("SEGREDO_DO_GATEWAY_OPENCLAW");
        assertThatThrownBy(() -> novo(SEGREDO_FORTE, Duration.ZERO,
                60, Duration.ofDays(7), true))
                .hasMessageContaining("TOLERANCIA_DO_GATEWAY_OPENCLAW");
        assertThatThrownBy(() -> novo(SEGREDO_FORTE, Duration.ofMinutes(11),
                60, Duration.ofDays(7), true))
                .hasMessageContaining("TOLERANCIA_DO_GATEWAY_OPENCLAW");
        assertThatThrownBy(() -> novo(SEGREDO_FORTE, Duration.ofMinutes(2),
                10_001, Duration.ofDays(7), true))
                .hasMessageContaining("LIMITE_DO_GATEWAY_POR_MINUTO");
        assertThatThrownBy(() -> novo(SEGREDO_FORTE, Duration.ofMinutes(2),
                60, Duration.ofDays(31), true))
                .hasMessageContaining("RETENCAO_DE_NONCES_DO_GATEWAY");
    }

    @Test
    void devePermitirFlagDesligadaMasRecusarUsoComConfiguracaoInvalida() {
        ValidadorDeAssinaturaDoGateway validador = novo("", Duration.ZERO,
                0, Duration.ZERO, false);
        assertThatThrownBy(() -> validador.validar("", "", "", "", "",
                "POST", "/teste", new byte[0]))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Configuracao de seguranca do Gateway invalida");
    }

    private ValidadorDeAssinaturaDoGateway novo(String segredo,
            Duration tolerancia, int limite, Duration retencao,
            boolean habilitada) {
        return new ValidadorDeAssinaturaDoGateway(mock(JdbcTemplate.class),
                "gateway-openclaw", segredo, tolerancia, limite, retencao,
                habilitada);
    }
}
