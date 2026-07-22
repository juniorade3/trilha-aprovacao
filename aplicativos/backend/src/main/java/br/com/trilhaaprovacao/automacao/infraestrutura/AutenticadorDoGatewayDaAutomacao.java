package br.com.trilhaaprovacao.automacao.infraestrutura;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class AutenticadorDoGatewayDaAutomacao {
    private final String chaveEsperada;

    public AutenticadorDoGatewayDaAutomacao(
            @Value("${trilha.automacao.chave-do-gateway:}") String chaveEsperada) {
        this.chaveEsperada = chaveEsperada == null ? "" : chaveEsperada.trim();
    }

    public void autenticar(String chaveInformada) {
        if (chaveEsperada.isBlank() || chaveInformada == null
                || !MessageDigest.isEqual(
                        chaveEsperada.getBytes(StandardCharsets.UTF_8),
                        chaveInformada.getBytes(StandardCharsets.UTF_8))) {
            throw new AccessDeniedException("Gateway nao autorizado.");
        }
    }
}
