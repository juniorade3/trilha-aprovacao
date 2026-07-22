package br.com.trilhaaprovacao.automacao.infraestrutura;

import br.com.trilhaaprovacao.automacao.dominio.CanalDeIntegracao;
import br.com.trilhaaprovacao.automacao.dominio.EstadoDoVinculoDeCanal;
import br.com.trilhaaprovacao.automacao.dominio.VinculoDeCanal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vinculos_de_canal")
public class VinculoDeCanalPersistido {
    @Id private UUID identificador;
    @Column(name = "usuario_id", nullable = false) private UUID identificadorDoUsuario;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CanalDeIntegracao canal;
    @Column(name = "bot") private Long identificadorDoBot;
    @Column(name = "identificador_externo") private Long identificadorExterno;
    @Column(name = "identificador_do_chat") private Long identificadorDoChat;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private EstadoDoVinculoDeCanal estado;
    @Column(name = "codigo_de_vinculo_hash", nullable = false, length = 128)
    private String codigoDeVinculoHash;
    @Column(name = "codigo_expira_em", nullable = false) private OffsetDateTime codigoExpiraEm;
    @Column(name = "codigo_consumido_em") private OffsetDateTime codigoConsumidoEm;
    @Column(name = "criado_em", nullable = false) private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false) private OffsetDateTime atualizadoEm;
    @Column(name = "revogado_em") private OffsetDateTime revogadoEm;
    @Version private long versao;

    protected VinculoDeCanalPersistido() {
    }

    public VinculoDeCanalPersistido(VinculoDeCanal vinculo) {
        identificador = vinculo.identificador();
        identificadorDoUsuario = vinculo.identificadorDoUsuario();
        canal = vinculo.canal();
        criadoEm = vinculo.criadoEm();
        atualizarDe(vinculo);
    }

    public void atualizarDe(VinculoDeCanal vinculo) {
        identificadorDoBot = vinculo.identificadorDoBot();
        identificadorExterno = vinculo.identificadorExterno();
        identificadorDoChat = vinculo.identificadorDoChat();
        estado = vinculo.estado();
        codigoDeVinculoHash = vinculo.codigoDeVinculoHash();
        codigoExpiraEm = vinculo.codigoExpiraEm();
        codigoConsumidoEm = vinculo.codigoConsumidoEm();
        atualizadoEm = vinculo.atualizadoEm();
        revogadoEm = vinculo.revogadoEm();
    }

    public VinculoDeCanal paraDominio() {
        return VinculoDeCanal.reconstituir(identificador, identificadorDoUsuario,
                canal, identificadorDoBot, identificadorExterno, identificadorDoChat,
                estado, codigoDeVinculoHash, codigoExpiraEm, codigoConsumidoEm,
                criadoEm, atualizadoEm, revogadoEm, versao);
    }

    public UUID identificador() { return identificador; }
    public UUID identificadorDoUsuario() { return identificadorDoUsuario; }
    public EstadoDoVinculoDeCanal estado() { return estado; }
}
