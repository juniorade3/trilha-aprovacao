package br.com.trilhaaprovacao.estudos.infraestrutura;

import br.com.trilhaaprovacao.estudos.dominio.MaterialDeEstudo;
import br.com.trilhaaprovacao.estudos.dominio.TipoDeMaterial;
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
@Table(name = "materiais_de_estudo")
public class MaterialDeEstudoPersistido {
    @Id private UUID identificador;
    @Column(name = "usuario_id", nullable = false) private UUID identificadorDoUsuario;
    @Column(nullable = false, length = 200) private String titulo;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TipoDeMaterial tipo;
    @Column(length = 1000) private String descricao;
    @Column(length = 200) private String fonte;
    @Column(length = 2048) private String endereco;
    @Column(name = "duracao_estimada_em_minutos") private Integer duracaoEstimadaEmMinutos;
    @Column(nullable = false) private boolean arquivado;
    @Column(name = "criado_em", nullable = false) private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false) private OffsetDateTime atualizadoEm;
    @Version private long versao;

    protected MaterialDeEstudoPersistido() {
    }

    public MaterialDeEstudoPersistido(MaterialDeEstudo material) {
        identificador = material.identificador();
        identificadorDoUsuario = material.identificadorDoUsuario();
        criadoEm = material.criadoEm();
        atualizarDe(material);
    }

    public void atualizarDe(MaterialDeEstudo material) {
        titulo = material.titulo();
        tipo = material.tipo();
        descricao = material.descricao();
        fonte = material.fonte();
        endereco = material.endereco();
        duracaoEstimadaEmMinutos = material.duracaoEstimadaEmMinutos();
        arquivado = material.arquivado();
        atualizadoEm = material.atualizadoEm();
    }

    public MaterialDeEstudo paraDominio() {
        return new MaterialDeEstudo(identificador, identificadorDoUsuario, titulo,
                tipo, descricao, fonte, endereco, duracaoEstimadaEmMinutos,
                arquivado, criadoEm, atualizadoEm, versao);
    }

    public UUID identificador() {
        return identificador;
    }
}
