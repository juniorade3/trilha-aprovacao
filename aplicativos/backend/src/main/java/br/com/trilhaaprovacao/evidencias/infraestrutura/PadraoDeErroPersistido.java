package br.com.trilhaaprovacao.evidencias.infraestrutura;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "padroes_de_erro")
public class PadraoDeErroPersistido {
    @Id private UUID identificador;
    @Column(name = "usuario_id", nullable = false) private UUID identificadorDoUsuario;
    @Column(name = "topico_id", nullable = false) private UUID identificadorDoTopico;
    @Column(nullable = false, length = 200) private String descricao;
    @Column(name = "descricao_normalizada", nullable = false, length = 200)
    private String descricaoNormalizada;
    @Column(name = "criado_em", nullable = false) private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false) private OffsetDateTime atualizadoEm;
    @Version private long versao;

    protected PadraoDeErroPersistido() {
    }

    public PadraoDeErroPersistido(UUID usuario, UUID topico, String descricao,
            String normalizada) {
        identificador = UUID.randomUUID();
        identificadorDoUsuario = usuario;
        identificadorDoTopico = topico;
        this.descricao = descricao;
        descricaoNormalizada = normalizada;
        criadoEm = OffsetDateTime.now();
        atualizadoEm = criadoEm;
    }

    public UUID identificador() {
        return identificador;
    }

    public String descricao() {
        return descricao;
    }
}
