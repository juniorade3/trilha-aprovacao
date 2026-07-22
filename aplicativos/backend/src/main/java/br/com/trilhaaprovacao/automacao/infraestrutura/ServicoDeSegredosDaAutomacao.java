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
    static final int QUANTIDADE_MINIMA_DE_BYTES_DO_SEGREDO = 32;
    private static final char[] ALFABETO_DO_CODIGO =
            "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private final SecureRandom aleatorio = new SecureRandom();
    private final String segredoDeHash;

    public ServicoDeSegredosDaAutomacao(
            @Value("${trilha.automacao.segredo-de-hash:}") String segredoDeHash,
            @Value("${trilha.automacao.habilitada:false}")
                    boolean automacaoHabilitada) {
        this.segredoDeHash = segredoDeHash == null ? "" : segredoDeHash.trim();
        if (automacaoHabilitada) {
            exigirSegredoForte();
        }
    }

    public String gerarCodigoDeVinculo() {
        StringBuilder codigo = new StringBuilder(10);
        for (int indice = 0; indice < 10; indice++) {
            codigo.append(ALFABETO_DO_CODIGO[aleatorio.nextInt(ALFABETO_DO_CODIGO.length)]);
        }
        return codigo.toString();
    }

    public String derivarCodigoDeConfirmacao(String material) {
        byte[] bytes = hmac("confirmacao:" + material);
        StringBuilder codigo = new StringBuilder(8);
        for (int indice = 0; indice < 8; indice++) {
            codigo.append(ALFABETO_DO_CODIGO[
                    Byte.toUnsignedInt(bytes[indice]) % ALFABETO_DO_CODIGO.length]);
        }
        return codigo.toString();
    }

    public String gerarToken() {
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        return "mcp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String derivarToken(String material) {
        if (material == null || material.isBlank()) {
            throw new IllegalArgumentException(
                    "Material para derivacao do token e obrigatorio.");
        }
        return "mcp_" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(material));
    }

    public boolean configurado() {
        return quantidadeDeBytesDoSegredo() >= QUANTIDADE_MINIMA_DE_BYTES_DO_SEGREDO;
    }

    public String hash(String valor) {
        exigirSegredoForte();
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Valor para hash e obrigatorio.");
        }
        return java.util.HexFormat.of().formatHex(hmac(valor));
    }

    private byte[] hmac(String valor) {
        exigirSegredoForte();
        try {
            Mac autenticador = Mac.getInstance("HmacSHA256");
            autenticador.init(new SecretKeySpec(
                    segredoDeHash.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return autenticador.doFinal(valor.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException excecao) {
            throw new IllegalStateException("HMAC-SHA-256 indisponivel.", excecao);
        }
    }

    private void exigirSegredoForte() {
        if (quantidadeDeBytesDoSegredo()
                < QUANTIDADE_MINIMA_DE_BYTES_DO_SEGREDO) {
            throw new IllegalStateException(
                    "SEGREDO_DE_HASH_DA_AUTOMACAO deve possuir pelo menos "
                            + QUANTIDADE_MINIMA_DE_BYTES_DO_SEGREDO
                            + " bytes UTF-8 quando a automacao estiver habilitada.");
        }
    }

    private int quantidadeDeBytesDoSegredo() {
        return segredoDeHash.getBytes(StandardCharsets.UTF_8).length;
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
