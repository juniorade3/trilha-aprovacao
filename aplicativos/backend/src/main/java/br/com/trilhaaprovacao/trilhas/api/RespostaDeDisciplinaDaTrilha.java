package br.com.trilhaaprovacao.trilhas.api;

import br.com.trilhaaprovacao.trilhas.aplicacao.DisciplinaComTarefasDaTrilha;
import java.util.List;
import java.util.UUID;

public record RespostaDeDisciplinaDaTrilha(
        UUID identificador,
        String nome,
        int ordem,
        List<RespostaDeTarefaDaTrilha> tarefas) {
    static RespostaDeDisciplinaDaTrilha de(DisciplinaComTarefasDaTrilha disciplina) {
        return new RespostaDeDisciplinaDaTrilha(disciplina.identificador(), disciplina.nome(),
                disciplina.ordem(), disciplina.tarefas().stream()
                        .map(RespostaDeTarefaDaTrilha::de).toList());
    }
}
