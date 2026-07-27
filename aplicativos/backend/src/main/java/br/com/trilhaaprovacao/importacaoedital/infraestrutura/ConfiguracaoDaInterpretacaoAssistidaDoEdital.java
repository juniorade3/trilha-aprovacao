package br.com.trilhaaprovacao.importacaoedital.infraestrutura;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfiguracaoDaInterpretacaoAssistidaDoEdital {
    static final String PROVEDOR_RESPONSES_API = "responses-api";
    static final String PROVEDOR_CODEX_CLI = "codex-cli";

    private final boolean habilitada;
    private final String provedor;
    private final URI urlBase;
    private final String chaveDaApi;
    private final String modelo;
    private final Duration timeout;
    private final Path executavelDoCodex;
    private final Path codexHome;
    private final int limiteDePaginasRenderizadas;
    private final int dpiDaRenderizacao;

    public ConfiguracaoDaInterpretacaoAssistidaDoEdital(
            @Value("${trilha.importacao-de-edital.interpretacao-assistida.habilitada:false}")
            boolean habilitada,
            @Value("${trilha.importacao-de-edital.interpretacao-assistida.provedor:responses-api}")
            String provedor,
            @Value("${trilha.importacao-de-edital.interpretacao-assistida.url-base:https://api.openai.com/v1}")
            URI urlBase,
            @Value("${trilha.importacao-de-edital.interpretacao-assistida.chave-da-api:}")
            String chaveDaApi,
            @Value("${trilha.importacao-de-edital.interpretacao-assistida.modelo:gpt-5.6-sol}")
            String modelo,
            @Value("${trilha.importacao-de-edital.interpretacao-assistida.timeout:PT180S}")
            Duration timeout,
            @Value("${trilha.importacao-de-edital.interpretacao-assistida.executavel-do-codex:}")
            String executavelDoCodex,
            @Value("${trilha.importacao-de-edital.interpretacao-assistida.codex-home:}")
            String codexHome,
            @Value("${trilha.importacao-de-edital.interpretacao-assistida.limite-de-paginas-renderizadas:20}")
            int limiteDePaginasRenderizadas,
            @Value("${trilha.importacao-de-edital.interpretacao-assistida.dpi-da-renderizacao:144}")
            int dpiDaRenderizacao) {
        String provedorNormalizado = provedor == null ? ""
                : provedor.strip().toLowerCase(java.util.Locale.ROOT);
        if (!urlSegura(urlBase)
                || (!PROVEDOR_RESPONSES_API.equals(provedorNormalizado)
                        && !PROVEDOR_CODEX_CLI.equals(provedorNormalizado))
                || modelo == null || modelo.isBlank()
                || timeout == null || timeout.isZero() || timeout.isNegative()
                || limiteDePaginasRenderizadas < 1
                || limiteDePaginasRenderizadas > 100
                || dpiDaRenderizacao < 72 || dpiDaRenderizacao > 200) {
            throw new IllegalArgumentException(
                    "Configuracao da interpretacao assistida invalida.");
        }
        this.habilitada = habilitada;
        this.provedor = provedorNormalizado;
        this.urlBase = urlBase;
        this.chaveDaApi = chaveDaApi == null ? "" : chaveDaApi.strip();
        this.modelo = modelo.strip();
        this.timeout = timeout;
        this.executavelDoCodex = caminhoAbsoluto(executavelDoCodex);
        this.codexHome = caminhoAbsoluto(codexHome);
        this.limiteDePaginasRenderizadas = limiteDePaginasRenderizadas;
        this.dpiDaRenderizacao = dpiDaRenderizacao;
    }

    boolean respostasApiDisponivel() {
        return habilitada && PROVEDOR_RESPONSES_API.equals(provedor)
                && !chaveDaApi.isBlank();
    }

    boolean codexCliDisponivel() {
        if (!habilitada || !PROVEDOR_CODEX_CLI.equals(provedor)
                || executavelDoCodex == null || codexHome == null
                || !Files.isRegularFile(executavelDoCodex)
                || !Files.isExecutable(executavelDoCodex)
                || !Files.isDirectory(codexHome)
                || !Files.isReadable(codexHome)) {
            return false;
        }
        Path autenticacao = codexHome.resolve("auth.json");
        try {
            return Files.isRegularFile(autenticacao)
                    && Files.isReadable(autenticacao)
                    && Files.size(autenticacao) > 0;
        } catch (java.io.IOException excecao) {
            return false;
        }
    }

    URI urlBase() {
        return urlBase;
    }

    String chaveDaApi() {
        return chaveDaApi;
    }

    String modelo() {
        return modelo;
    }

    Duration timeout() {
        return timeout;
    }

    Path executavelDoCodex() {
        return executavelDoCodex;
    }

    Path codexHome() {
        return codexHome;
    }

    int limiteDePaginasRenderizadas() {
        return limiteDePaginasRenderizadas;
    }

    int dpiDaRenderizacao() {
        return dpiDaRenderizacao;
    }

    private static Path caminhoAbsoluto(String caminho) {
        if (caminho == null || caminho.isBlank()) return null;
        try {
            Path normalizado = Path.of(caminho.strip()).normalize();
            return normalizado.isAbsolute() ? normalizado : null;
        } catch (InvalidPathException excecao) {
            return null;
        }
    }

    private static boolean urlSegura(URI url) {
        if (url == null || url.getScheme() == null || url.getHost() == null
                || url.getUserInfo() != null || url.getQuery() != null
                || url.getFragment() != null) {
            return false;
        }
        if ("https".equalsIgnoreCase(url.getScheme())) return true;
        if (!"http".equalsIgnoreCase(url.getScheme())) return false;
        String host = url.getHost();
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
    }
}
