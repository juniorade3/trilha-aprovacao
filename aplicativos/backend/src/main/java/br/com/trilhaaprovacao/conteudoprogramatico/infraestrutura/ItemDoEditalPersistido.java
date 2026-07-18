package br.com.trilhaaprovacao.conteudoprogramatico.infraestrutura;

import br.com.trilhaaprovacao.conteudoprogramatico.dominio.ItemDoEdital;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "itens_do_edital")
public class ItemDoEditalPersistido {
    @Id
    private UUID identificador;
    @Column(name = "edital_id", nullable = false)
    private UUID identificadorDoEdital;
    @Column(name = "materia_da_prova_id", nullable = false)
    private UUID identificadorDaMateriaDaProva;
    @Column(name = "descricao_original", nullable = false, columnDefinition = "text")
    private String descricaoOriginal;
    @Column(name = "item_pai_id")
    private UUID identificadorDoItemPai;
    @Column(nullable = false)
    private int ordem;
    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;
    @Version
    private long versao;

    protected ItemDoEditalPersistido() {
    }

    public ItemDoEditalPersistido(ItemDoEdital item) {
        identificador = item.identificador();
        identificadorDoEdital = item.identificadorDoEdital();
        identificadorDaMateriaDaProva = item.identificadorDaMateriaDaProva();
        criadoEm = item.criadoEm();
        atualizarDe(item);
    }

    public void atualizarDe(ItemDoEdital item) {
        descricaoOriginal = item.descricaoOriginal();
        identificadorDoItemPai = item.identificadorDoItemPai();
        ordem = item.ordem();
        atualizadoEm = item.atualizadoEm();
    }

    public ItemDoEdital paraDominio() {
        return new ItemDoEdital(identificador, identificadorDoEdital,
                identificadorDaMateriaDaProva, descricaoOriginal,
                identificadorDoItemPai, ordem, criadoEm, atualizadoEm, versao);
    }

    public UUID identificador() {
        return identificador;
    }

    public UUID identificadorDoEdital() {
        return identificadorDoEdital;
    }

    public UUID identificadorDaMateriaDaProva() {
        return identificadorDaMateriaDaProva;
    }

    public UUID identificadorDoItemPai() {
        return identificadorDoItemPai;
    }
}
