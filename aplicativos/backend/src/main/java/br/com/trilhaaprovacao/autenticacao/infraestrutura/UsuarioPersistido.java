package br.com.trilhaaprovacao.autenticacao.infraestrutura;

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
@Table(name = "usuarios")
public class UsuarioPersistido {

    @Id
    private UUID identificador;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(nullable = false)
    private SituacaoDoUsuario situacao;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @Version
    private long versao;

    protected UsuarioPersistido() {
    }

    public UsuarioPersistido(String nome, String email, String senhaHash) {
        OffsetDateTime agora = OffsetDateTime.now();
        this.identificador = UUID.randomUUID();
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.situacao = SituacaoDoUsuario.ATIVO;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public UUID identificador() { return identificador; }
    public String nome() { return nome; }
    public String email() { return email; }
    public String senhaHash() { return senhaHash; }
    public SituacaoDoUsuario situacao() { return situacao; }
}
