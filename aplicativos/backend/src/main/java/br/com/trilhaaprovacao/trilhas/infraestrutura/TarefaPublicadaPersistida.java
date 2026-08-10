package br.com.trilhaaprovacao.trilhas.infraestrutura;

import br.com.trilhaaprovacao.planejamento.dominio.TipoDeAtividade;
import br.com.trilhaaprovacao.trilhas.dominio.TarefaPublicada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "tarefas_publicadas_da_trilha")
public class TarefaPublicadaPersistida {
    @Id
    private UUID identificador;
    @Column(name = "disciplina_id", nullable = false)
    private UUID identificadorDaDisciplina;
    @Column(nullable = false)
    private int numero;
    @Column(nullable = false, length = 280)
    private String titulo;
    @Column(length = 160)
    private String aula;
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_de_atividade", nullable = false)
    private TipoDeAtividade tipoDeAtividade;
    @Column(name = "endereco_do_material", length = 2048)
    private String enderecoDoMaterial;
    @Column(length = 8000)
    private String orientacao;

    protected TarefaPublicadaPersistida() {
    }

    public TarefaPublicada paraDominio() {
        return new TarefaPublicada(identificador, identificadorDaDisciplina, numero, titulo,
                aula, tipoDeAtividade, enderecoDoMaterial, orientacao);
    }

    public UUID identificador() {
        return identificador;
    }

    public UUID identificadorDaDisciplina() {
        return identificadorDaDisciplina;
    }
}
