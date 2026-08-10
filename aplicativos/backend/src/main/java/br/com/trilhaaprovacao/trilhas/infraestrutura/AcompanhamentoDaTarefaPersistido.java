package br.com.trilhaaprovacao.trilhas.infraestrutura;

import br.com.trilhaaprovacao.trilhas.dominio.AcompanhamentoDaTarefa;
import br.com.trilhaaprovacao.trilhas.dominio.SituacaoDoAcompanhamentoDaTarefa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "acompanhamentos_de_tarefas_da_trilha")
public class AcompanhamentoDaTarefaPersistido {
    @Id
    private UUID identificador;
    @Column(name = "adesao_id", nullable = false)
    private UUID identificadorDaAdesao;
    @Column(name = "tarefa_id", nullable = false)
    private UUID identificadorDaTarefa;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SituacaoDoAcompanhamentoDaTarefa situacao;
    @Column(length = 2000)
    private String observacao;
    @Column(name = "concluida_em")
    private OffsetDateTime concluidaEm;
    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;
    @Version
    private long versao;

    protected AcompanhamentoDaTarefaPersistido() {
    }

    public AcompanhamentoDaTarefaPersistido(AcompanhamentoDaTarefa acompanhamento) {
        identificador = acompanhamento.identificador();
        identificadorDaAdesao = acompanhamento.identificadorDaAdesao();
        identificadorDaTarefa = acompanhamento.identificadorDaTarefa();
        criadoEm = acompanhamento.criadoEm();
        atualizarDe(acompanhamento);
    }

    public void atualizarDe(AcompanhamentoDaTarefa acompanhamento) {
        situacao = acompanhamento.situacao();
        observacao = acompanhamento.observacao();
        concluidaEm = acompanhamento.concluidaEm();
        atualizadoEm = acompanhamento.atualizadoEm();
    }

    public AcompanhamentoDaTarefa paraDominio() {
        return new AcompanhamentoDaTarefa(identificador, identificadorDaAdesao,
                identificadorDaTarefa, situacao, observacao, concluidaEm, criadoEm,
                atualizadoEm, versao);
    }
}
