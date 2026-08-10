package br.com.trilhaaprovacao.trilhas.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AcompanhamentoDaTarefaTest {
    @Test
    void deveRegistrarConclusaoEAbrirNovamenteUmaTarefa() {
        AcompanhamentoDaTarefa pendente = AcompanhamentoDaTarefa.criar(
                UUID.randomUUID(), UUID.randomUUID());

        AcompanhamentoDaTarefa concluida = pendente.alterar(
                SituacaoDoAcompanhamentoDaTarefa.CONCLUIDA, "Resolvi as questoes.");
        AcompanhamentoDaTarefa reaberta = concluida.alterar(
                SituacaoDoAcompanhamentoDaTarefa.EM_ANDAMENTO, null);

        assertEquals(SituacaoDoAcompanhamentoDaTarefa.CONCLUIDA, concluida.situacao());
        assertEquals("Resolvi as questoes.", concluida.observacao());
        assertEquals(SituacaoDoAcompanhamentoDaTarefa.EM_ANDAMENTO, reaberta.situacao());
        assertNull(reaberta.concluidaEm());
    }
}
