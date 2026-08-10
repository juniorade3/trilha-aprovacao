package br.com.trilhaaprovacao.trilhas.api;

import br.com.trilhaaprovacao.planejamento.dominio.TipoDeAtividade;
import br.com.trilhaaprovacao.trilhas.aplicacao.TarefaComAcompanhamentoDaTrilha;
import br.com.trilhaaprovacao.trilhas.dominio.SituacaoDoAcompanhamentoDaTarefa;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDeTarefaDaTrilha(
        UUID identificador,
        int numero,
        String titulo,
        String aula,
        TipoDeAtividade tipoDeAtividade,
        String enderecoDoMaterial,
        String orientacao,
        SituacaoDoAcompanhamentoDaTarefa situacao,
        String observacao,
        OffsetDateTime concluidaEm) {
    static RespostaDeTarefaDaTrilha de(TarefaComAcompanhamentoDaTrilha tarefa) {
        return new RespostaDeTarefaDaTrilha(tarefa.identificador(), tarefa.numero(), tarefa.titulo(),
                tarefa.aula(), tarefa.tipoDeAtividade(), tarefa.enderecoDoMaterial(),
                tarefa.orientacao(), tarefa.situacao(), tarefa.observacao(), tarefa.concluidaEm());
    }
}
