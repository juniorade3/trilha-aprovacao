package br.com.trilhaaprovacao.estudos.api;

import br.com.trilhaaprovacao.estudos.dominio.MaterialDeEstudo;
import br.com.trilhaaprovacao.estudos.dominio.TipoDeMaterial;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDeMaterial(
        UUID identificador,
        String titulo,
        TipoDeMaterial tipo,
        String descricao,
        String fonte,
        String endereco,
        Integer duracaoEstimadaEmMinutos,
        boolean arquivado,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    static RespostaDeMaterial de(MaterialDeEstudo material) {
        return new RespostaDeMaterial(material.identificador(), material.titulo(),
                material.tipo(), material.descricao(), material.fonte(),
                material.endereco(), material.duracaoEstimadaEmMinutos(),
                material.arquivado(), material.criadoEm(), material.atualizadoEm(),
                material.versao());
    }
}
