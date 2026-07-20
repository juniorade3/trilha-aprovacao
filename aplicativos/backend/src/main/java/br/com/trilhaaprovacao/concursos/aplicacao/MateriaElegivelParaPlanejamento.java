package br.com.trilhaaprovacao.concursos.aplicacao;

import java.util.UUID;

public record MateriaElegivelParaPlanejamento(
        UUID identificadorDaMateria,
        String nome,
        String nomeNormalizado,
        int ordemEstavel) {
}
