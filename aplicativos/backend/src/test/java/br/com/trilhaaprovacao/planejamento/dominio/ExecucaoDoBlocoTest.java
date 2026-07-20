package br.com.trilhaaprovacao.planejamento.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExecucaoDoBlocoTest {

    @Test
    void deveIniciarEConcluirBloco() {
        BlocoDeEstudo bloco = blocoPlanejado();
        BlocoDeEstudo iniciado = bloco.iniciar();
        ExecucaoDoBloco execucao = ExecucaoDoBloco.iniciar(
                UUID.randomUUID(), bloco.identificador(), OffsetDateTime.now().minusMinutes(10));

        ExecucaoDoBloco encerrada = execucao.encerrar(
                ResultadoDaExecucao.CONCLUIDO, 10, "Concluido", OffsetDateTime.now());

        assertEquals(EstadoDoBlocoDeEstudo.EM_ANDAMENTO, iniciado.estado());
        assertEquals(EstadoDoBlocoDeEstudo.CONCLUIDO, iniciado.concluir().estado());
        assertFalse(encerrada.estaEmAndamento());
        assertTrue(encerrada.equivaleAoEncerramento(
                ResultadoDaExecucao.CONCLUIDO, 10, " Concluido "));
    }

    @Test
    void deveConcluirParcialmenteEImpedirTransicoesInvalidas() {
        BlocoDeEstudo bloco = blocoPlanejado();
        BlocoDeEstudo iniciado = bloco.iniciar();

        assertEquals(EstadoDoBlocoDeEstudo.PARCIALMENTE_CONCLUIDO,
                iniciado.concluirParcialmente().estado());
        assertThrows(IllegalStateException.class, bloco::concluir);
        assertThrows(IllegalStateException.class, iniciado::iniciar);
    }

    @Test
    void deveValidarDuracaoEEncerramentoUnico() {
        OffsetDateTime inicio = OffsetDateTime.now().minusMinutes(5);
        ExecucaoDoBloco execucao = ExecucaoDoBloco.iniciar(
                UUID.randomUUID(), UUID.randomUUID(), inicio);

        assertTrue(execucao.estaEmAndamento());
        assertThrows(IllegalArgumentException.class,
                () -> execucao.encerrar(ResultadoDaExecucao.CONCLUIDO, 0, null,
                        OffsetDateTime.now()));
        ExecucaoDoBloco encerrada = execucao.encerrar(
                ResultadoDaExecucao.PARCIALMENTE_CONCLUIDO, 5, null,
                OffsetDateTime.now());
        assertThrows(IllegalStateException.class,
                () -> encerrada.encerrar(ResultadoDaExecucao.CONCLUIDO, 5, null,
                        OffsetDateTime.now()));
    }


    @Test
    void deveVincularUmUnicoRegistroDeEstudo() {
        ExecucaoDoBloco encerrada = ExecucaoDoBloco.iniciar(
                        UUID.randomUUID(), UUID.randomUUID(),
                        OffsetDateTime.now().minusMinutes(10))
                .encerrar(ResultadoDaExecucao.CONCLUIDO, 10, null,
                        OffsetDateTime.now());
        UUID registro = UUID.randomUUID();
        ExecucaoDoBloco vinculada = encerrada.vincularRegistroDeEstudo(
                registro, OffsetDateTime.now());
        assertEquals(registro, vinculada.identificadorDoRegistroDeEstudo());
        assertEquals(vinculada, vinculada.vincularRegistroDeEstudo(
                registro, OffsetDateTime.now()));
        assertThrows(IllegalStateException.class,
                () -> vinculada.vincularRegistroDeEstudo(
                        UUID.randomUUID(), OffsetDateTime.now()));
    }

    private BlocoDeEstudo blocoPlanejado() {
        return BlocoDeEstudo.criar(UUID.randomUUID(), null, null,
                "Teoria", TipoDeAtividade.TEORIA, LocalDate.now(),
                50, 1, null, null);
    }
}
