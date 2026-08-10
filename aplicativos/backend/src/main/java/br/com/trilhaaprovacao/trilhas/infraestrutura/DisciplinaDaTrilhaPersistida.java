package br.com.trilhaaprovacao.trilhas.infraestrutura;

import br.com.trilhaaprovacao.trilhas.dominio.DisciplinaDaTrilha;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "disciplinas_da_trilha")
public class DisciplinaDaTrilhaPersistida {
    @Id
    private UUID identificador;
    @Column(name = "trilha_id", nullable = false)
    private UUID identificadorDaTrilha;
    @Column(nullable = false, length = 160)
    private String nome;
    @Column(nullable = false)
    private int ordem;

    protected DisciplinaDaTrilhaPersistida() {
    }

    public DisciplinaDaTrilha paraDominio() {
        return new DisciplinaDaTrilha(identificador, identificadorDaTrilha, nome, ordem);
    }

    public UUID identificador() {
        return identificador;
    }

    public UUID identificadorDaTrilha() {
        return identificadorDaTrilha;
    }
}
