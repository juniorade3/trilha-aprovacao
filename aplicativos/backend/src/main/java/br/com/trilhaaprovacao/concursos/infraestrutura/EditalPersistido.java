package br.com.trilhaaprovacao.concursos.infraestrutura;

import br.com.trilhaaprovacao.concursos.dominio.Edital;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "editais")
public class EditalPersistido {
    @Id
    private UUID identificador;
    @Column(name = "concurso_id", nullable = false)
    private UUID identificadorDoConcurso;
    @Column(nullable = false)
    private String titulo;
    private String numero;
    private Integer ano;
    private String descricao;
    @Column(name = "data_de_publicacao")
    private LocalDate dataDePublicacao;
    @Column(name = "endereco_do_documento")
    private String enderecoDoDocumento;
    @Column(nullable = false)
    private boolean principal;
    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;
    @Version
    private long versao;

    protected EditalPersistido() {
    }

    public EditalPersistido(Edital edital) {
        identificador = edital.identificador();
        identificadorDoConcurso = edital.identificadorDoConcurso();
        criadoEm = edital.criadoEm();
        atualizarDe(edital);
    }

    public void atualizarDe(Edital edital) {
        titulo = edital.titulo();
        numero = edital.numero();
        ano = edital.ano();
        descricao = edital.descricao();
        dataDePublicacao = edital.dataDePublicacao();
        enderecoDoDocumento = edital.enderecoDoDocumento();
        principal = edital.principal();
        atualizadoEm = edital.atualizadoEm();
    }

    public Edital paraDominio() {
        return new Edital(identificador, identificadorDoConcurso, titulo, numero, ano,
                descricao, dataDePublicacao, enderecoDoDocumento, principal,
                criadoEm, atualizadoEm, versao);
    }

    public UUID identificador() {
        return identificador;
    }

    public UUID identificadorDoConcurso() {
        return identificadorDoConcurso;
    }
}
