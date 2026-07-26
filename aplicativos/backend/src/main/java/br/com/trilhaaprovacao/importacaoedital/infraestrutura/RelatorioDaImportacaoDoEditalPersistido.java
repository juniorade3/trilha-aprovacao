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
@Table(name = "relatorios_da_importacao_do_edital")
public class RelatorioDaImportacaoDoEditalPersistido {
    @Id
    private UUID identificador;
    @Column(name = "importacao_id", nullable = false)
    private UUID identificadorDaImportacao;
    @Column(name = "usuario_id", nullable = false)
    private UUID identificadorDoUsuario;
    @Column(name = "operacao_assistida_id", nullable = false)
    private UUID identificadorDaOperacaoAssistida;
    @Column(name = "concurso_id", nullable = false)
    private UUID identificadorDoConcurso;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String dados;
    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    protected RelatorioDaImportacaoDoEditalPersistido() {
    }

    public RelatorioDaImportacaoDoEditalPersistido(UUID importacao,
            UUID usuario, UUID operacao, UUID concurso, String dados,
            OffsetDateTime criadoEm) {
        this(UUID.randomUUID(), importacao, usuario, operacao, concurso,
                dados, criadoEm);
    }

    public RelatorioDaImportacaoDoEditalPersistido(UUID identificador,
            UUID importacao, UUID usuario, UUID operacao, UUID concurso,
            String dados, OffsetDateTime criadoEm) {
        this.identificador = identificador;
        identificadorDaImportacao = importacao;
        identificadorDoUsuario = usuario;
        identificadorDaOperacaoAssistida = operacao;
        identificadorDoConcurso = concurso;
        this.dados = dados;
        this.criadoEm = criadoEm;
    }

    public UUID identificador() { return identificador; }
    public UUID identificadorDaImportacao() { return identificadorDaImportacao; }
    public UUID identificadorDoUsuario() { return identificadorDoUsuario; }
    public UUID identificadorDaOperacaoAssistida() {
        return identificadorDaOperacaoAssistida;
    }
    public UUID identificadorDoConcurso() { return identificadorDoConcurso; }
    public String dados() { return dados; }
    public OffsetDateTime criadoEm() { return criadoEm; }
}
