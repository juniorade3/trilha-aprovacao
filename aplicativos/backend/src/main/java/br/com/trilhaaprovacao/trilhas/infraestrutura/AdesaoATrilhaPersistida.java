package br.com.trilhaaprovacao.trilhas.infraestrutura;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "adesoes_a_trilhas_publicadas")
public class AdesaoATrilhaPersistida {
    @Id
    private UUID identificador;
    @Column(name = "usuario_id", nullable = false)
    private UUID identificadorDoUsuario;
    @Column(name = "trilha_id", nullable = false)
    private UUID identificadorDaTrilha;
    @Column(name = "aderida_em", nullable = false)
    private OffsetDateTime aderidaEm;
    @Version
    private long versao;

    protected AdesaoATrilhaPersistida() {
    }

    public static AdesaoATrilhaPersistida criar(UUID usuario, UUID trilha) {
        AdesaoATrilhaPersistida adesao = new AdesaoATrilhaPersistida();
        adesao.identificador = UUID.randomUUID();
        adesao.identificadorDoUsuario = usuario;
        adesao.identificadorDaTrilha = trilha;
        adesao.aderidaEm = OffsetDateTime.now();
        return adesao;
    }

    public UUID identificador() {
        return identificador;
    }

    public UUID identificadorDaTrilha() {
        return identificadorDaTrilha;
    }
}
