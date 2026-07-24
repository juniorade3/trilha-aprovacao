package br.com.trilhaaprovacao.estudos.api;

import br.com.trilhaaprovacao.estudos.aplicacao.MaterialRelacionadoAoTopico;
import java.util.UUID;

public record RespostaDeMaterialRelacionadoAoTopico(
        UUID identificadorDoTopico,
        UUID identificadorDoMaterial,
        String tituloDoMaterial) {

    static RespostaDeMaterialRelacionadoAoTopico de(
            MaterialRelacionadoAoTopico material) {
        return new RespostaDeMaterialRelacionadoAoTopico(
                material.identificadorDoTopico(),
                material.identificadorDoMaterial(),
                material.tituloDoMaterial());
    }
}
