package br.com.trilhaaprovacao.planejamento.aplicacao;

import br.com.trilhaaprovacao.planejamento.dominio.PreviaDaGeracaoDaSemana;

public record ResultadoDaPreviaDaGeracao(
        PreviaDaGeracaoDaSemana previa,
        String assinaturaDaPrevia) {
}
