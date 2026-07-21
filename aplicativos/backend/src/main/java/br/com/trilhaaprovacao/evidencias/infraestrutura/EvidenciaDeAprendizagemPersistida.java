package br.com.trilhaaprovacao.evidencias.infraestrutura;

import br.com.trilhaaprovacao.evidencias.dominio.EvidenciaDeAprendizagem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "evidencias_de_aprendizagem")
public class EvidenciaDeAprendizagemPersistida {
    @Id private UUID identificador;
    @Column(name = "registro_de_estudo_id", nullable = false, unique = true)
    private UUID identificadorDoRegistroDeEstudo;
    @Column(name = "quantidade_de_questoes") private Integer quantidadeDeQuestoes;
    @Column(name = "quantidade_de_acertos") private Integer quantidadeDeAcertos;
    @Column(name = "nivel_de_recordacao") private Integer nivelDeRecordacao;
    @Column(name = "dificuldade_percebida") private Integer dificuldadePercebida;
    @Column(name = "criado_em", nullable = false) private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false) private OffsetDateTime atualizadoEm;
    @Version private long versao;

    protected EvidenciaDeAprendizagemPersistida() {
    }

    public EvidenciaDeAprendizagemPersistida(EvidenciaDeAprendizagem evidencia) {
        identificador = evidencia.identificador();
        identificadorDoRegistroDeEstudo = evidencia.identificadorDoRegistroDeEstudo();
        quantidadeDeQuestoes = evidencia.quantidadeDeQuestoes();
        quantidadeDeAcertos = evidencia.quantidadeDeAcertos();
        nivelDeRecordacao = evidencia.nivelDeRecordacao();
        dificuldadePercebida = evidencia.dificuldadePercebida();
        criadoEm = evidencia.criadoEm();
        atualizadoEm = evidencia.atualizadoEm();
    }

    public EvidenciaDeAprendizagem paraDominio(
            List<br.com.trilhaaprovacao.evidencias.dominio.DadosDoPadraoDeErro> padroes) {
        return new EvidenciaDeAprendizagem(identificador, identificadorDoRegistroDeEstudo,
                quantidadeDeQuestoes, quantidadeDeAcertos, nivelDeRecordacao,
                dificuldadePercebida, padroes, criadoEm, atualizadoEm, versao);
    }

    public UUID identificador() {
        return identificador;
    }

    public UUID identificadorDoRegistroDeEstudo() {
        return identificadorDoRegistroDeEstudo;
    }
}
