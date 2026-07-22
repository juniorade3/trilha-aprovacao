package br.com.trilhaaprovacao.automacao.dominio;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public final class CredencialDeIntegracao {
    private final UUID identificador;
    private final UUID identificadorDoVinculo;
    private final String tokenHash;
    private final String prefixo;
    private final String escopos;
    private final OffsetDateTime expiraEm;
    private final OffsetDateTime criadoEm;
    private OffsetDateTime ultimoUsoEm;
    private OffsetDateTime revogadoEm;
    private long versao;

    private CredencialDeIntegracao(UUID identificador, UUID vinculo, String tokenHash,
            String prefixo, String escopos, OffsetDateTime expiraEm,
            OffsetDateTime ultimoUsoEm, OffsetDateTime revogadoEm,
            OffsetDateTime criadoEm, long versao) {
        this.identificador = Objects.requireNonNull(identificador);
        this.identificadorDoVinculo = Objects.requireNonNull(vinculo);
        this.tokenHash = exigirTexto(tokenHash, "Hash do token");
        this.prefixo = exigirTexto(prefixo, "Prefixo do token");
        this.escopos = exigirTexto(escopos, "Escopos");
        this.expiraEm = Objects.requireNonNull(expiraEm);
        this.ultimoUsoEm = ultimoUsoEm;
        this.revogadoEm = revogadoEm;
        this.criadoEm = Objects.requireNonNull(criadoEm);
        this.versao = versao;
        if (!expiraEm.isAfter(criadoEm)) {
            throw new IllegalArgumentException("Credencial deve expirar depois da criacao.");
        }
    }

    public static CredencialDeIntegracao criar(UUID vinculo, String tokenHash,
            String prefixo, String escopos, OffsetDateTime expiraEm,
            OffsetDateTime agora) {
        return new CredencialDeIntegracao(UUID.randomUUID(), vinculo, tokenHash,
                prefixo, escopos, expiraEm, null, null, agora, 0);
    }

    public static CredencialDeIntegracao reconstituir(UUID identificador, UUID vinculo,
            String tokenHash, String prefixo, String escopos, OffsetDateTime expiraEm,
            OffsetDateTime ultimoUsoEm, OffsetDateTime revogadoEm,
            OffsetDateTime criadoEm, long versao) {
        return new CredencialDeIntegracao(identificador, vinculo, tokenHash, prefixo,
                escopos, expiraEm, ultimoUsoEm, revogadoEm, criadoEm, versao);
    }

    public void registrarUso(OffsetDateTime agora) {
        if (!ativaEm(agora)) {
            throw new IllegalStateException("Credencial inativa.");
        }
        ultimoUsoEm = agora;
    }

    public void revogar(OffsetDateTime agora) {
        if (revogadoEm == null) {
            revogadoEm = agora;
        }
    }

    public boolean ativaEm(OffsetDateTime instante) {
        return revogadoEm == null && instante.isBefore(expiraEm);
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " e obrigatorio.");
        }
        return valor.trim();
    }

    public UUID identificador() { return identificador; }
    public UUID identificadorDoVinculo() { return identificadorDoVinculo; }
    public String tokenHash() { return tokenHash; }
    public String prefixo() { return prefixo; }
    public String escopos() { return escopos; }
    public OffsetDateTime expiraEm() { return expiraEm; }
    public OffsetDateTime ultimoUsoEm() { return ultimoUsoEm; }
    public OffsetDateTime revogadoEm() { return revogadoEm; }
    public OffsetDateTime criadoEm() { return criadoEm; }
    public long versao() { return versao; }
}
