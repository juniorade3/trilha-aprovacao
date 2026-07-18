package br.com.trilhaaprovacao.conteudos.infraestrutura;

import br.com.trilhaaprovacao.conteudos.dominio.Materia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "materias")
public class MateriaPersistida {
    @Id
    private UUID identificador;

    @Column(name = "usuario_id", nullable = false)
    private UUID identificadorDoUsuario;

    @Column(nullable = false)
    private String nome;

    @Column(name = "nome_normalizado", nullable = false)
    private String nomeNormalizado;

    private String descricao;
    private String cor;

    @Column(nullable = false)
    private boolean arquivada;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @Version
    private long versao;

    protected MateriaPersistida() {
    }

    public MateriaPersistida(Materia materia) {
        this.identificador = materia.identificador();
        this.identificadorDoUsuario = materia.identificadorDoUsuario();
        this.criadoEm = materia.criadoEm();
        atualizarDe(materia);
    }

    public void atualizarDe(Materia materia) {
        this.nome = materia.nome();
        this.nomeNormalizado = materia.nomeNormalizado();
        this.descricao = materia.descricao();
        this.cor = materia.cor();
        this.arquivada = materia.arquivada();
        this.atualizadoEm = materia.atualizadoEm();
    }

    public Materia paraDominio() {
        return Materia.reconstituir(identificador, identificadorDoUsuario, nome, descricao, cor,
                arquivada, criadoEm, atualizadoEm, versao);
    }

    public UUID identificador() { return identificador; }
    public UUID identificadorDoUsuario() { return identificadorDoUsuario; }
}
