package br.com.trilhaaprovacao.trilhas.aplicacao;

import java.util.List;
import java.util.UUID;

public record DisciplinaComTarefasDaTrilha(
        UUID identificador,
        String nome,
        int ordem,
        List<TarefaComAcompanhamentoDaTrilha> tarefas) {
}
