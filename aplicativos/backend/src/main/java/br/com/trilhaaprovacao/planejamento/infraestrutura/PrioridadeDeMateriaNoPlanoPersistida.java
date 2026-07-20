package br.com.trilhaaprovacao.planejamento.infraestrutura;

import br.com.trilhaaprovacao.planejamento.dominio.PrioridadeDaMateriaNoPlano;
import br.com.trilhaaprovacao.planejamento.dominio.PrioridadeDeMateriaNoPlano;
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
@Table(name = "prioridades_de_materias_no_plano")
public class PrioridadeDeMateriaNoPlanoPersistida {
    @Id private UUID identificador;
    @Column(name = "plano_id", nullable = false) private UUID identificadorDoPlano;
    @Column(name = "materia_id", nullable = false) private UUID identificadorDaMateria;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private PrioridadeDaMateriaNoPlano prioridade;
    @Column(name = "criado_em", nullable = false) private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false) private OffsetDateTime atualizadoEm;
    @Version private long versao;

    protected PrioridadeDeMateriaNoPlanoPersistida() {
    }

    public PrioridadeDeMateriaNoPlanoPersistida(PrioridadeDeMateriaNoPlano valor) {
        identificador = valor.identificador();
        identificadorDoPlano = valor.identificadorDoPlano();
        identificadorDaMateria = valor.identificadorDaMateria();
        criadoEm = valor.criadoEm();
        atualizarDe(valor);
    }

    public void atualizarDe(PrioridadeDeMateriaNoPlano valor) {
        prioridade = valor.prioridade();
        atualizadoEm = valor.atualizadoEm();
    }

    public PrioridadeDeMateriaNoPlano paraDominio() {
        return new PrioridadeDeMateriaNoPlano(identificador, identificadorDoPlano,
                identificadorDaMateria, prioridade, criadoEm, atualizadoEm, versao);
    }
}
