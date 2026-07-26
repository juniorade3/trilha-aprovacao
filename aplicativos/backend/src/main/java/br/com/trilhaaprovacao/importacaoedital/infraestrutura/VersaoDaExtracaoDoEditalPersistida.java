package br.com.trilhaaprovacao.importacaoedital.infraestrutura;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "versoes_da_extracao_do_edital")
public class VersaoDaExtracaoDoEditalPersistida {
    @Id
    private UUID identificador;
    @Column(name = "importacao_id", nullable = false)
    private UUID identificadorDaImportacao;
    @Column(name = "usuario_id", nullable = false)
    private UUID identificadorDoUsuario;
    @Column(name = "numero_da_versao", nullable = false)
    private int numeroDaVersao;
    @Column(name = "versao_do_contrato", nullable = false, length = 20)
    private String versaoDoContrato;
    @Column(name = "versao_do_extrator", nullable = false, length = 40)
    private String versaoDoExtrator;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_estruturados", nullable = false,
            columnDefinition = "jsonb")
    private String dadosEstruturados;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String problemas;
    @Column(name = "hash_da_extracao", nullable = false, length = 64)
    private String hashDaExtracao;
    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    protected VersaoDaExtracaoDoEditalPersistida() {
    }

    public VersaoDaExtracaoDoEditalPersistida(UUID importacao, UUID usuario,
            int numero, String versaoDoContrato, String versaoDoExtrator,
            String dadosEstruturados, String problemas, String hash,
            OffsetDateTime criadoEm) {
        identificador = UUID.randomUUID();
        identificadorDaImportacao = importacao;
        identificadorDoUsuario = usuario;
        numeroDaVersao = numero;
        this.versaoDoContrato = versaoDoContrato;
        this.versaoDoExtrator = versaoDoExtrator;
        this.dadosEstruturados = dadosEstruturados;
        this.problemas = problemas;
        hashDaExtracao = hash;
        this.criadoEm = criadoEm;
    }

    public UUID identificador() { return identificador; }
    public UUID identificadorDaImportacao() { return identificadorDaImportacao; }
    public UUID identificadorDoUsuario() { return identificadorDoUsuario; }
    public int numeroDaVersao() { return numeroDaVersao; }
    public String versaoDoContrato() { return versaoDoContrato; }
    public String versaoDoExtrator() { return versaoDoExtrator; }
    public String dadosEstruturados() { return dadosEstruturados; }
    public String problemas() { return problemas; }
    public String hashDaExtracao() { return hashDaExtracao; }
    public OffsetDateTime criadoEm() { return criadoEm; }
}
