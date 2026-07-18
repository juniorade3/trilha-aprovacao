package br.com.trilhaaprovacao.concursos.infraestrutura;

import br.com.trilhaaprovacao.concursos.dominio.CaraterDaProva;
import br.com.trilhaaprovacao.concursos.dominio.Prova;
import br.com.trilhaaprovacao.concursos.dominio.TipoDeProva;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "provas")
public class ProvaPersistida {
    @Id
    private UUID identificador;
    @Column(name = "cargo_id", nullable = false)
    private UUID identificadorDoCargo;
    @Column(nullable = false)
    private String nome;
    @Column(name = "nome_normalizado", nullable = false)
    private String nomeNormalizado;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoDeProva tipo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CaraterDaProva carater;
    @Column(nullable = false)
    private int ordem;
    @Column(name = "data_hora_prevista")
    private OffsetDateTime dataHoraPrevista;
    @Column(name = "duracao_em_minutos")
    private Integer duracaoEmMinutos;
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

    protected ProvaPersistida() {
    }

    public ProvaPersistida(Prova prova) {
        identificador = prova.identificador();
        identificadorDoCargo = prova.identificadorDoCargo();
        criadoEm = prova.criadoEm();
        atualizarDe(prova);
    }

    public void atualizarDe(Prova prova) {
        nome = prova.nome();
        nomeNormalizado = prova.nomeNormalizado();
        tipo = prova.tipo();
        carater = prova.carater();
        ordem = prova.ordem();
        dataHoraPrevista = prova.dataHoraPrevista();
        duracaoEmMinutos = prova.duracaoEmMinutos();
        quantidadeDeQuestoes = prova.quantidadeDeQuestoes();
        pontuacaoMaxima = prova.pontuacaoMaxima();
        pontuacaoMinima = prova.pontuacaoMinima();
        atualizadoEm = prova.atualizadoEm();
    }

    public Prova paraDominio() {
        return new Prova(identificador, identificadorDoCargo, nome, nomeNormalizado,
                tipo, carater, ordem, dataHoraPrevista, duracaoEmMinutos,
                quantidadeDeQuestoes, pontuacaoMaxima, pontuacaoMinima,
                criadoEm, atualizadoEm, versao);
    }

    public UUID identificador() {
        return identificador;
    }

    public UUID identificadorDoCargo() {
        return identificadorDoCargo;
    }
}
