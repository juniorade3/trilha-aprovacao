package br.com.trilhaaprovacao.trilhas.api;

import br.com.trilhaaprovacao.trilhas.aplicacao.DetalheDaTrilhaPublicada;
import java.util.List;

public record RespostaDetalhadaDeTrilhaPublicada(
        RespostaDeTrilhaPublicada trilha,
        List<RespostaDeDisciplinaDaTrilha> disciplinas) {
    static RespostaDetalhadaDeTrilhaPublicada de(DetalheDaTrilhaPublicada detalhe) {
        return new RespostaDetalhadaDeTrilhaPublicada(RespostaDeTrilhaPublicada.de(detalhe.resumo()),
                detalhe.disciplinas().stream().map(RespostaDeDisciplinaDaTrilha::de).toList());
    }
}
