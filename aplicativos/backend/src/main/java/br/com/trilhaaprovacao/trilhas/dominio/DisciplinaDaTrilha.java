package br.com.trilhaaprovacao.trilhas.dominio;

import java.util.UUID;

public record DisciplinaDaTrilha(UUID identificador, UUID identificadorDaTrilha,
        String nome, int ordem) {
}
