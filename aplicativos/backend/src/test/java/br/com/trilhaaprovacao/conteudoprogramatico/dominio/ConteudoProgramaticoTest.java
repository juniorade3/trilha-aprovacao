package br.com.trilhaaprovacao.conteudoprogramatico.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConteudoProgramaticoTest {
    @Test
    void devePreservarRedacaoOriginalDoItem() {
        String redacao = "  Direitos e garantias fundamentais; controle de constitucionalidade.  ";

        ItemDoEdital item = ItemDoEdital.criar(
                UUID.randomUUID(), UUID.randomUUID(), redacao, null, 1);

        assertEquals(redacao, item.descricaoOriginal());
        assertThrows(IllegalArgumentException.class,
                () -> item.alterar("   ", null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> item.alterar(redacao, item.identificador(), 1));
    }

    @Test
    void deveCriarMapeamentoManualConfirmado() {
        MapeamentoDeItemDoEdital mapeamento =
                MapeamentoDeItemDoEdital.criarManual(
                        UUID.randomUUID(), UUID.randomUUID());

        assertTrue(mapeamento.confirmado());
    }
}
