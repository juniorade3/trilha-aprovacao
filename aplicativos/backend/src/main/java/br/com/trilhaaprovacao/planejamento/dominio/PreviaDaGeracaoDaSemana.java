package br.com.trilhaaprovacao.planejamento.dominio;

import java.util.List;
import java.util.UUID;

public record PreviaDaGeracaoDaSemana(
        UUID identificadorDoPlano,
        List<DiaDaPreviaDaGeracao> dias,
        List<JustificativaDaGeracao> avisos) {

    public PreviaDaGeracaoDaSemana {
        dias = List.copyOf(dias);
        avisos = List.copyOf(avisos);
        if (dias.size() != 7) throw new IllegalArgumentException("A previa deve possuir sete dias.");
    }
}
