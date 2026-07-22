package br.com.trilhaaprovacao.automacao.infraestrutura;

import br.com.trilhaaprovacao.automacao.infraestrutura.PedidoComCorpoReutilizavel.CorpoDoGatewayMuitoGrande;
import br.com.trilhaaprovacao.automacao.infraestrutura.ValidadorDeAssinaturaDoGateway.IdempotenciaDoGatewayReutilizada;
import br.com.trilhaaprovacao.automacao.infraestrutura.ValidadorDeAssinaturaDoGateway.LimiteDoGatewayAtingido;
import br.com.trilhaaprovacao.automacao.infraestrutura.ValidadorDeAssinaturaDoGateway.RequisicaoDoGatewayRepetida;
import br.com.trilhaaprovacao.compartilhado.api.RespostaDeErro;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

public final class FiltroDeAssinaturaDoGateway extends OncePerRequestFilter {
    private final ValidadorDeAssinaturaDoGateway validador;
    private final ObjectMapper mapeador;
    private final int limiteDoCorpo;

    public FiltroDeAssinaturaDoGateway(
            ValidadorDeAssinaturaDoGateway validador,
            ObjectMapper mapeador, int limiteDoCorpo) {
        this.validador = validador;
        this.mapeador = mapeador;
        this.limiteDoCorpo = limiteDoCorpo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido,
            HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        try {
            PedidoComCorpoReutilizavel reutilizavel =
                    PedidoComCorpoReutilizavel.ler(pedido, limiteDoCorpo);
            String caminho = pedido.getRequestURI()
                    + (pedido.getQueryString() == null ? ""
                            : "?" + pedido.getQueryString());
            String chave = validador.validar(
                    pedido.getHeader(
                            ValidadorDeAssinaturaDoGateway.CABECALHO_DA_CHAVE),
                    pedido.getHeader(
                            ValidadorDeAssinaturaDoGateway.CABECALHO_DO_INSTANTE),
                    pedido.getHeader(
                            ValidadorDeAssinaturaDoGateway.CABECALHO_DO_NONCE),
                    pedido.getHeader(
                            ValidadorDeAssinaturaDoGateway.CABECALHO_DA_ASSINATURA),
                    pedido.getHeader(
                            ValidadorDeAssinaturaDoGateway.CABECALHO_DA_IDEMPOTENCIA),
                    pedido.getMethod(), caminho, reutilizavel.corpo());
            var autenticacao = UsernamePasswordAuthenticationToken.authenticated(
                    chave, null,
                    List.of(new SimpleGrantedAuthority("GATEWAY_CONFIAVEL")));
            var contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(autenticacao);
            SecurityContextHolder.setContext(contexto);
            cadeia.doFilter(reutilizavel, resposta);
        } catch (BadCredentialsException excecao) {
            erro(pedido, resposta, HttpStatus.UNAUTHORIZED,
                    "ASSINATURA_DO_GATEWAY_INVALIDA",
                    "A autenticacao do Gateway e invalida.");
        } catch (RequisicaoDoGatewayRepetida excecao) {
            erro(pedido, resposta, HttpStatus.CONFLICT,
                    "REQUISICAO_DO_GATEWAY_REPETIDA",
                    "A requisicao ja foi recebida.");
        } catch (IdempotenciaDoGatewayReutilizada excecao) {
            erro(pedido, resposta, HttpStatus.CONFLICT,
                    "CHAVE_DE_IDEMPOTENCIA_REUTILIZADA",
                    "A chave de idempotencia ja foi usada com outros dados.");
        } catch (LimiteDoGatewayAtingido excecao) {
            resposta.setHeader("Retry-After", "60");
            erro(pedido, resposta, HttpStatus.TOO_MANY_REQUESTS,
                    "LIMITE_DE_REQUISICOES_ATINGIDO",
                    "Aguarde antes de tentar novamente.");
        } catch (CorpoDoGatewayMuitoGrande excecao) {
            erro(pedido, resposta, HttpStatus.PAYLOAD_TOO_LARGE,
                    "CORPO_DA_REQUISICAO_MUITO_GRANDE",
                    "O corpo excede o limite permitido.");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void erro(HttpServletRequest pedido, HttpServletResponse resposta,
            HttpStatus status, String codigo, String mensagem) throws IOException {
        resposta.setStatus(status.value());
        resposta.setContentType("application/json");
        mapeador.writeValue(resposta.getOutputStream(), new RespostaDeErro(
                codigo, mensagem,
                (String) pedido.getAttribute("identificadorDeCorrelacao"),
                List.of()));
    }
}
