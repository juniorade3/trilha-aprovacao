package br.com.trilhaaprovacao.planejamento.infraestrutura;

import br.com.trilhaaprovacao.planejamento.dominio.EstadoDoPlanoSemanal;
import br.com.trilhaaprovacao.planejamento.dominio.PlanoSemanal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "planos_semanais")
public class PlanoSemanalPersistido {
    @Id private UUID identificador;
    @Column(name = "usuario_id", nullable = false) private UUID identificadorDoUsuario;
    @Column(name = "data_inicial", nullable = false) private LocalDate dataInicial;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private EstadoDoPlanoSemanal estado;
    @Column(name = "criado_em", nullable = false) private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false) private OffsetDateTime atualizadoEm;
    @Version private long versao;

    protected PlanoSemanalPersistido() {
    }

    public PlanoSemanalPersistido(PlanoSemanal plano) {
        identificador = plano.identificador();
        identificadorDoUsuario = plano.identificadorDoUsuario();
        dataInicial = plano.dataInicial();
        criadoEm = plano.criadoEm();
        atualizarDe(plano);
    }

    public void atualizarDe(PlanoSemanal plano) {
        estado = plano.estado();
        atualizadoEm = plano.atualizadoEm();
    }

    public PlanoSemanal paraDominio() {
        return new PlanoSemanal(identificador, identificadorDoUsuario, dataInicial,
                estado, criadoEm, atualizadoEm, versao);
    }
}
