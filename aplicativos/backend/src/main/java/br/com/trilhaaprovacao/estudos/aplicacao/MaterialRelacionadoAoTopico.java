package br.com.trilhaaprovacao.estudos.aplicacao;

import java.util.UUID;

public record MaterialRelacionadoAoTopico(
        UUID identificadorDoTopico,
        UUID identificadorDoMaterial,
        String tituloDoMaterial) {
}
