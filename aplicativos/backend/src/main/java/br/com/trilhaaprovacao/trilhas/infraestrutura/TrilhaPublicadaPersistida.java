package br.com.trilhaaprovacao.trilhas.infraestrutura;

import br.com.trilhaaprovacao.trilhas.dominio.TrilhaPublicada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "trilhas_publicadas")
public class TrilhaPublicadaPersistida {
    @Id
    private UUID identificador;
    @Column(nullable = false, unique = true, length = 80)
    private String codigo;
    @Column(nullable = false, length = 200)
    private String nome;
    @Column(name = "versao_publicada", nullable = false, length = 40)
    private String versaoPublicada;
    @Column(length = 1000)
    private String descricao;
    @Column(name = "publicada_em", nullable = false)
    private OffsetDateTime publicadaEm;
    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;
    @Version
    private long versao;

    protected TrilhaPublicadaPersistida() {
    }

    public TrilhaPublicada paraDominio() {
        return new TrilhaPublicada(identificador, codigo, nome, versaoPublicada, descricao,
                publicadaEm, criadoEm, atualizadoEm, versao);
    }

    public UUID identificador() {
        return identificador;
    }
}
