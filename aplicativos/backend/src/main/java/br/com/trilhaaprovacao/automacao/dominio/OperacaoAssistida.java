package br.com.trilhaaprovacao.automacao.dominio;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.Locale;
import java.util.regex.Pattern;

public final class OperacaoAssistida {
    private static final Pattern TIPO_VALIDO = Pattern.compile("^[A-Z][A-Z0-9_]{2,79}$");

    private final UUID identificador;
    private final UUID identificadorDoUsuario;
    private final UUID identificadorDoVinculo;
    private final String tipo;
    private final String resumo;
    private final String propostaCanonica;
    private final String assinatura;
    private final String versoesConsultadas;
    private final String chaveDeIdempotencia;
    private final String hashDaRequisicao;
    private final OffsetDateTime criadoEm;
    private EstadoDaOperacaoAssistida estado;
    private OffsetDateTime expiraEm;
    private OffsetDateTime confirmadaEm;
    private OffsetDateTime aplicadaEm;
    private OffsetDateTime canceladaEm;
    private String falha;
    private String resultado;
    private OffsetDateTime atualizadoEm;
    private long versao;

    private OperacaoAssistida(UUID identificador, UUID usuario, UUID vinculo,
            String tipo, EstadoDaOperacaoAssistida estado, String resumo,
            String propostaCanonica, String assinatura, String versoesConsultadas,
            String chaveDeIdempotencia, String hashDaRequisicao,
            OffsetDateTime expiraEm, OffsetDateTime confirmadaEm,
            OffsetDateTime aplicadaEm, OffsetDateTime canceladaEm, String falha,
            String resultado, OffsetDateTime criadoEm, OffsetDateTime atualizadoEm,
            long versao) {
        this.identificador = Objects.requireNonNull(identificador);
        this.identificadorDoUsuario = Objects.requireNonNull(usuario);
        this.identificadorDoVinculo = vinculo;
        this.tipo = validarTipo(tipo);
        this.estado = Objects.requireNonNull(estado);
        this.resumo = exigirTexto(resumo, "Resumo", 500);
        this.propostaCanonica = exigirJson(propostaCanonica, "Proposta canonica");
        this.assinatura = exigirTexto(assinatura, "Assinatura", 128);
        this.versoesConsultadas = exigirJson(versoesConsultadas, "Versoes consultadas");
        this.chaveDeIdempotencia = exigirTexto(
                chaveDeIdempotencia, "Chave de idempotencia", 160);
        this.hashDaRequisicao = exigirTexto(hashDaRequisicao, "Hash da requisicao", 128);
        this.expiraEm = Objects.requireNonNull(expiraEm);
        this.confirmadaEm = confirmadaEm;
        this.aplicadaEm = aplicadaEm;
        this.canceladaEm = canceladaEm;
        this.falha = falha;
        this.resultado = resultado;
        this.criadoEm = Objects.requireNonNull(criadoEm);
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm);
        this.versao = versao;
        validarCoerencia();
    }

    public static OperacaoAssistida preparar(UUID usuario, UUID vinculo, String tipo,
            String resumo, String propostaCanonica, String assinatura,
            String versoesConsultadas, String chaveDeIdempotencia,
            String hashDaRequisicao, OffsetDateTime expiraEm, OffsetDateTime agora) {
        if (expiraEm == null || !expiraEm.isAfter(agora)) {
            throw new IllegalArgumentException("Expiracao da operacao deve estar no futuro.");
        }
        return new OperacaoAssistida(UUID.randomUUID(), usuario, vinculo, tipo,
                EstadoDaOperacaoAssistida.PREPARADA, resumo, propostaCanonica,
                assinatura, versoesConsultadas, chaveDeIdempotencia,
                hashDaRequisicao, expiraEm, null, null, null, null, null,
                agora, agora, 0);
    }

    public static OperacaoAssistida reconstituir(UUID identificador, UUID usuario,
            UUID vinculo, String tipo, EstadoDaOperacaoAssistida estado, String resumo,
            String propostaCanonica, String assinatura, String versoesConsultadas,
            String chaveDeIdempotencia, String hashDaRequisicao,
            OffsetDateTime expiraEm, OffsetDateTime confirmadaEm,
            OffsetDateTime aplicadaEm, OffsetDateTime canceladaEm, String falha,
            String resultado, OffsetDateTime criadoEm, OffsetDateTime atualizadoEm,
            long versao) {
        return new OperacaoAssistida(identificador, usuario, vinculo, tipo, estado,
                resumo, propostaCanonica, assinatura, versoesConsultadas,
                chaveDeIdempotencia, hashDaRequisicao, expiraEm, confirmadaEm,
                aplicadaEm, canceladaEm, falha, resultado, criadoEm, atualizadoEm,
                versao);
    }

    public void aguardarConfirmacao(OffsetDateTime agora) {
        exigirNaoExpirada(agora);
        if (estado != EstadoDaOperacaoAssistida.PREPARADA) {
            throw new IllegalStateException("Operacao nao esta preparada.");
        }
        estado = EstadoDaOperacaoAssistida.AGUARDANDO_CONFIRMACAO;
        atualizadoEm = agora;
    }

    public void confirmar(String assinaturaInformada, OffsetDateTime agora) {
        exigirNaoExpirada(agora);
        if (estado != EstadoDaOperacaoAssistida.AGUARDANDO_CONFIRMACAO) {
            throw new IllegalStateException("Operacao nao aguarda confirmacao.");
        }
        if (!assinatura.equals(assinaturaInformada)) {
            throw new IllegalArgumentException("Assinatura da operacao divergiu.");
        }
        estado = EstadoDaOperacaoAssistida.CONFIRMADA;
        confirmadaEm = agora;
        atualizadoEm = agora;
    }

    public void aplicar(String resultado, OffsetDateTime agora) {
        exigirNaoExpirada(agora);
        if (estado != EstadoDaOperacaoAssistida.CONFIRMADA) {
            throw new IllegalStateException("Operacao ainda nao foi confirmada.");
        }
        this.resultado = exigirJson(resultado, "Resultado");
        estado = EstadoDaOperacaoAssistida.APLICADA;
        aplicadaEm = agora;
        atualizadoEm = agora;
    }

    public void cancelar(OffsetDateTime agora) {
        if (estado == EstadoDaOperacaoAssistida.APLICADA
                || estado == EstadoDaOperacaoAssistida.FALHOU
                || estado == EstadoDaOperacaoAssistida.EXPIRADA) {
            throw new IllegalStateException("Operacao finalizada nao pode ser cancelada.");
        }
        if (estado != EstadoDaOperacaoAssistida.CANCELADA) {
            estado = EstadoDaOperacaoAssistida.CANCELADA;
            canceladaEm = agora;
            atualizadoEm = agora;
        }
    }

    public void registrarFalha(String descricao, OffsetDateTime agora) {
        if (estado == EstadoDaOperacaoAssistida.APLICADA
                || estado == EstadoDaOperacaoAssistida.CANCELADA
                || estado == EstadoDaOperacaoAssistida.EXPIRADA) {
            throw new IllegalStateException("Operacao finalizada nao aceita falha.");
        }
        falha = exigirTexto(descricao, "Falha", 2000);
        estado = EstadoDaOperacaoAssistida.FALHOU;
        atualizadoEm = agora;
    }

    public void expirar(OffsetDateTime agora) {
        if (estado == EstadoDaOperacaoAssistida.APLICADA
                || estado == EstadoDaOperacaoAssistida.CANCELADA
                || estado == EstadoDaOperacaoAssistida.FALHOU) {
            return;
        }
        estado = EstadoDaOperacaoAssistida.EXPIRADA;
        atualizadoEm = agora;
    }

    public boolean mesmaRequisicao(String hash) {
        return hashDaRequisicao.equals(hash);
    }

    private void exigirNaoExpirada(OffsetDateTime agora) {
        if (!agora.isBefore(expiraEm)) {
            expirar(agora);
            throw new IllegalStateException("Operacao expirada.");
        }
    }

    private void validarCoerencia() {
        if (estado == EstadoDaOperacaoAssistida.CONFIRMADA && confirmadaEm == null) {
            throw new IllegalArgumentException("Operacao confirmada exige momento da confirmacao.");
        }
        if (estado == EstadoDaOperacaoAssistida.APLICADA
                && (confirmadaEm == null || aplicadaEm == null || resultado == null)) {
            throw new IllegalArgumentException("Operacao aplicada exige confirmacao e resultado.");
        }
        if (estado == EstadoDaOperacaoAssistida.CANCELADA && canceladaEm == null) {
            throw new IllegalArgumentException("Operacao cancelada exige momento do cancelamento.");
        }
        if (estado == EstadoDaOperacaoAssistida.FALHOU
                && (falha == null || falha.isBlank())) {
            throw new IllegalArgumentException("Operacao com falha exige descricao.");
        }
    }

    private static String validarTipo(String valor) {
        String normalizado = exigirTexto(valor, "Tipo", 80).toUpperCase(Locale.ROOT);
        if (!TIPO_VALIDO.matcher(normalizado).matches()) {
            throw new IllegalArgumentException("Tipo da operacao possui formato invalido.");
        }
        return normalizado;
    }

    private static String exigirJson(String valor, String campo) {
        return exigirTexto(valor, campo, 1_000_000);
    }

    private static String exigirTexto(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " e obrigatorio.");
        }
        String tratado = valor.trim();
        if (tratado.length() > limite) {
            throw new IllegalArgumentException(campo + " excede o limite permitido.");
        }
        return tratado;
    }

    public UUID identificador() { return identificador; }
    public UUID identificadorDoUsuario() { return identificadorDoUsuario; }
    public UUID identificadorDoVinculo() { return identificadorDoVinculo; }
    public String tipo() { return tipo; }
    public EstadoDaOperacaoAssistida estado() { return estado; }
    public String resumo() { return resumo; }
    public String propostaCanonica() { return propostaCanonica; }
    public String assinatura() { return assinatura; }
    public String versoesConsultadas() { return versoesConsultadas; }
    public String chaveDeIdempotencia() { return chaveDeIdempotencia; }
    public String hashDaRequisicao() { return hashDaRequisicao; }
    public OffsetDateTime expiraEm() { return expiraEm; }
    public OffsetDateTime confirmadaEm() { return confirmadaEm; }
    public OffsetDateTime aplicadaEm() { return aplicadaEm; }
    public OffsetDateTime canceladaEm() { return canceladaEm; }
    public String falha() { return falha; }
    public String resultado() { return resultado; }
    public OffsetDateTime criadoEm() { return criadoEm; }
    public OffsetDateTime atualizadoEm() { return atualizadoEm; }
    public long versao() { return versao; }
}
