package br.com.trilhaaprovacao.autenticacao.infraestrutura;

import br.com.trilhaaprovacao.compartilhado.api.RespostaDeErro;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class ConfiguracaoDeSeguranca {
    @Bean PasswordEncoder codificadorDeSenha() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService detalhesDoUsuario(RepositorioDeUsuarios repositorio) {
        return email -> repositorio.findByEmail(email)
                .filter(usuario -> usuario.situacao() == SituacaoDoUsuario.ATIVO)
                .map(usuario -> User.withUsername(usuario.email()).password(usuario.senhaHash()).authorities("USUARIO").build())
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("Conta nao encontrada."));
    }

    @Bean
    AuthenticationManager gerenciadorDeAutenticacao(UserDetailsService detalhesDoUsuario, PasswordEncoder codificadorDeSenha) {
        DaoAuthenticationProvider provedor = new DaoAuthenticationProvider(detalhesDoUsuario);
        provedor.setPasswordEncoder(codificadorDeSenha);
        return new ProviderManager(provedor);
    }

    @Bean SecurityContextRepository repositorioDeContexto() { return new HttpSessionSecurityContextRepository(); }

    @Bean
    SecurityFilterChain filtroDeSeguranca(HttpSecurity http, SecurityContextRepository repositorioDeContexto, ObjectMapper mapeador) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieCustomizer(cookie -> cookie.sameSite("Lax").path("/"));
        http
                .csrf(configuracao -> configuracao.csrfTokenRepository(csrf))
                .securityContext(configuracao -> configuracao.securityContextRepository(repositorioDeContexto).requireExplicitSave(true))
                .sessionManagement(configuracao -> configuracao.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(autorizacao -> autorizacao
                        .requestMatchers(
                                "/actuator/health",
                                "/api/v1/autenticacao/csrf",
                                "/api/v1/autenticacao/cadastro",
                                "/api/v1/autenticacao/login",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .httpBasic(configuracao -> configuracao.disable())
                .formLogin(configuracao -> configuracao.disable())
                .exceptionHandling(configuracao -> configuracao
                        .authenticationEntryPoint((pedido, resposta, excecao) -> erro(mapeador, resposta, HttpStatus.UNAUTHORIZED, "AUTENTICACAO_NECESSARIA", "Faca login para continuar.", pedido.getAttribute("identificadorDeCorrelacao")))
                        .accessDeniedHandler((pedido, resposta, excecao) -> erro(mapeador, resposta, HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Voce nao tem permissao para esta operacao.", pedido.getAttribute("identificadorDeCorrelacao"))));
        return http.build();
    }

    private void erro(ObjectMapper mapeador, HttpServletResponse resposta, HttpStatus status, String codigo, String mensagem, Object correlacao) throws java.io.IOException {
        resposta.setStatus(status.value());
        resposta.setContentType("application/json");
        mapeador.writeValue(resposta.getOutputStream(), new RespostaDeErro(codigo, mensagem, (String) correlacao, List.of()));
    }
}
