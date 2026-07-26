package br.com.trilhaaprovacao.automacao.dominio;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public final class VinculoDeCanal {
    private final UUID identificador;
    private final UUID identificadorDoUsuario;
    private final CanalDeIntegracao canal;
    private final long identificadorDoBot;
    private final OffsetDateTime criadoEm;
    private Long identificadorExterno;
    private Long identificadorDoChat;
    private EstadoDoVinculoDeCanal estado;
    private String codigoDeVinculoHash;
    private OffsetDateTime codigoExpiraEm;
    private OffsetDateTime codigoConsumidoEm;
    private String identificadorDoAgente;
    private String identificadorDaSessao;
    private OffsetDateTime provisionadoEm;
    private OffsetDateTime atualizadoEm;
    private OffsetDateTime revogadoEm;
    private long versao;

    private VinculoDeCanal(UUID identificador, UUID identificadorDoUsuario,
            CanalDeIntegracao canal, long identificadorDoBot, Long identificadorExterno,
            Long identificadorDoChat, EstadoDoVinculoDeCanal estado,
            String codigoDeVinculoHash, OffsetDateTime codigoExpiraEm,
            OffsetDateTime codigoConsumidoEm, String identificadorDoAgente,
            String identificadorDaSessao, OffsetDateTime provisionadoEm,
            OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm, OffsetDateTime revogadoEm, long versao) {
        this.identificador = Objects.requireNonNull(identificador);
        this.identificadorDoUsuario = Objects.requireNonNull(identificadorDoUsuario);
        this.canal = Objects.requireNonNull(canal);
        if (identificadorDoBot <= 0) {
            throw new IllegalArgumentException("Identificador do bot deve ser positivo.");
        }
        this.identificadorDoBot = identificadorDoBot;
        this.identificadorExterno = identificadorExterno;
        this.identificadorDoChat = identificadorDoChat;
        this.estado = Objects.requireNonNull(estado);
        this.codigoDeVinculoHash = codigoDeVinculoHash;
        this.codigoExpiraEm = codigoExpiraEm;
        this.codigoConsumidoEm = codigoConsumidoEm;
        this.identificadorDoAgente = identificadorDoAgente;
        this.identificadorDaSessao = identificadorDaSessao;
        this.provisionadoEm = provisionadoEm;
        this.criadoEm = Objects.requireNonNull(criadoEm);
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm);
        this.revogadoEm = revogadoEm;
        this.versao = versao;
        validarCoerencia();
    }

    public static VinculoDeCanal preparar(UUID usuario, long bot, String codigoHash,
            OffsetDateTime expiraEm, OffsetDateTime agora) {
        if (codigoHash == null || codigoHash.isBlank()) {
            throw new IllegalArgumentException("Hash do codigo de vinculo e obrigatorio.");
        }
        if (expiraEm == null || !expiraEm.isAfter(agora)) {
            throw new IllegalArgumentException("Expiracao do codigo deve estar no futuro.");
        }
        return new VinculoDeCanal(UUID.randomUUID(), usuario, CanalDeIntegracao.TELEGRAM,
                bot, null, null, EstadoDoVinculoDeCanal.PENDENTE, codigoHash,
                expiraEm, null, null, null, null, agora, agora, null, 0);
    }

    public static VinculoDeCanal reconstituir(UUID identificador, UUID usuario,
            CanalDeIntegracao canal, long bot, Long identificadorExterno,
            Long identificadorDoChat, EstadoDoVinculoDeCanal estado,
            String codigoHash, OffsetDateTime codigoExpiraEm,
            OffsetDateTime codigoConsumidoEm, OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm, OffsetDateTime revogadoEm, long versao) {
        return new VinculoDeCanal(identificador, usuario, canal, bot,
                identificadorExterno, identificadorDoChat, estado, codigoHash,
                codigoExpiraEm, codigoConsumidoEm, null, null, null,
                criadoEm, atualizadoEm,
                revogadoEm, versao);
    }

    public static VinculoDeCanal reconstituir(UUID identificador, UUID usuario,
            CanalDeIntegracao canal, long bot, Long identificadorExterno,
            Long identificadorDoChat, EstadoDoVinculoDeCanal estado,
            String codigoHash, OffsetDateTime codigoExpiraEm,
            OffsetDateTime codigoConsumidoEm, String identificadorDoAgente,
            String identificadorDaSessao, OffsetDateTime provisionadoEm,
            OffsetDateTime criadoEm, OffsetDateTime atualizadoEm,
            OffsetDateTime revogadoEm, long versao) {
        return new VinculoDeCanal(identificador, usuario, canal, bot,
                identificadorExterno, identificadorDoChat, estado, codigoHash,
                codigoExpiraEm, codigoConsumidoEm, identificadorDoAgente,
                identificadorDaSessao, provisionadoEm, criadoEm, atualizadoEm,
                revogadoEm, versao);
    }

    public void ativar(long telegram, long chat, OffsetDateTime agora) {
        if (estado != EstadoDoVinculoDeCanal.PENDENTE) {
            throw new IllegalStateException("Somente vinculo pendente pode ser ativado.");
        }
        if (!agora.isBefore(codigoExpiraEm)) {
            expirar(agora);
            throw new IllegalStateException("Codigo de vinculo expirado.");
        }
        if (telegram <= 0 || chat <= 0) {
            throw new IllegalArgumentException("Identificadores do Telegram devem ser positivos.");
        }
        identificadorExterno = telegram;
        identificadorDoChat = chat;
        estado = EstadoDoVinculoDeCanal.ATIVO;
        codigoConsumidoEm = agora;
        atualizadoEm = agora;
    }

    public void revogar(OffsetDateTime agora) {
        if (estado == EstadoDoVinculoDeCanal.REVOGADO) {
            return;
        }
        estado = EstadoDoVinculoDeCanal.REVOGADO;
        revogadoEm = agora;
        atualizadoEm = agora;
    }

    public void expirar(OffsetDateTime agora) {
        if (estado != EstadoDoVinculoDeCanal.PENDENTE) {
            throw new IllegalStateException("Somente vinculo pendente pode expirar.");
        }
        estado = EstadoDoVinculoDeCanal.EXPIRADO;
        atualizadoEm = agora;
    }

    public void registrarProvisionamento(String agente, String sessao,
            OffsetDateTime agora) {
        if (estado != EstadoDoVinculoDeCanal.ATIVO) {
            throw new IllegalStateException(
                    "Somente vinculo ativo pode ser provisionado.");
        }
        identificadorDoAgente = exigirIdentificador(
                agente, "Identificador do agente");
        identificadorDaSessao = exigirIdentificador(
                sessao, "Identificador da sessao");
        provisionadoEm = Objects.requireNonNull(agora);
        atualizadoEm = agora;
    }

    public boolean codigoValidoEm(OffsetDateTime instante) {
        return estado == EstadoDoVinculoDeCanal.PENDENTE
                && codigoDeVinculoHash != null && instante.isBefore(codigoExpiraEm);
    }

    private void validarCoerencia() {
        if (estado == EstadoDoVinculoDeCanal.PENDENTE
                && (codigoDeVinculoHash == null || codigoExpiraEm == null)) {
            throw new IllegalArgumentException("Vinculo pendente exige codigo e expiracao.");
        }
        if (estado == EstadoDoVinculoDeCanal.ATIVO
                && (identificadorExterno == null || identificadorDoChat == null
                        || codigoConsumidoEm == null)) {
            throw new IllegalArgumentException("Vinculo ativo exige identidade do Telegram.");
        }
        if (estado == EstadoDoVinculoDeCanal.ATIVO
                && (identificadorExterno <= 0 || identificadorDoChat <= 0)) {
            throw new IllegalArgumentException(
                    "Vinculo ativo exige identificadores positivos do Telegram.");
        }
        boolean provisionamentoIncompleto = identificadorDoAgente == null
                ^ identificadorDaSessao == null;
        provisionamentoIncompleto = provisionamentoIncompleto
                || ((identificadorDoAgente == null) != (provisionadoEm == null));
        if (provisionamentoIncompleto) {
            throw new IllegalArgumentException(
                    "Provisionamento exige agente, sessao e instante.");
        }
    }

    private String exigirIdentificador(String valor, String campo) {
        if (valor == null || valor.isBlank() || valor.trim().length() > 160) {
            throw new IllegalArgumentException(
                    campo + " deve conter entre 1 e 160 caracteres.");
        }
        return valor.trim();
    }

    public UUID identificador() { return identificador; }
    public UUID identificadorDoUsuario() { return identificadorDoUsuario; }
    public CanalDeIntegracao canal() { return canal; }
    public long identificadorDoBot() { return identificadorDoBot; }
    public Long identificadorExterno() { return identificadorExterno; }
    public Long identificadorDoChat() { return identificadorDoChat; }
    public EstadoDoVinculoDeCanal estado() { return estado; }
    public String codigoDeVinculoHash() { return codigoDeVinculoHash; }
    public OffsetDateTime codigoExpiraEm() { return codigoExpiraEm; }
    public OffsetDateTime codigoConsumidoEm() { return codigoConsumidoEm; }
    public String identificadorDoAgente() { return identificadorDoAgente; }
    public String identificadorDaSessao() { return identificadorDaSessao; }
    public OffsetDateTime provisionadoEm() { return provisionadoEm; }
    public OffsetDateTime criadoEm() { return criadoEm; }
    public OffsetDateTime atualizadoEm() { return atualizadoEm; }
    public OffsetDateTime revogadoEm() { return revogadoEm; }
    public long versao() { return versao; }
}
