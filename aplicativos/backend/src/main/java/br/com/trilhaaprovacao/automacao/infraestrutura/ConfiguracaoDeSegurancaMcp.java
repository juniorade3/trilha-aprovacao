package br.com.trilhaaprovacao.automacao.infraestrutura;

import br.com.trilhaaprovacao.compartilhado.api.RespostaDeErro;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@ConditionalOnProperty(prefix = "trilha.automacao", name = "habilitada",
        havingValue = "true")
public class ConfiguracaoDeSegurancaMcp {

    @Bean
    @Order(1)
    SecurityFilterChain filtroDeSegurancaMcp(HttpSecurity http,
            AutenticadorDeCredencialMcp autenticador, ObjectMapper mapeador)
            throws Exception {
        AuthenticationEntryPoint pontoDeEntrada = (pedido, resposta, excecao) -> {
            resposta.setStatus(HttpStatus.UNAUTHORIZED.value());
            resposta.setContentType("application/json");
            mapeador.writeValue(resposta.getOutputStream(), new RespostaDeErro(
                    "CREDENCIAL_DE_INTEGRACAO_INVALIDA",
                    "Credencial de integracao invalida, expirada ou revogada.",
                    (String) pedido.getAttribute("identificadorDeCorrelacao"),
                    List.of()));
        };
        http.securityMatcher("/mcp", "/mcp/**")
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
                        .anyRequest().hasAuthority("INTEGRACAO_MCP"))
                .exceptionHandling(configuracao -> configuracao
                        .authenticationEntryPoint(pontoDeEntrada))
                .addFilterBefore(new FiltroDeCredencialMcp(
                                autenticador, pontoDeEntrada),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
