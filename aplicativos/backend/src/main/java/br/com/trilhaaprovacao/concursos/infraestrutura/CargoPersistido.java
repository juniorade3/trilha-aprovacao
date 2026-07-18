package br.com.trilhaaprovacao.concursos.infraestrutura;

import br.com.trilhaaprovacao.concursos.dominio.CargoDoConcurso;
import br.com.trilhaaprovacao.concursos.dominio.NivelDeEscolaridade;
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
@Table(name = "cargos_do_concurso")
public class CargoPersistido {
    @Id
    private UUID identificador;
    @Column(name = "concurso_id", nullable = false)
    private UUID identificadorDoConcurso;
    @Column(nullable = false)
    private String nome;
    @Column(name = "nome_normalizado", nullable = false)
    private String nomeNormalizado;
    private String area;
    private String especialidade;
    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_de_escolaridade", nullable = false, length = 30)
    private NivelDeEscolaridade nivelDeEscolaridade;
    @Column(nullable = false)
    private boolean selecionado;
    @Column(nullable = false)
    private int ordem;
    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;
    @Version
    private long versao;

    protected CargoPersistido() {
    }

    public CargoPersistido(CargoDoConcurso cargo) {
        identificador = cargo.identificador();
        identificadorDoConcurso = cargo.identificadorDoConcurso();
        criadoEm = cargo.criadoEm();
        atualizarDe(cargo);
    }

    public void atualizarDe(CargoDoConcurso cargo) {
        nome = cargo.nome();
        nomeNormalizado = cargo.nomeNormalizado();
        area = cargo.area();
        especialidade = cargo.especialidade();
        nivelDeEscolaridade = cargo.nivelDeEscolaridade();
        selecionado = cargo.selecionado();
        ordem = cargo.ordem();
        atualizadoEm = cargo.atualizadoEm();
    }

    public CargoDoConcurso paraDominio() {
        return new CargoDoConcurso(identificador, identificadorDoConcurso, nome,
                nomeNormalizado, area, especialidade, nivelDeEscolaridade, selecionado,
                ordem, criadoEm, atualizadoEm, versao);
    }

    public UUID identificador() {
        return identificador;
    }

    public UUID identificadorDoConcurso() {
        return identificadorDoConcurso;
    }
}
