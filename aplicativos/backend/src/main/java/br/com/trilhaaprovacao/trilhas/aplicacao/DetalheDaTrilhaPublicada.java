package br.com.trilhaaprovacao.trilhas.aplicacao;

import java.util.List;

public record DetalheDaTrilhaPublicada(
        ResumoDaTrilhaPublicada resumo,
        List<DisciplinaComTarefasDaTrilha> disciplinas) {
}
