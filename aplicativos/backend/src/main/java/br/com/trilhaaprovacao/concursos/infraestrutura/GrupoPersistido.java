package br.com.trilhaaprovacao.concursos.infraestrutura;

import br.com.trilhaaprovacao.concursos.dominio.GrupoDeConteudo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "grupos_de_conteudo")
public class GrupoPersistido {
    @Id
    private UUID identificador;
    @Column(name = "prova_id", nullable = false)
    private UUID identificadorDaProva;
    @Column(nullable = false)
    private String nome;
    @Column(name = "nome_normalizado", nullable = false)
    private String nomeNormalizado;
    @Column(nullable = false)
    private int ordem;
    @Column(name = "quantidade_de_questoes")
    private Integer quantidadeDeQuestoes;
    @Column(name = "pontuacao_maxima")
    private BigDecimal pontuacaoMaxima;
    @Column(name = "pontuacao_minima")
    private BigDecimal pontuacaoMinima;
    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;
    @Version
    private long versao;

    protected GrupoPersistido() {
    }

    public GrupoPersistido(GrupoDeConteudo grupo) {
        identificador = grupo.identificador();
        identificadorDaProva = grupo.identificadorDaProva();
        criadoEm = grupo.criadoEm();
        atualizarDe(grupo);
    }

    public void atualizarDe(GrupoDeConteudo grupo) {
        nome = grupo.nome();
        nomeNormalizado = grupo.nomeNormalizado();
        ordem = grupo.ordem();
        quantidadeDeQuestoes = grupo.quantidadeDeQuestoes();
        pontuacaoMaxima = grupo.pontuacaoMaxima();
        pontuacaoMinima = grupo.pontuacaoMinima();
        atualizadoEm = grupo.atualizadoEm();
    }

    public GrupoDeConteudo paraDominio() {
        return new GrupoDeConteudo(identificador, identificadorDaProva, nome,
                nomeNormalizado, ordem, quantidadeDeQuestoes, pontuacaoMaxima,
                pontuacaoMinima, criadoEm, atualizadoEm, versao);
    }

    public UUID identificador() {
        return identificador;
    }

    public UUID identificadorDaProva() {
        return identificadorDaProva;
    }
}
