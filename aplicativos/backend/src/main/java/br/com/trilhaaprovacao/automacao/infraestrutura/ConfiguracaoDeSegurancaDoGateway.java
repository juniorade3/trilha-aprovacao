package br.com.trilhaaprovacao.automacao.infraestrutura;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@ConditionalOnProperty(prefix = "trilha.automacao", name = "habilitada",
        havingValue = "true")
public class ConfiguracaoDeSegurancaDoGateway {

    @Bean
    @Order(2)
    SecurityFilterChain filtroDeSegurancaDoGateway(HttpSecurity http,
            ValidadorDeAssinaturaDoGateway validador,
            ObjectMapper mapeador,
            @Value("${trilha.automacao.limite-do-corpo-do-gateway:65536}")
                    int limiteDoCorpo) throws Exception {
        http.securityMatcher("/api/v1/integracoes-confiaveis/**")
                .csrf(configuracao -> configuracao.disable())
                .securityContext(configuracao -> configuracao
                        .requireExplicitSave(true))
                .sessionManagement(configuracao -> configuracao
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(configuracao -> configuracao.disable())
                .httpBasic(configuracao -> configuracao.disable())
                .formLogin(configuracao -> configuracao.disable())
                .logout(configuracao -> configuracao.disable())
                .authorizeHttpRequests(autorizacao -> autorizacao
                        .anyRequest().hasAuthority("GATEWAY_CONFIAVEL"))
                .addFilterBefore(new FiltroDeAssinaturaDoGateway(
                                validador, mapeador, limiteDoCorpo),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
