package br.com.trilhaaprovacao.compartilhado.infraestrutura;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroDeCorrelacao extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia) throws ServletException, IOException {
        String identificador = requisicao.getHeader("X-Identificador-De-Correlacao");
        if (identificador == null || identificador.isBlank()) identificador = UUID.randomUUID().toString();
        requisicao.setAttribute("identificadorDeCorrelacao", identificador);
        resposta.setHeader("X-Identificador-De-Correlacao", identificador);
        cadeia.doFilter(requisicao, resposta);
    }
}
