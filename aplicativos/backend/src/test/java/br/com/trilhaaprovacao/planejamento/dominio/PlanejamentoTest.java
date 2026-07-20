package br.com.trilhaaprovacao.planejamento.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanejamentoTest {
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 7, 20);

    @Test
    void deveCriarPlanoEmRascunhoSomenteNaSegundaFeira() {
        PlanoSemanal plano = PlanoSemanal.criar(UUID.randomUUID(), SEGUNDA);

        assertEquals(EstadoDoPlanoSemanal.RASCUNHO, plano.estado());
        assertEquals(SEGUNDA.plusDays(6), plano.dataFinal());
        assertThrows(IllegalArgumentException.class,
                () -> PlanoSemanal.criar(UUID.randomUUID(), SEGUNDA.plusDays(1)));
    }

    @Test
    void deveCriarDisponibilidadeDaSemanaEValidarLimites() {
        PlanoSemanal plano = PlanoSemanal.criar(UUID.randomUUID(), SEGUNDA);
        DisponibilidadeDoDia dia = DisponibilidadeDoDia.criar(plano, SEGUNDA);

        assertEquals(0, dia.minutosDisponiveis());
        assertEquals(180, dia.alterarMinutos(180).minutosDisponiveis());
        assertThrows(IllegalArgumentException.class, () -> dia.alterarMinutos(-1));
        assertThrows(IllegalArgumentException.class, () -> dia.alterarMinutos(1441));
        assertThrows(IllegalArgumentException.class,
                () -> DisponibilidadeDoDia.criar(plano, SEGUNDA.minusDays(1)));
    }

    @Test
    void deveImpedirEdicaoDePlanoForaDoRascunho() {
        OffsetDateTime agora = OffsetDateTime.now();
        PlanoSemanal encerrado = new PlanoSemanal(UUID.randomUUID(),
                UUID.randomUUID(), SEGUNDA, EstadoDoPlanoSemanal.ENCERRADO,
                agora, agora, 0);

        assertThrows(IllegalStateException.class, encerrado::exigirEditavel);
    }

    @Test
    void deveAtivarPlanoEManterAtivacaoIdempotente() {
        PlanoSemanal rascunho = PlanoSemanal.criar(UUID.randomUUID(), SEGUNDA);

        PlanoSemanal ativo = rascunho.ativar();

        assertEquals(EstadoDoPlanoSemanal.ATIVO, ativo.estado());
        assertEquals(true, ativo.estaAtivo());
        assertEquals(ativo, ativo.ativar());
        assertThrows(IllegalStateException.class, ativo::exigirRascunho);
    }

    @Test
    void deveEncerrarOuCancelarSomenteNosEstadosPermitidos() {
        PlanoSemanal rascunho = PlanoSemanal.criar(UUID.randomUUID(), SEGUNDA);
        PlanoSemanal ativo = rascunho.ativar();

        assertEquals(EstadoDoPlanoSemanal.ENCERRADO, ativo.encerrar().estado());
        assertEquals(EstadoDoPlanoSemanal.CANCELADO, rascunho.cancelar().estado());
        assertEquals(EstadoDoPlanoSemanal.CANCELADO, ativo.cancelar().estado());
        assertThrows(IllegalStateException.class, rascunho::encerrar);
        assertThrows(IllegalStateException.class, () -> ativo.encerrar().cancelar());
    }

    @Test
    void deveCriarEAlterarBlocoPlanejado() {
        UUID plano = UUID.randomUUID();
        BlocoDeEstudo bloco = BlocoDeEstudo.criar(plano, null, null,
                "  Leitura livre  ", TipoDeAtividade.TEORIA, SEGUNDA,
                60, 1, LocalTime.of(8, 30), "  Capitulo inicial  ");

        assertEquals("Leitura livre", bloco.titulo());
        assertEquals("Capitulo inicial", bloco.observacao());
        assertEquals(OrigemDoBlocoDeEstudo.MANUAL, bloco.origem());
        assertEquals(EstadoDoBlocoDeEstudo.PLANEJADO, bloco.estado());
        assertEquals(2, bloco.moverPara(SEGUNDA.plusDays(1), 2).ordem());
    }

    @Test
    void deveCriarGeradoETransformarEmAjustadoNaPrimeiraEdicaoManual() {
        BlocoDeEstudo gerado = BlocoDeEstudo.criarGerado(UUID.randomUUID(),
                UUID.randomUUID(), "Banco de dados", TipoDeAtividade.TEORIA,
                SEGUNDA, 50, 2, "EQUILIBRIO_DA_SEMANA: Menor carga.");

        assertEquals(OrigemDoBlocoDeEstudo.GERADO_DETERMINISTICAMENTE,
                gerado.origem());
        assertEquals(OrigemDoBlocoDeEstudo.GERADO_DETERMINISTICAMENTE,
                gerado.normalizarPosicao(SEGUNDA, 1).origem());

        BlocoDeEstudo ajustado = gerado.alterarPlanejamento(
                gerado.identificadorDaMateria(), null, "Banco de dados ajustado",
                TipoDeAtividade.QUESTOES, SEGUNDA, 45, 1, null, null);

        assertEquals(OrigemDoBlocoDeEstudo.GERADO_AJUSTADO_MANUALMENTE,
                ajustado.origem());
        assertEquals(gerado.justificativaDaGeracao(),
                ajustado.justificativaDaGeracao());
    }

    @Test
    void deveReagendarCancelarECorrigirResultadoDoBloco() {
        BlocoDeEstudo bloco = BlocoDeEstudo.criar(UUID.randomUUID(), null, null,
                "Teoria", TipoDeAtividade.TEORIA, SEGUNDA,
                60, 1, null, null);

        BlocoDeEstudo reagendado = bloco.reagendar(
                SEGUNDA.plusDays(1), LocalTime.of(9, 0), 1);
        assertEquals(1, reagendado.quantidadeDeReagendamentos());
        assertEquals(SEGUNDA.plusDays(1), reagendado.data());
        assertEquals(EstadoDoBlocoDeEstudo.CANCELADO, reagendado.cancelar().estado());

        BlocoDeEstudo concluido = bloco.iniciar().concluir();
        assertEquals(EstadoDoBlocoDeEstudo.PARCIALMENTE_CONCLUIDO,
                concluido.corrigirResultado(
                        ResultadoDaExecucao.PARCIALMENTE_CONCLUIDO).estado());
        assertThrows(IllegalStateException.class,
                () -> bloco.corrigirResultado(ResultadoDaExecucao.CONCLUIDO));
    }

    @Test
    void deveValidarTituloDuracaoOrdemEVinculoDoBloco() {
        UUID plano = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> BlocoDeEstudo.criar(
                plano, null, null, " ", TipoDeAtividade.TEORIA,
                SEGUNDA, 60, 1, null, null));
        assertThrows(IllegalArgumentException.class, () -> BlocoDeEstudo.criar(
                plano, null, null, "Titulo", TipoDeAtividade.TEORIA,
                SEGUNDA, 0, 1, null, null));
        assertThrows(IllegalArgumentException.class, () -> BlocoDeEstudo.criar(
                plano, null, null, "Titulo", TipoDeAtividade.TEORIA,
                SEGUNDA, 60, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> BlocoDeEstudo.criar(
                plano, null, UUID.randomUUID(), "Titulo", TipoDeAtividade.TEORIA,
                SEGUNDA, 60, 1, null, null));
    }
}
