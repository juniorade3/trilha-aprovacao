package br.com.trilhaaprovacao.automacao.infraestrutura;

import br.com.trilhaaprovacao.automacao.dominio.EstadoDaOperacaoAssistida;
import br.com.trilhaaprovacao.automacao.dominio.OperacaoAssistida;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "operacoes_assistidas")
public class OperacaoAssistidaPersistida {
    @Id private UUID identificador;
    @Column(name = "usuario_id", nullable = false) private UUID identificadorDoUsuario;
    @Column(name = "vinculo_id") private UUID identificadorDoVinculo;
    @Column(nullable = false, length = 80) private String tipo;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private EstadoDaOperacaoAssistida estado;
    @Column(nullable = false, length = 500) private String resumo;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proposta_canonica", nullable = false, columnDefinition = "jsonb")
    private String propostaCanonica;
    @Column(nullable = false, length = 128) private String assinatura;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "versoes_consultadas", nullable = false, columnDefinition = "jsonb")
    private String versoesConsultadas;
    @Column(name = "chave_de_idempotencia", nullable = false, length = 160)
    private String chaveDeIdempotencia;
    @Column(name = "hash_da_requisicao", nullable = false, length = 128)
    private String hashDaRequisicao;
    @Column(name = "expira_em", nullable = false) private OffsetDateTime expiraEm;
    @Column(name = "confirmada_em") private OffsetDateTime confirmadaEm;
    @Column(name = "aplicada_em") private OffsetDateTime aplicadaEm;
    @Column(name = "cancelada_em") private OffsetDateTime canceladaEm;
    @Column(length = 2000) private String falha;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb") private String resultado;
    @Column(name = "criado_em", nullable = false) private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false) private OffsetDateTime atualizadoEm;
    @Version private long versao;

    protected OperacaoAssistidaPersistida() {
    }

    public OperacaoAssistidaPersistida(OperacaoAssistida operacao) {
        identificador = operacao.identificador();
        identificadorDoUsuario = operacao.identificadorDoUsuario();
        identificadorDoVinculo = operacao.identificadorDoVinculo();
        tipo = operacao.tipo();
        resumo = operacao.resumo();
        propostaCanonica = operacao.propostaCanonica();
        assinatura = operacao.assinatura();
        versoesConsultadas = operacao.versoesConsultadas();
        chaveDeIdempotencia = operacao.chaveDeIdempotencia();
        hashDaRequisicao = operacao.hashDaRequisicao();
        expiraEm = operacao.expiraEm();
        criadoEm = operacao.criadoEm();
        atualizarDe(operacao);
    }

    public void atualizarDe(OperacaoAssistida operacao) {
        estado = operacao.estado();
        confirmadaEm = operacao.confirmadaEm();
        aplicadaEm = operacao.aplicadaEm();
        canceladaEm = operacao.canceladaEm();
        falha = operacao.falha();
        resultado = operacao.resultado();
        atualizadoEm = operacao.atualizadoEm();
    }

    public OperacaoAssistida paraDominio() {
        return OperacaoAssistida.reconstituir(identificador, identificadorDoUsuario,
                identificadorDoVinculo, tipo, estado, resumo, propostaCanonica,
                assinatura, versoesConsultadas, chaveDeIdempotencia,
                hashDaRequisicao, expiraEm, confirmadaEm, aplicadaEm,
                canceladaEm, falha, resultado, criadoEm, atualizadoEm, versao);
    }
}
