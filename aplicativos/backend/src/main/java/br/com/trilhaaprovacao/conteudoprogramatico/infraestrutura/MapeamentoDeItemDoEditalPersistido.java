package br.com.trilhaaprovacao.conteudoprogramatico.infraestrutura;

import br.com.trilhaaprovacao.conteudoprogramatico.dominio.MapeamentoDeItemDoEdital;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mapeamentos_de_itens_do_edital")
public class MapeamentoDeItemDoEditalPersistido {
    @Id
    private UUID identificador;
    @Column(name = "item_do_edital_id", nullable = false)
    private UUID identificadorDoItemDoEdital;
    @Column(name = "topico_da_materia_id", nullable = false)
    private UUID identificadorDoTopicoDaMateria;
    @Column(nullable = false)
    private boolean confirmado;
    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    protected MapeamentoDeItemDoEditalPersistido() {
    }

    public MapeamentoDeItemDoEditalPersistido(MapeamentoDeItemDoEdital mapeamento) {
        identificador = mapeamento.identificador();
        identificadorDoItemDoEdital = mapeamento.identificadorDoItemDoEdital();
        identificadorDoTopicoDaMateria = mapeamento.identificadorDoTopicoDaMateria();
        confirmado = mapeamento.confirmado();
        criadoEm = mapeamento.criadoEm();
    }

    public MapeamentoDeItemDoEdital paraDominio() {
        return new MapeamentoDeItemDoEdital(identificador, identificadorDoItemDoEdital,
                identificadorDoTopicoDaMateria, confirmado, criadoEm);
    }
}
