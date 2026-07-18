package br.com.trilhaaprovacao.estudos.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MateriaisEEstudosTest {
    @Test
    void deveValidarUrlEDuracaoDoMaterial() {
        assertThrows(IllegalArgumentException.class,
                () -> MaterialDeEstudo.criar(UUID.randomUUID(), "PDF",
                        TipoDeMaterial.PDF, null, null, "arquivo-local", 10));
        assertThrows(IllegalArgumentException.class,
                () -> MaterialDeEstudo.criar(UUID.randomUUID(), "PDF",
                        TipoDeMaterial.PDF, null, null, null, 0));
    }

    @Test
    void deveValidarLimitesDoRegistro() {
        assertThrows(IllegalArgumentException.class,
                () -> RegistroDeEstudo.criar(UUID.randomUUID(), null,
                        OffsetDateTime.now(), 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> RegistroDeEstudo.criar(UUID.randomUUID(), null,
                        OffsetDateTime.now(), 1441, null));
    }

    @Test
    void devePreservarOrigemNaCorrecaoECancelarSemExcluir() {
        RegistroDeEstudo original = RegistroDeEstudo.criar(UUID.randomUUID(), null,
                OffsetDateTime.now(), 60, "Original");
        RegistroDeEstudo correcao = original.criarCorrecao(
                original.identificadorDoTopico(), null,
                OffsetDateTime.now(), 45, "Corrigido");

        assertEquals(original.identificador(), correcao.identificadorDoRegistroDeOrigem());
        assertTrue(correcao.cancelar().situacao()
                == SituacaoDoRegistroDeEstudo.CANCELADO);
        assertEquals(SituacaoDoRegistroDeEstudo.CORRIGIDO,
                original.encerrarComoCorrigido().situacao());
    }
}
