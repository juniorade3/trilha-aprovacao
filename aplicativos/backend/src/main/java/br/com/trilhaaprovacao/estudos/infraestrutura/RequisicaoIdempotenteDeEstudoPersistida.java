package br.com.trilhaaprovacao.estudos.infraestrutura;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "requisicoes_idempotentes_de_estudo")
public class RequisicaoIdempotenteDeEstudoPersistida {
    @Id
    private UUID identificador;

    @Column(name = "usuario_id", nullable = false)
    private UUID identificadorDoUsuario;

    @Column(name = "chave_de_idempotencia", nullable = false, length = 160)
    private String chaveDeIdempotencia;

    @Column(name = "hash_da_requisicao", nullable = false, length = 64)
    private String hashDaRequisicao;

    @Column(name = "registro_de_estudo_id", nullable = false)
    private UUID identificadorDoRegistroDeEstudo;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    protected RequisicaoIdempotenteDeEstudoPersistida() {
    }

    public RequisicaoIdempotenteDeEstudoPersistida(
            UUID usuario, String chave, String hash, UUID registro) {
        identificador = UUID.randomUUID();
        identificadorDoUsuario = usuario;
        chaveDeIdempotencia = chave;
        hashDaRequisicao = hash;
        identificadorDoRegistroDeEstudo = registro;
        criadoEm = OffsetDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
    }

    public String hashDaRequisicao() {
        return hashDaRequisicao;
    }

    public UUID identificadorDoRegistroDeEstudo() {
        return identificadorDoRegistroDeEstudo;
    }
}
