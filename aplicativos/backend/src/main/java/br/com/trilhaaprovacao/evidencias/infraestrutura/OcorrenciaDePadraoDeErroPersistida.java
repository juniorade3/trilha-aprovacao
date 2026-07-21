package br.com.trilhaaprovacao.evidencias.infraestrutura;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ocorrencias_de_padrao_de_erro")
public class OcorrenciaDePadraoDeErroPersistida {
    @Id private UUID identificador;
    @Column(name = "evidencia_id", nullable = false) private UUID identificadorDaEvidencia;
    @Column(name = "padrao_de_erro_id", nullable = false) private UUID identificadorDoPadrao;
    @Column(name = "quantidade_de_ocorrencias", nullable = false)
    private int quantidadeDeOcorrencias;
    @Column(name = "criado_em", nullable = false) private OffsetDateTime criadoEm;

    protected OcorrenciaDePadraoDeErroPersistida() {
    }

    public OcorrenciaDePadraoDeErroPersistida(
            UUID evidencia, UUID padrao, int quantidade) {
        identificador = UUID.randomUUID();
        identificadorDaEvidencia = evidencia;
        identificadorDoPadrao = padrao;
        quantidadeDeOcorrencias = quantidade;
        criadoEm = OffsetDateTime.now();
    }
}
