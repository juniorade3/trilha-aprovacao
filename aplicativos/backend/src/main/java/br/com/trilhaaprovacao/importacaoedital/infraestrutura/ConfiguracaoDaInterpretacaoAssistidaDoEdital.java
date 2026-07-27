package br.com.trilhaaprovacao.importacaoedital.infraestrutura;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfiguracaoDaInterpretacaoAssistidaDoEdital {
    private final boolean habilitada;
    private final URI urlBase;
    private final String chaveDaApi;
    private final String modelo;
    private final Duration timeout;

    public ConfiguracaoDaInterpretacaoAssistidaDoEdital(
            @Value("${trilha.importacao-de-edital.interpretacao-assistida.habilitada:false}")
            boolean habilitada,
            @Value("${trilha.importacao-de-edital.interpretacao-assistida.url-base:https://api.openai.com/v1}")
            URI urlBase,
            @Value("${trilha.importacao-de-edital.interpretacao-assistida.chave-da-api:}")
            String chaveDaApi,
            @Value("${trilha.importacao-de-edital.interpretacao-assistida.modelo:gpt-5.6-sol}")
            String modelo,
            @Value("${trilha.importacao-de-edital.interpretacao-assistida.timeout:PT180S}")
            Duration timeout) {
        if (!urlSegura(urlBase)
                || modelo == null || modelo.isBlank()
                || timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(
                    "Configuracao da interpretacao assistida invalida.");
        }
        this.habilitada = habilitada;
        this.urlBase = urlBase;
        this.chaveDaApi = chaveDaApi == null ? "" : chaveDaApi.strip();
        this.modelo = modelo.strip();
        this.timeout = timeout;
    }

    public boolean disponivel() {
        return habilitada && !chaveDaApi.isBlank();
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
