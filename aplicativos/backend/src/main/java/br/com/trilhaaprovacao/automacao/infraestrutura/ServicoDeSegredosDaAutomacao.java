package br.com.trilhaaprovacao.automacao.infraestrutura;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ServicoDeSegredosDaAutomacao {
    private static final char[] ALFABETO_DO_CODIGO =
            "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private final SecureRandom aleatorio = new SecureRandom();
    private final String segredoDeHash;

    public ServicoDeSegredosDaAutomacao(
            @Value("${trilha.automacao.segredo-de-hash:}") String segredoDeHash) {
        this.segredoDeHash = segredoDeHash == null ? "" : segredoDeHash.trim();
    }

    public String gerarCodigoDeVinculo() {
        StringBuilder codigo = new StringBuilder(10);
        for (int indice = 0; indice < 10; indice++) {
            codigo.append(ALFABETO_DO_CODIGO[aleatorio.nextInt(ALFABETO_DO_CODIGO.length)]);
        }
        return codigo.toString();
    }

    public String gerarToken() {
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        return "mcp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public boolean configurado() {
        return !segredoDeHash.isBlank();
    }

    public String hash(String valor) {
        if (segredoDeHash.isBlank()) {
            throw new IllegalStateException(
                    "Segredo de hash da automacao nao foi configurado.");
        }
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Valor para hash e obrigatorio.");
        }
        try {
            Mac autenticador = Mac.getInstance("HmacSHA256");
            autenticador.init(new SecretKeySpec(
                    segredoDeHash.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] resultado = autenticador.doFinal(valor.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(resultado);
        } catch (GeneralSecurityException excecao) {
            throw new IllegalStateException("HMAC-SHA-256 indisponivel.", excecao);
        }
    }

    public boolean corresponde(String esperado, String informado) {
        if (esperado == null || informado == null) {
            return false;
        }
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8),
                informado.getBytes(StandardCharsets.UTF_8));
    }
}
