package br.com.trilhaaprovacao.planejamento.infraestrutura;

import br.com.trilhaaprovacao.planejamento.dominio.DisponibilidadeDoDia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "disponibilidades_do_dia")
public class DisponibilidadeDoDiaPersistida {
    @Id private UUID identificador;
    @Column(name = "plano_id", nullable = false) private UUID identificadorDoPlano;
    @Column(nullable = false) private LocalDate data;
    @Column(name = "minutos_disponiveis", nullable = false) private int minutosDisponiveis;
    @Column(name = "criado_em", nullable = false) private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false) private OffsetDateTime atualizadoEm;
    @Version private long versao;

    protected DisponibilidadeDoDiaPersistida() {
    }

    public DisponibilidadeDoDiaPersistida(DisponibilidadeDoDia disponibilidade) {
        identificador = disponibilidade.identificador();
        identificadorDoPlano = disponibilidade.identificadorDoPlano();
        atualizarDe(disponibilidade);
        criadoEm = disponibilidade.criadoEm();
    }

    public void atualizarDe(DisponibilidadeDoDia disponibilidade) {
        data = disponibilidade.data();
        minutosDisponiveis = disponibilidade.minutosDisponiveis();
        atualizadoEm = disponibilidade.atualizadoEm();
    }

    public DisponibilidadeDoDia paraDominio() {
        return new DisponibilidadeDoDia(identificador, identificadorDoPlano, data,
                minutosDisponiveis, criadoEm, atualizadoEm, versao);
    }
}
