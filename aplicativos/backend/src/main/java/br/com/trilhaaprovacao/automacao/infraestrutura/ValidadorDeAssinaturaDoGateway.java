package br.com.trilhaaprovacao.automacao.infraestrutura;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ValidadorDeAssinaturaDoGateway {
    public static final String CABECALHO_DA_CHAVE = "X-Trilha-Chave";
    public static final String CABECALHO_DO_INSTANTE = "X-Trilha-Instante";
    public static final String CABECALHO_DO_NONCE = "X-Trilha-Nonce";
    public static final String CABECALHO_DA_ASSINATURA = "X-Trilha-Assinatura";
    public static final String CABECALHO_DA_IDEMPOTENCIA =
            "X-Chave-De-Idempotencia";
    private static final Pattern NONCE_VALIDO =
            Pattern.compile("^[A-Za-z0-9_-]{22,128}$");
    private static final Pattern ASSINATURA_VALIDA =
            Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Pattern IDENTIFICADOR_DE_CHAVE_VALIDO =
            Pattern.compile("^[A-Za-z0-9._-]{1,80}$");
    static final Duration TOLERANCIA_MINIMA = Duration.ofSeconds(1);
    static final Duration TOLERANCIA_MAXIMA = Duration.ofMinutes(10);
    static final Duration RETENCAO_MAXIMA = Duration.ofDays(30);
    static final int LIMITE_MAXIMO_POR_MINUTO = 10_000;
    static final int QUANTIDADE_MINIMA_DE_BYTES_DO_SEGREDO = 32;
    static final int QUANTIDADE_MAXIMA_DE_BYTES_DO_SEGREDO = 4_096;

    private final JdbcTemplate banco;
    private final String identificadorDaChave;
    private final String segredo;
    private final Duration tolerancia;
    private final int limitePorMinuto;
    private final Duration retencao;

    public ValidadorDeAssinaturaDoGateway(JdbcTemplate banco,
            @Value("${trilha.automacao.identificador-da-chave-do-gateway:"
                    + "gateway-openclaw}") String identificadorDaChave,
            @Value("${trilha.automacao.segredo-do-gateway:}") String segredo,
            @Value("${trilha.automacao.tolerancia-do-gateway:PT2M}")
                    Duration tolerancia,
            @Value("${trilha.automacao.limite-do-gateway-por-minuto:60}")
                    int limitePorMinuto,
            @Value("${trilha.automacao.retencao-de-nonces:PT168H}")
                    Duration retencao,
            @Value("${trilha.automacao.habilitada:false}")
                    boolean automacaoHabilitada) {
        this.banco = banco;
        this.identificadorDaChave = tratar(identificadorDaChave);
        this.segredo = tratar(segredo);
        this.tolerancia = tolerancia;
        this.limitePorMinuto = limitePorMinuto;
        this.retencao = retencao;
        if (automacaoHabilitada) {
            validarConfiguracao();
        }
    }

    @Transactional
    public String validar(String chave, String instante, String nonce,
            String assinatura, String idempotencia, String metodo,
            String caminho, byte[] corpo) {
        validarConfiguracao();
        String chaveTratada = tratar(chave);
        String nonceTratado = tratar(nonce);
        String idempotenciaTratada = tratar(idempotencia);
        String assinaturaTratada = tratar(assinatura);
        if (!constante(identificadorDaChave, chaveTratada)
                || !NONCE_VALIDO.matcher(nonceTratado).matches()
                || !ASSINATURA_VALIDA.matcher(assinaturaTratada).matches()
                || idempotenciaTratada.isBlank()
                || idempotenciaTratada.length() > 160) {
            throw invalida();
        }
        long epoch;
        try {
            epoch = Long.parseLong(instante);
        } catch (RuntimeException excecao) {
            throw invalida();
        }
        Instant agora = Instant.now();
        Instant informado;
        try {
            informado = Instant.ofEpochSecond(epoch);
        } catch (RuntimeException excecao) {
            throw invalida();
        }
        if (Duration.between(informado, agora).abs().compareTo(tolerancia) > 0) {
            throw invalida();
        }
        String hashDoCorpo = sha256(corpo);
        String canonico = "TRILHA-HMAC-V1\n" + chaveTratada + "\n" + epoch
                + "\n" + nonceTratado + "\n" + metodo.toUpperCase() + "\n"
                + caminho + "\n" + hashDoCorpo + "\n"
                + idempotenciaTratada;
        if (!constante(hmac(canonico), assinaturaTratada.toLowerCase())) {
            throw invalida();
        }
        registrar(chaveTratada, nonceTratado, idempotenciaTratada,
                metodo.toUpperCase(), caminho, hashDoCorpo,
                OffsetDateTime.ofInstant(informado, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(agora, ZoneOffset.UTC));
        return chaveTratada;
    }

    private void registrar(String chave, String nonce, String idempotencia,
            String metodo, String caminho, String hashDoCorpo,
            OffsetDateTime instante, OffsetDateTime agora) {
        banco.query("""
                SELECT pg_advisory_xact_lock(
                    hashtextextended('gateway:' || ?, 0))
                """, resultado -> null, chave);
        banco.update("""
                DELETE FROM requisicoes_confiaveis_da_automacao
                WHERE expira_em < ?
                """, agora);
        String nonceHash = sha256(nonce.getBytes(StandardCharsets.UTF_8));
        Integer repetido = banco.queryForObject("""
                SELECT COUNT(*)
                FROM requisicoes_confiaveis_da_automacao
                WHERE identificador_da_chave = ? AND nonce_hash = ?
                """, Integer.class, chave, nonceHash);
        if (repetido != null && repetido > 0) {
            throw new RequisicaoDoGatewayRepetida();
        }
        List<RequisicaoAnterior> anteriores = banco.query("""
                SELECT metodo, hash_do_corpo
                FROM requisicoes_confiaveis_da_automacao
                WHERE identificador_da_chave = ? AND caminho = ?
                  AND chave_de_idempotencia = ?
                ORDER BY recebido_em DESC
                LIMIT 1
                """, (resultado, linha) -> new RequisicaoAnterior(
                        resultado.getString("metodo"),
                        resultado.getString("hash_do_corpo")),
                chave, caminho, idempotencia);
        if (!anteriores.isEmpty()
                && (!constante(anteriores.getFirst().metodo(), metodo)
                    || !constante(anteriores.getFirst().hashDoCorpo(), hashDoCorpo))) {
            throw new IdempotenciaDoGatewayReutilizada();
        }
        Integer quantidade = banco.queryForObject("""
                SELECT COUNT(*)
                FROM requisicoes_confiaveis_da_automacao
                WHERE identificador_da_chave = ? AND recebido_em >= ?
                """, Integer.class, chave, agora.minusMinutes(1));
        if (quantidade != null && quantidade >= limitePorMinuto) {
            throw new LimiteDoGatewayAtingido();
        }
        banco.update("""
                INSERT INTO requisicoes_confiaveis_da_automacao (
                    identificador, identificador_da_chave, nonce_hash,
                    chave_de_idempotencia, metodo, caminho, hash_do_corpo,
                    instante_informado, recebido_em, expira_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), chave, nonceHash, idempotencia,
                metodo, caminho, hashDoCorpo, instante, agora,
                agora.plus(retencao));
    }

    private void validarConfiguracao() {
        if (!IDENTIFICADOR_DE_CHAVE_VALIDO.matcher(
                identificadorDaChave).matches()) {
            throw configuracaoInvalida(
                    "IDENTIFICADOR_DA_CHAVE_DO_GATEWAY_OPENCLAW deve conter "
                            + "de 1 a 80 caracteres seguros.");
        }
        int bytesDoSegredo = segredo.getBytes(StandardCharsets.UTF_8).length;
        if (bytesDoSegredo < QUANTIDADE_MINIMA_DE_BYTES_DO_SEGREDO
                || bytesDoSegredo > QUANTIDADE_MAXIMA_DE_BYTES_DO_SEGREDO) {
            throw configuracaoInvalida(
                    "SEGREDO_DO_GATEWAY_OPENCLAW deve possuir entre "
                            + QUANTIDADE_MINIMA_DE_BYTES_DO_SEGREDO + " e "
                            + QUANTIDADE_MAXIMA_DE_BYTES_DO_SEGREDO
                            + " bytes UTF-8.");
        }
        if (tolerancia == null
                || tolerancia.compareTo(TOLERANCIA_MINIMA) < 0
                || tolerancia.compareTo(TOLERANCIA_MAXIMA) > 0) {
            throw configuracaoInvalida(
                    "TOLERANCIA_DO_GATEWAY_OPENCLAW deve ficar entre PT1S e PT10M.");
        }
        if (limitePorMinuto < 1
                || limitePorMinuto > LIMITE_MAXIMO_POR_MINUTO) {
            throw configuracaoInvalida(
                    "LIMITE_DO_GATEWAY_POR_MINUTO deve ficar entre 1 e "
                            + LIMITE_MAXIMO_POR_MINUTO + ".");
        }
        if (retencao == null || retencao.compareTo(tolerancia) < 0
                || retencao.compareTo(RETENCAO_MAXIMA) > 0) {
            throw configuracaoInvalida(
                    "RETENCAO_DE_NONCES_DO_GATEWAY deve ser maior ou igual "
                            + "a tolerancia e menor ou igual a P30D.");
        }
    }

    private IllegalStateException configuracaoInvalida(String detalhe) {
        return new IllegalStateException(
                "Configuracao de seguranca do Gateway invalida: " + detalhe);
    }

    private String sha256(byte[] valor) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(valor));
        } catch (GeneralSecurityException excecao) {
            throw new IllegalStateException("SHA-256 indisponivel.", excecao);
        }
    }

    private String hmac(String valor) {
        try {
            Mac autenticador = Mac.getInstance("HmacSHA256");
            autenticador.init(new SecretKeySpec(
                    segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(autenticador.doFinal(
                    valor.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException excecao) {
            throw new IllegalStateException("HMAC-SHA-256 indisponivel.", excecao);
        }
    }

    private boolean constante(String esperado, String informado) {
        return esperado != null && informado != null
                && MessageDigest.isEqual(
                        esperado.getBytes(StandardCharsets.UTF_8),
                        informado.getBytes(StandardCharsets.UTF_8));
    }

    private String tratar(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private BadCredentialsException invalida() {
        return new BadCredentialsException("Assinatura do Gateway invalida.");
    }

    private record RequisicaoAnterior(String metodo, String hashDoCorpo) {
    }

    public static final class RequisicaoDoGatewayRepetida
            extends RuntimeException {
        RequisicaoDoGatewayRepetida() { super("Requisicao repetida."); }
    }

    public static final class IdempotenciaDoGatewayReutilizada
            extends RuntimeException {
        IdempotenciaDoGatewayReutilizada() {
            super("Chave de idempotencia reutilizada com outro corpo.");
        }
    }

    public static final class LimiteDoGatewayAtingido
            extends RuntimeException {
        LimiteDoGatewayAtingido() { super("Limite de requisicoes atingido."); }
    }
}
