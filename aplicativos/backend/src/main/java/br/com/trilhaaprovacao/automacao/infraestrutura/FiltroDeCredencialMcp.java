package br.com.trilhaaprovacao.automacao.infraestrutura;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

public final class FiltroDeCredencialMcp extends OncePerRequestFilter {
    public static final String ATRIBUTO_DA_IDENTIDADE =
            IdentidadeDaIntegracaoMcp.class.getName();
    public static final String CABECALHO_DO_AGENTE =
            "X-Identificador-Do-Agente";
    public static final String CABECALHO_DA_SESSAO =
            "X-Identificador-Da-Sessao";

    private final AutenticadorDeCredencialMcp autenticador;
    private final AuthenticationEntryPoint pontoDeEntrada;

    public FiltroDeCredencialMcp(AutenticadorDeCredencialMcp autenticador,
            AuthenticationEntryPoint pontoDeEntrada) {
        this.autenticador = autenticador;
        this.pontoDeEntrada = pontoDeEntrada;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido,
            HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        String autorizacao = pedido.getHeader(HttpHeaders.AUTHORIZATION);
        if (autorizacao == null || autorizacao.isBlank()) {
            cadeia.doFilter(pedido, resposta);
            return;
        }
        if (!autorizacao.startsWith("Bearer ")) {
            pontoDeEntrada.commence(pedido, resposta,
                    new BadCredentialsException("Esquema de autenticacao invalido."));
            return;
        }
        try {
            IdentidadeDaIntegracaoMcp identidade = autenticador.autenticar(
                    autorizacao.substring(7).trim(),
                    pedido.getHeader(CABECALHO_DO_AGENTE),
                    pedido.getHeader(CABECALHO_DA_SESSAO));
            var autenticacao = UsernamePasswordAuthenticationToken.authenticated(
                    identidade, null,
                    List.of(new SimpleGrantedAuthority("INTEGRACAO_MCP")));
            var contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(autenticacao);
            SecurityContextHolder.setContext(contexto);
            pedido.setAttribute(ATRIBUTO_DA_IDENTIDADE, identidade);
            cadeia.doFilter(pedido, resposta);
        } catch (BadCredentialsException excecao) {
            SecurityContextHolder.clearContext();
            pontoDeEntrada.commence(pedido, resposta, excecao);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
