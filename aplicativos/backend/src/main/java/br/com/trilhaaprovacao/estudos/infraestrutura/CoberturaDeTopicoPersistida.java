package br.com.trilhaaprovacao.estudos.infraestrutura;

import br.com.trilhaaprovacao.estudos.dominio.CoberturaDeTopicoPorMaterial;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "coberturas_de_topicos_por_material")
public class CoberturaDeTopicoPersistida {
    @Id private UUID identificador;
    @Column(name = "material_id", nullable = false) private UUID identificadorDoMaterial;
    @Column(name = "topico_id", nullable = false) private UUID identificadorDoTopico;
    @Column(name = "criado_em", nullable = false) private OffsetDateTime criadoEm;

    protected CoberturaDeTopicoPersistida() {
    }

    public CoberturaDeTopicoPersistida(CoberturaDeTopicoPorMaterial cobertura) {
        identificador = cobertura.identificador();
        identificadorDoMaterial = cobertura.identificadorDoMaterial();
        identificadorDoTopico = cobertura.identificadorDoTopico();
        criadoEm = cobertura.criadoEm();
    }

    public CoberturaDeTopicoPorMaterial paraDominio() {
        return new CoberturaDeTopicoPorMaterial(identificador,
                identificadorDoMaterial, identificadorDoTopico, criadoEm);
    }
}
