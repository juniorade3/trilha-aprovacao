package br.com.trilhaaprovacao.estudos.infraestrutura;

import java.util.UUID;

public interface ProjecaoDeMaterialRelacionadoAoTopico {
    UUID getIdentificadorDoTopico();

    UUID getIdentificadorDoMaterial();

    String getTituloDoMaterial();
}
