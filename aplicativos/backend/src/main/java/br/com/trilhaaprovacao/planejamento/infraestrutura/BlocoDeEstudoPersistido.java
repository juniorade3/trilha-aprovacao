package br.com.trilhaaprovacao.planejamento.infraestrutura;

import br.com.trilhaaprovacao.planejamento.dominio.BlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.EstadoDoBlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.TipoDeAtividade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blocos_de_estudo")
public class BlocoDeEstudoPersistido {
    @Id private UUID identificador;
    @Column(name = "plano_id", nullable = false) private UUID identificadorDoPlano;
    @Column(name = "materia_id") private UUID identificadorDaMateria;
    @Column(name = "topico_id") private UUID identificadorDoTopico;
    @Column(nullable = false, length = 200) private String titulo;
    @Enumerated(EnumType.STRING) @Column(name = "tipo_de_atividade", nullable = false)
    private TipoDeAtividade tipoDeAtividade;
    @Column(nullable = false) private LocalDate data;
    @Column(name = "duracao_prevista_em_minutos", nullable = false)
    private int duracaoPrevistaEmMinutos;
    @Column(nullable = false) private int ordem;
    @Column(name = "horario_previsto") private LocalTime horarioPrevisto;
    @Column(length = 2000) private String observacao;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private EstadoDoBlocoDeEstudo estado;
    @Column(name = "criado_em", nullable = false) private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false) private OffsetDateTime atualizadoEm;
    @Version private long versao;

    protected BlocoDeEstudoPersistido() {
    }

    public BlocoDeEstudoPersistido(BlocoDeEstudo bloco) {
        identificador = bloco.identificador();
        identificadorDoPlano = bloco.identificadorDoPlano();
        criadoEm = bloco.criadoEm();
        atualizarDe(bloco);
    }

    public void atualizarDe(BlocoDeEstudo bloco) {
        identificadorDaMateria = bloco.identificadorDaMateria();
        identificadorDoTopico = bloco.identificadorDoTopico();
        titulo = bloco.titulo();
        tipoDeAtividade = bloco.tipoDeAtividade();
        data = bloco.data();
        duracaoPrevistaEmMinutos = bloco.duracaoPrevistaEmMinutos();
        ordem = bloco.ordem();
        horarioPrevisto = bloco.horarioPrevisto();
        observacao = bloco.observacao();
        estado = bloco.estado();
        atualizadoEm = bloco.atualizadoEm();
    }

    public BlocoDeEstudo paraDominio() {
        return new BlocoDeEstudo(identificador, identificadorDoPlano,
                identificadorDaMateria, identificadorDoTopico, titulo,
                tipoDeAtividade, data, duracaoPrevistaEmMinutos, ordem,
                horarioPrevisto, observacao, estado, criadoEm, atualizadoEm, versao);
    }
}
