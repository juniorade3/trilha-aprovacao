package br.com.trilhaaprovacao.automacao.infraestrutura;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "trilha.automacao", name = "habilitada",
        havingValue = "true")
public class ConfiguracaoDoServidorMcp {

    @Bean
    HttpServletStatelessServerTransport transporteMcp(
            @Value("${trilha.automacao.mcp.origens-permitidas:"
                    + "http://localhost:5173,http://127.0.0.1:5173}")
                    String origens,
            @Value("${trilha.automacao.mcp.hosts-permitidos:"
                    + "localhost:*,127.0.0.1:*,backend:8080,"
                    + "host.docker.internal:8080}") String hosts) {
        var seguranca = DefaultServerTransportSecurityValidator.builder()
                .allowedOrigins(lista(origens)).allowedHosts(lista(hosts)).build();
        return HttpServletStatelessServerTransport.builder()
                .messageEndpoint("/mcp")
                .securityValidator(seguranca)
                .contextExtractor(this::extrairContexto)
                .build();
    }

    @Bean
    ServletRegistrationBean<HttpServletStatelessServerTransport>
            registroDoTransporteMcp(HttpServletStatelessServerTransport transporte) {
        var registro = new ServletRegistrationBean<>(transporte, "/mcp");
        registro.setName("transporteMcpDaTrilha");
        registro.setAsyncSupported(true);
        registro.setLoadOnStartup(1);
        return registro;
    }

    @Bean(destroyMethod = "close")
    McpStatelessSyncServer servidorMcp(
            HttpServletStatelessServerTransport transporte,
            CatalogoDeFerramentasMcp catalogo) {
        var servidor = McpServer.sync(transporte)
                .serverInfo("trilha-aprovacao", "1")
                .instructions("Consulte somente dados do usuario derivado da credencial. "
                        + "Preserve ordem, estados, valores e avisos retornados.")
                .strictToolNameValidation(true)
                .validateToolInputs(true)
                .requestTimeout(Duration.ofSeconds(15));
        catalogo.ferramentas().forEach(servidor::tools);
        return servidor.build();
    }

    private McpTransportContext extrairContexto(HttpServletRequest pedido) {
        Object identidade = pedido.getAttribute(
                FiltroDeCredencialMcp.ATRIBUTO_DA_IDENTIDADE);
        if (!(identidade instanceof IdentidadeDaIntegracaoMcp autenticada)) {
            return McpTransportContext.EMPTY;
        }
        UUID correlacao = uuidOuNovo(pedido.getHeader(
                "X-Identificador-De-Correlacao"));
        ContextoDaChamadaMcp contexto = new ContextoDaChamadaMcp(
                autenticada, correlacao,
                limitar(pedido.getHeader("X-Identificador-Do-Update"), 160));
        return McpTransportContext.create(Map.of(
                CatalogoDeFerramentasMcp.CHAVE_DO_CONTEXTO, contexto));
    }

    private UUID uuidOuNovo(String valor) {
        try {
            return valor == null ? UUID.randomUUID() : UUID.fromString(valor);
        } catch (IllegalArgumentException excecao) {
            return UUID.randomUUID();
        }
    }

    private String limitar(String valor, int limite) {
        if (valor == null || valor.isBlank()) return null;
        String tratado = valor.trim();
        return tratado.length() <= limite
                ? tratado : tratado.substring(0, limite);
    }

    private List<String> lista(String valor) {
        return Arrays.stream(valor.split(","))
                .map(String::trim).filter(item -> !item.isBlank()).toList();
    }
}
