package br.com.trilhaaprovacao.concursos.infraestrutura;

import br.com.trilhaaprovacao.concursos.dominio.MateriaDaProva;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "materias_da_prova")
public class MateriaDaProvaPersistida {
    @Id
    private UUID identificador;
    @Column(name = "grupo_de_conteudo_id", nullable = false)
    private UUID identificadorDoGrupoDeConteudo;
    @Column(name = "materia_id", nullable = false)
    private UUID identificadorDaMateria;
    @Column(nullable = false)
    private int ordem;
    private BigDecimal peso;
    @Column(name = "quantidade_de_questoes")
    private Integer quantidadeDeQuestoes;
    @Column(name = "pontuacao_maxima")
    private BigDecimal pontuacaoMaxima;
    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;
    @Version
    private long versao;

    protected MateriaDaProvaPersistida() {
    }

    public MateriaDaProvaPersistida(MateriaDaProva materia) {
        identificador = materia.identificador();
        identificadorDoGrupoDeConteudo = materia.identificadorDoGrupoDeConteudo();
        identificadorDaMateria = materia.identificadorDaMateria();
        criadoEm = materia.criadoEm();
        atualizarDe(materia);
    }

    public void atualizarDe(MateriaDaProva materia) {
        ordem = materia.ordem();
        peso = materia.peso();
        quantidadeDeQuestoes = materia.quantidadeDeQuestoes();
        pontuacaoMaxima = materia.pontuacaoMaxima();
        atualizadoEm = materia.atualizadoEm();
    }

    public MateriaDaProva paraDominio() {
        return new MateriaDaProva(identificador, identificadorDoGrupoDeConteudo,
                identificadorDaMateria, ordem, peso, quantidadeDeQuestoes,
                pontuacaoMaxima, criadoEm, atualizadoEm, versao);
    }

    public UUID identificador() {
        return identificador;
    }

    public UUID identificadorDoGrupoDeConteudo() {
        return identificadorDoGrupoDeConteudo;
    }

    public UUID identificadorDaMateria() {
        return identificadorDaMateria;
    }
}
