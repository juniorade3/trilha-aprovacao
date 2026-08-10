package br.com.trilhaaprovacao.trilhas.aplicacao;

import br.com.trilhaaprovacao.planejamento.dominio.TipoDeAtividade;
import br.com.trilhaaprovacao.trilhas.dominio.SituacaoDoAcompanhamentoDaTarefa;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TarefaComAcompanhamentoDaTrilha(
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
}
