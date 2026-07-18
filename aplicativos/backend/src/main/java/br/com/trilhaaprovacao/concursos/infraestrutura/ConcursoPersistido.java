package br.com.trilhaaprovacao.concursos.infraestrutura;

import br.com.trilhaaprovacao.concursos.dominio.Concurso;
import br.com.trilhaaprovacao.concursos.dominio.SituacaoDoConcurso;
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
@Table(name = "concursos")
public class ConcursoPersistido {
    @Id
    private UUID identificador;
    @Column(name = "usuario_id", nullable = false)
    private UUID identificadorDoUsuario;
    @Column(nullable = false)
    private String nome;
    @Column(name = "nome_normalizado", nullable = false)
    private String nomeNormalizado;
    private String descricao;
    private String orgao;
    private String banca;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SituacaoDoConcurso situacao;
    @Column(name = "data_prevista_principal")
    private LocalDate dataPrevistaPrincipal;
    @Column(nullable = false)
    private boolean ativo;
    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;
    @Version
    private long versao;

    protected ConcursoPersistido() {
    }

    public ConcursoPersistido(Concurso concurso) {
        identificador = concurso.identificador();
        identificadorDoUsuario = concurso.identificadorDoUsuario();
        criadoEm = concurso.criadoEm();
        atualizarDe(concurso);
    }

    public void atualizarDe(Concurso concurso) {
        nome = concurso.nome();
        nomeNormalizado = concurso.nomeNormalizado();
        descricao = concurso.descricao();
        orgao = concurso.orgao();
        banca = concurso.banca();
        situacao = concurso.situacao();
        dataPrevistaPrincipal = concurso.dataPrevistaPrincipal();
        ativo = concurso.ativo();
        atualizadoEm = concurso.atualizadoEm();
    }

    public Concurso paraDominio() {
        return new Concurso(identificador, identificadorDoUsuario, nome, nomeNormalizado,
                descricao, orgao, banca, situacao, dataPrevistaPrincipal, ativo,
                criadoEm, atualizadoEm, versao);
    }

    public UUID identificador() {
        return identificador;
    }

    public UUID identificadorDoUsuario() {
        return identificadorDoUsuario;
    }

    public boolean ativo() {
        return ativo;
    }
}
