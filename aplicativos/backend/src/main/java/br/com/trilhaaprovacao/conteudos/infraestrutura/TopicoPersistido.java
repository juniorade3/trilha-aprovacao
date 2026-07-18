package br.com.trilhaaprovacao.conteudos.infraestrutura;

import br.com.trilhaaprovacao.conteudos.dominio.TopicoDaMateria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "topicos_da_materia")
public class TopicoPersistido {
    @Id
    private UUID identificador;

    @Column(name = "materia_id", nullable = false)
    private UUID identificadorDaMateria;

    @Column(name = "topico_pai_id")
    private UUID identificadorDoTopicoPai;

    @Column(nullable = false)
    private String nome;

    @Column(name = "nome_normalizado", nullable = false)
    private String nomeNormalizado;

    private String descricao;

    @Column(nullable = false)
    private int ordem;

    @Column(nullable = false)
    private boolean arquivado;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @Version
    private long versao;

    protected TopicoPersistido() {
    }

    public TopicoPersistido(TopicoDaMateria topico) {
        this.identificador = topico.identificador();
        this.identificadorDaMateria = topico.identificadorDaMateria();
        this.criadoEm = topico.criadoEm();
        atualizarDe(topico);
    }

    public void atualizarDe(TopicoDaMateria topico) {
        this.identificadorDoTopicoPai = topico.identificadorDoTopicoPai();
        this.nome = topico.nome();
        this.nomeNormalizado = topico.nomeNormalizado();
        this.descricao = topico.descricao();
        this.ordem = topico.ordem();
        this.arquivado = topico.arquivado();
        this.atualizadoEm = topico.atualizadoEm();
    }

    public TopicoDaMateria paraDominio() {
        return TopicoDaMateria.reconstituir(identificador, identificadorDaMateria,
                identificadorDoTopicoPai, nome, descricao, ordem, arquivado, criadoEm, atualizadoEm, versao);
    }

    public UUID identificador() { return identificador; }
    public UUID identificadorDaMateria() { return identificadorDaMateria; }
    public UUID identificadorDoTopicoPai() { return identificadorDoTopicoPai; }
}
