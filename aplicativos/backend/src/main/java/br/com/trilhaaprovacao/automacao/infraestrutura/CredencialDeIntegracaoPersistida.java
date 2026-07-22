package br.com.trilhaaprovacao.automacao.infraestrutura;

import br.com.trilhaaprovacao.automacao.dominio.CredencialDeIntegracao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "credenciais_de_integracao")
public class CredencialDeIntegracaoPersistida {
    @Id private UUID identificador;
    @Column(name = "vinculo_id", nullable = false) private UUID identificadorDoVinculo;
    @Column(name = "token_hash", nullable = false, length = 128) private String tokenHash;
    @Column(nullable = false, length = 24) private String prefixo;
    @Column(nullable = false) private String escopos;
    @Column(name = "expira_em", nullable = false) private OffsetDateTime expiraEm;
    @Column(name = "ultimo_uso_em") private OffsetDateTime ultimoUsoEm;
    @Column(name = "revogado_em") private OffsetDateTime revogadoEm;
    @Column(name = "criado_em", nullable = false) private OffsetDateTime criadoEm;
    @Version private long versao;

    protected CredencialDeIntegracaoPersistida() {
    }

    public CredencialDeIntegracaoPersistida(CredencialDeIntegracao credencial) {
        identificador = credencial.identificador();
        identificadorDoVinculo = credencial.identificadorDoVinculo();
        tokenHash = credencial.tokenHash();
        prefixo = credencial.prefixo();
        escopos = credencial.escopos();
        expiraEm = credencial.expiraEm();
        criadoEm = credencial.criadoEm();
        atualizarDe(credencial);
    }

    public void atualizarDe(CredencialDeIntegracao credencial) {
        ultimoUsoEm = credencial.ultimoUsoEm();
        revogadoEm = credencial.revogadoEm();
    }

    public CredencialDeIntegracao paraDominio() {
        return CredencialDeIntegracao.reconstituir(identificador,
                identificadorDoVinculo, tokenHash, prefixo, escopos, expiraEm,
                ultimoUsoEm, revogadoEm, criadoEm, versao);
    }

    public UUID identificador() { return identificador; }
    public UUID identificadorDoVinculo() { return identificadorDoVinculo; }
    public String escopos() { return escopos; }
}
