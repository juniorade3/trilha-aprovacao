package br.com.trilhaaprovacao.planejamento.infraestrutura;

import br.com.trilhaaprovacao.planejamento.dominio.ExecucaoDoBloco;
import br.com.trilhaaprovacao.planejamento.dominio.ResultadoDaExecucao;
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
@Table(name = "execucoes_de_bloco")
public class ExecucaoDoBlocoPersistida {
    @Id private UUID identificador;
    @Column(name = "usuario_id", nullable = false) private UUID identificadorDoUsuario;
    @Column(name = "bloco_id", nullable = false) private UUID identificadorDoBloco;
    @Column(name = "iniciada_em", nullable = false) private OffsetDateTime iniciadaEm;
    @Column(name = "encerrada_em") private OffsetDateTime encerradaEm;
    @Column(name = "duracao_executada_em_minutos") private Integer duracaoExecutadaEmMinutos;
    @Enumerated(EnumType.STRING) @Column(length = 40) private ResultadoDaExecucao resultado;
    @Column(length = 2000) private String observacao;
    @Column(name = "registro_de_estudo_id") private UUID identificadorDoRegistroDeEstudo;
    @Column(name = "criado_em", nullable = false) private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false) private OffsetDateTime atualizadoEm;
    @Version private long versao;

    protected ExecucaoDoBlocoPersistida() {
    }

    public ExecucaoDoBlocoPersistida(ExecucaoDoBloco execucao) {
        identificador = execucao.identificador();
        identificadorDoUsuario = execucao.identificadorDoUsuario();
        identificadorDoBloco = execucao.identificadorDoBloco();
        criadaDe(execucao);
    }

    public void atualizarDe(ExecucaoDoBloco execucao) {
        identificadorDoUsuario = execucao.identificadorDoUsuario();
        identificadorDoBloco = execucao.identificadorDoBloco();
        iniciadaEm = execucao.iniciadaEm();
        encerradaEm = execucao.encerradaEm();
        duracaoExecutadaEmMinutos = execucao.duracaoExecutadaEmMinutos();
        resultado = execucao.resultado();
        observacao = execucao.observacao();
        identificadorDoRegistroDeEstudo = execucao.identificadorDoRegistroDeEstudo();
        atualizadoEm = execucao.atualizadoEm();
    }

    private void criadaDe(ExecucaoDoBloco execucao) {
        criadoEm = execucao.criadoEm();
        atualizarDe(execucao);
    }

    public ExecucaoDoBloco paraDominio() {
        return new ExecucaoDoBloco(identificador, identificadorDoUsuario,
                identificadorDoBloco, iniciadaEm, encerradaEm,
                duracaoExecutadaEmMinutos, resultado, observacao,
                identificadorDoRegistroDeEstudo, criadoEm, atualizadoEm, versao);
    }
}
