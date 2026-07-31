package br.com.trilhaaprovacao.importacaoedital.infraestrutura;

import br.com.trilhaaprovacao.importacaoedital.dominio.ProvenienciaDoDado;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "proveniencias_da_importacao_do_edital")
public class ProvenienciaDaImportacaoDoEditalPersistida {
    @Id
    private UUID identificador;
    @Column(name = "importacao_id", nullable = false)
    private UUID identificadorDaImportacao;
    @Column(name = "usuario_id", nullable = false)
    private UUID identificadorDoUsuario;
    @Column(name = "tipo_do_recurso", nullable = false, length = 50)
    private String tipoDoRecurso;
    @Column(name = "recurso_id", nullable = false)
    private UUID identificadorDoRecurso;
    @Column(nullable = false, length = 100)
    private String campo;
    private Integer pagina;
    @Column(length = 300)
    private String secao;
    @Column(length = 1000)
    private String trecho;
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confianca;
    @Column(nullable = false)
    private boolean inferido;
    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    protected ProvenienciaDaImportacaoDoEditalPersistida() {
    }

    public ProvenienciaDaImportacaoDoEditalPersistida(UUID importacao,
            UUID usuario, String tipoDoRecurso, UUID recurso, String campo,
            ProvenienciaDoDado proveniencia, BigDecimal confianca,
            boolean inferido, OffsetDateTime criadoEm) {
        identificador = UUID.randomUUID();
        identificadorDaImportacao = importacao;
        identificadorDoUsuario = usuario;
        this.tipoDoRecurso = tipoDoRecurso;
        identificadorDoRecurso = recurso;
        this.campo = campo;
        pagina = proveniencia == null ? null : proveniencia.pagina();
        secao = proveniencia == null ? null : proveniencia.secao();
        trecho = proveniencia == null ? null : proveniencia.trecho();
        this.confianca = confianca;
        this.inferido = inferido;
        this.criadoEm = criadoEm;
    }

    public UUID identificador() { return identificador; }
    public UUID identificadorDaImportacao() { return identificadorDaImportacao; }
    public UUID identificadorDoUsuario() { return identificadorDoUsuario; }
    public String tipoDoRecurso() { return tipoDoRecurso; }
    public UUID identificadorDoRecurso() { return identificadorDoRecurso; }
    public String campo() { return campo; }
    public Integer pagina() { return pagina; }
    public String secao() { return secao; }
    public String trecho() { return trecho; }
    public BigDecimal confianca() { return confianca; }
    public boolean inferido() { return inferido; }
    public OffsetDateTime criadoEm() { return criadoEm; }
}
