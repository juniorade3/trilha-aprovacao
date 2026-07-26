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
    @Column(name = "numero_oficial", length = 80)
    private String numeroOficial;
    @Column(name = "descricao_normalizada", columnDefinition = "text")
    private String descricaoNormalizada;
    @Column(name = "importacao_de_edital_id")
    private UUID identificadorDaImportacaoDeEdital;
    @Column(name = "importacao_de_edital_usuario_id")
    private UUID identificadorDoUsuarioDaImportacaoDeEdital;
    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;
    @Version
    private long versao;

    protected ItemDoEditalPersistido() {
    }

    public ItemDoEditalPersistido(ItemDoEdital item) {
        this(item, null);
    }

    public ItemDoEditalPersistido(ItemDoEdital item,
            UUID identificadorDoUsuarioDaImportacao) {
        identificador = item.identificador();
        identificadorDoEdital = item.identificadorDoEdital();
        identificadorDaMateriaDaProva = item.identificadorDaMateriaDaProva();
        criadoEm = item.criadoEm();
        identificadorDoUsuarioDaImportacaoDeEdital =
                identificadorDoUsuarioDaImportacao;
        atualizarDe(item);
    }

    public void atualizarDe(ItemDoEdital item) {
        descricaoOriginal = item.descricaoOriginal();
        identificadorDoItemPai = item.identificadorDoItemPai();
        ordem = item.ordem();
        numeroOficial = item.numeroOficial();
        descricaoNormalizada = item.descricaoNormalizada();
        identificadorDaImportacaoDeEdital =
                item.identificadorDaImportacaoDeEdital();
        atualizadoEm = item.atualizadoEm();
    }

    public ItemDoEdital paraDominio() {
        return new ItemDoEdital(identificador, identificadorDoEdital,
                identificadorDaMateriaDaProva, descricaoOriginal,
                identificadorDoItemPai, ordem, numeroOficial,
                descricaoNormalizada, identificadorDaImportacaoDeEdital,
                criadoEm, atualizadoEm, versao);
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
