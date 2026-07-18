package br.com.trilhaaprovacao.compartilhado.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ConfiguracaoDeProducaoTest {
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=producao");

    @Test
    void deveDesabilitarOpenApiESwaggerPorPadraoEmProducao() {
        contexto.run(aplicacao -> {
            assertThat(aplicacao.getEnvironment().getProperty(
                    "springdoc.api-docs.enabled", Boolean.class)).isFalse();
            assertThat(aplicacao.getEnvironment().getProperty(
                    "springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
            assertThat(aplicacao.getEnvironment().getProperty(
                    "server.servlet.session.cookie.secure", Boolean.class)).isTrue();
        });
    }
}
