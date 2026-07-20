package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.planejamento.aplicacao.MateriaParaGeracao;
import java.util.List;

public record RespostaDeMateriasParaGeracao(
        List<RespostaDeMateriaParaGeracao> materias) {

    static RespostaDeMateriasParaGeracao de(List<MateriaParaGeracao> materias) {
        return new RespostaDeMateriasParaGeracao(materias.stream()
                .map(RespostaDeMateriaParaGeracao::de).toList());
    }
}
