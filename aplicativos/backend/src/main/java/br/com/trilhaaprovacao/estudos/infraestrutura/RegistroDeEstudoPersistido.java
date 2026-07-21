package br.com.trilhaaprovacao.estudos.infraestrutura;

import br.com.trilhaaprovacao.estudos.dominio.RegistroDeEstudo;
import br.com.trilhaaprovacao.estudos.dominio.SituacaoDoRegistroDeEstudo;
import br.com.trilhaaprovacao.estudos.dominio.TipoDeEstudo;
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
@Table(name = "registros_de_estudo")
public class RegistroDeEstudoPersistido {
    @Id private UUID identificador;
    @Column(name = "topico_id", nullable = false) private UUID identificadorDoTopico;
    @Column(name = "material_id") private UUID identificadorDoMaterial;
    @Column(name = "registro_de_origem_id") private UUID identificadorDoRegistroDeOrigem;
    @Enumerated(EnumType.STRING) @Column(name = "tipo_de_estudo", nullable = false)
    private TipoDeEstudo tipoDeEstudo;
    @Column(name = "data_hora", nullable = false) private OffsetDateTime dataHora;
    @Column(name = "duracao_em_minutos", nullable = false) private int duracaoEmMinutos;
    @Column(length = 2000) private String observacao;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private SituacaoDoRegistroDeEstudo situacao;
    @Column(name = "criado_em", nullable = false) private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false) private OffsetDateTime atualizadoEm;
    @Version private long versao;

    protected RegistroDeEstudoPersistido() {
    }

    public RegistroDeEstudoPersistido(RegistroDeEstudo registro) {
        identificador = registro.identificador();
        identificadorDoTopico = registro.identificadorDoTopico();
        identificadorDoMaterial = registro.identificadorDoMaterial();
        identificadorDoRegistroDeOrigem = registro.identificadorDoRegistroDeOrigem();
        tipoDeEstudo = registro.tipoDeEstudo();
        dataHora = registro.dataHora();
        duracaoEmMinutos = registro.duracaoEmMinutos();
        observacao = registro.observacao();
        situacao = registro.situacao();
        criadoEm = registro.criadoEm();
        atualizadoEm = registro.atualizadoEm();
    }

    public void atualizarDe(RegistroDeEstudo registro) {
        situacao = registro.situacao();
        atualizadoEm = registro.atualizadoEm();
    }

    public RegistroDeEstudo paraDominio() {
        return new RegistroDeEstudo(identificador, identificadorDoTopico,
                identificadorDoMaterial, identificadorDoRegistroDeOrigem, tipoDeEstudo, dataHora,
                duracaoEmMinutos, observacao, situacao, criadoEm, atualizadoEm, versao);
    }

    public UUID identificadorDoTopico() {
        return identificadorDoTopico;
    }

    public UUID identificadorDoMaterial() {
        return identificadorDoMaterial;
    }
}
