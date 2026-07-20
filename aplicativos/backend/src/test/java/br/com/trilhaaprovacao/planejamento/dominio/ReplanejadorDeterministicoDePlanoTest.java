package br.com.trilhaaprovacao.planejamento.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReplanejadorDeterministicoDePlanoTest {
    private static final UUID PLANO = uuid(1);
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 7, 20);
    private static final LocalDate QUARTA = SEGUNDA.plusDays(2);
    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-07-22T10:00:00-03:00");
    private final ReplanejadorDeterministicoDePlano replanejador =
            new ReplanejadorDeterministicoDePlano();

    @Test
    void identificaNaoIniciadoParcialEReagendamentoVencidoSemMinutosNegativos() {
        BlocoDeEstudo naoIniciado = bloco(10, uuid(101), SEGUNDA, 50, 1,
                EstadoDoBlocoDeEstudo.PLANEJADO, 0);
        BlocoDeEstudo parcial = bloco(11, uuid(102), SEGUNDA.plusDays(1), 50, 1,
                EstadoDoBlocoDeEstudo.PARCIALMENTE_CONCLUIDO, 0);
        BlocoDeEstudo acimaDoPrevisto = bloco(13, uuid(104), SEGUNDA.plusDays(1), 50, 3,
                EstadoDoBlocoDeEstudo.PARCIALMENTE_CONCLUIDO, 0);
        BlocoDeEstudo reagendado = bloco(12, uuid(103), SEGUNDA.plusDays(1), 40, 2,
                EstadoDoBlocoDeEstudo.PLANEJADO, 1);
        Map<UUID, ExecucaoDoBloco> execucoes = Map.of(
                parcial.identificador(), execucao(parcial, 20),
                acimaDoPrevisto.identificador(), execucao(acimaDoPrevisto, 80));

        var previa = gerar(List.of(naoIniciado, parcial, acimaDoPrevisto, reagendado), execucoes,
                dias(180), Set.of(), Set.of());

        assertThat(previa.pendencias()).extracting(
                ReplanejadorDeterministicoDePlano.Pendencia::motivo)
                .containsExactlyInAnyOrder(
                        ReplanejadorDeterministicoDePlano.Motivo.NAO_INICIADO,
                        ReplanejadorDeterministicoDePlano.Motivo.EXECUCAO_PARCIAL,
                        ReplanejadorDeterministicoDePlano.Motivo.REAGENDAMENTO_VENCIDO);
        assertThat(previa.pendencias()).allSatisfy(p ->
                assertThat(p.minutosPendentes()).isPositive());
    }

    @Test
    void ordenaDeterministicamentePorReagendamentosPrioridadeAtrasoMinutosEOrigem() {
        BlocoDeEstudo normal = bloco(20, uuid(201), SEGUNDA, 60, 2,
                EstadoDoBlocoDeEstudo.PLANEJADO, 0);
        BlocoDeEstudo alta = bloco(21, uuid(202), SEGUNDA, 80, 1,
                EstadoDoBlocoDeEstudo.PLANEJADO, 0);
        BlocoDeEstudo reagendado = bloco(22, uuid(203), SEGUNDA, 25, 1,
                EstadoDoBlocoDeEstudo.PLANEJADO, 1);
        Map<UUID, PrioridadeDaMateriaNoPlano> prioridades = Map.of(
                normal.identificadorDaMateria(), PrioridadeDaMateriaNoPlano.NORMAL,
                alta.identificadorDaMateria(), PrioridadeDaMateriaNoPlano.ALTA,
                reagendado.identificadorDaMateria(), PrioridadeDaMateriaNoPlano.ALTA);

        var previa = replanejador.replanejar(QUARTA, dias(300),
                List.of(reagendado, normal, alta), Map.of(), prioridades, Set.of(), Set.of());

        assertThat(previa.pendencias()).extracting(p -> p.bloco().identificador())
                .containsExactly(alta.identificador(), normal.identificador(),
                        reagendado.identificador());
    }

    @Test
    void moveInteiraAntesDeDividirEAceitaPendenciaInteiraMenorQueVinteECinco() {
        BlocoDeEstudo pequena = bloco(30, uuid(301), SEGUNDA, 20, 1,
                EstadoDoBlocoDeEstudo.PLANEJADO, 0);
        var previa = gerar(List.of(pequena), Map.of(), dias(20), Set.of(), Set.of());

        assertThat(previa.propostas()).singleElement().satisfies(p -> {
            assertThat(p.decisao()).isEqualTo(ReplanejadorDeterministicoDePlano.Decisao.ADIAR);
            assertThat(p.fragmentos()).singleElement()
                    .extracting(ReplanejadorDeterministicoDePlano.Fragmento::minutos)
                    .isEqualTo(20);
        });
    }

    @Test
    void divideSomenteIntegralmenteComFragmentosMinimos() {
        BlocoDeEstudo pendencia = bloco(40, uuid(401), SEGUNDA, 80, 1,
                EstadoDoBlocoDeEstudo.PLANEJADO, 0);
        List<DisponibilidadeDoDia> dias = dias(0);
        dias.set(2, disponibilidade(QUARTA, 40));
        dias.set(3, disponibilidade(QUARTA.plusDays(1), 40));

        var dividida = gerar(List.of(pendencia), Map.of(), dias, Set.of(), Set.of());
        assertThat(dividida.propostas()).singleElement().satisfies(p -> {
            assertThat(p.decisao()).isEqualTo(ReplanejadorDeterministicoDePlano.Decisao.DIVIDIR);
            assertThat(p.fragmentos()).hasSize(2).allSatisfy(f ->
                    assertThat(f.minutos()).isGreaterThanOrEqualTo(25));
        });

        dias.set(3, disponibilidade(QUARTA.plusDays(1), 20));
        var semCapacidade = gerar(List.of(pendencia), Map.of(), dias, Set.of(), Set.of());
        assertThat(semCapacidade.propostas()).singleElement().satisfies(p -> {
            assertThat(p.decisao()).isEqualTo(
                    ReplanejadorDeterministicoDePlano.Decisao.SEM_CAPACIDADE);
            assertThat(p.fragmentos()).isEmpty();
            assertThat(p.minutosNaoAlocados()).isEqualTo(80);
        });
    }

    @Test
    void respeitaCapacidadeTresMateriasRevisaoEAtividadeLivre() {
        BlocoDeEstudo materiaUm = futuro(50, uuid(501), QUARTA, 25);
        BlocoDeEstudo materiaDois = futuro(51, uuid(502), QUARTA, 25);
        BlocoDeEstudo materiaTres = futuro(52, uuid(503), QUARTA, 25);
        BlocoDeEstudo revisao = futuro(53, null, QUARTA, 25);
        BlocoDeEstudo livre = futuro(54, null, QUARTA, 25);
        BlocoDeEstudo quartaMateria = bloco(55, uuid(504), SEGUNDA, 25, 1,
                EstadoDoBlocoDeEstudo.PLANEJADO, 0);

        List<DisponibilidadeDoDia> capacidades = dias(0);
        capacidades.set(2, disponibilidade(QUARTA, 200));
        var previa = gerar(List.of(materiaUm, materiaDois, materiaTres, revisao,
                livre, quartaMateria), Map.of(), capacidades, Set.of(), Set.of());

        assertThat(previa.propostas()).singleElement().extracting(
                ReplanejadorDeterministicoDePlano.Proposta::decisao)
                .isEqualTo(ReplanejadorDeterministicoDePlano.Decisao.SEM_CAPACIDADE);
        assertThat(previa.capacidades().getFirst().minutosOcupados()).isEqualTo(125);
    }

    @Test
    void exigeConfirmacaoNoTerceiroEDelegaAcimaDeTresSemCancelar() {
        BlocoDeEstudo terceiro = bloco(60, uuid(601), SEGUNDA, 25, 1,
                EstadoDoBlocoDeEstudo.PLANEJADO, 3);
        BlocoDeEstudo acima = bloco(61, uuid(602), SEGUNDA, 25, 2,
                EstadoDoBlocoDeEstudo.PLANEJADO, 4);

        var previa = gerar(List.of(acima, terceiro), Map.of(), dias(100), Set.of(), Set.of());

        assertThat(previa.propostas()).filteredOn(p ->
                p.pendencia().bloco().identificador().equals(terceiro.identificador()))
                .singleElement().satisfies(p -> assertThat(p.exigeConfirmacao()).isTrue());
        assertThat(previa.propostas()).filteredOn(p ->
                p.pendencia().bloco().identificador().equals(acima.identificador()))
                .singleElement().satisfies(p -> {
                    assertThat(p.decisao()).isEqualTo(
                            ReplanejadorDeterministicoDePlano.Decisao.DECIDIR_MANUALMENTE);
                    assertThat(p.fragmentos()).isEmpty();
                });
    }

    @Test
    void excluiConcluidosCanceladosEmAndamentoFuturosEJaTransferidos() {
        BlocoDeEstudo concluido = bloco(70, uuid(701), SEGUNDA, 25, 1,
                EstadoDoBlocoDeEstudo.CONCLUIDO, 0);
        BlocoDeEstudo cancelado = bloco(71, uuid(702), SEGUNDA, 25, 2,
                EstadoDoBlocoDeEstudo.CANCELADO, 0);
        BlocoDeEstudo emAndamento = bloco(72, uuid(703), SEGUNDA, 25, 3,
                EstadoDoBlocoDeEstudo.EM_ANDAMENTO, 0);
        BlocoDeEstudo futuro = bloco(73, uuid(704), QUARTA.plusDays(1), 25, 1,
                EstadoDoBlocoDeEstudo.PLANEJADO, 0);
        BlocoDeEstudo transferido = bloco(74, uuid(705), SEGUNDA, 25, 4,
                EstadoDoBlocoDeEstudo.PLANEJADO, 0);

        var previa = gerar(List.of(concluido, cancelado, emAndamento, futuro, transferido),
                Map.of(), dias(100), Set.of(transferido.identificador()), Set.of());

        assertThat(previa.pendencias()).isEmpty();
        assertThat(previa.propostas()).isEmpty();
    }

    private ReplanejadorDeterministicoDePlano.Previa gerar(List<BlocoDeEstudo> blocos,
            Map<UUID, ExecucaoDoBloco> execucoes, List<DisponibilidadeDoDia> dias,
            Set<UUID> transferidos, Set<UUID> ignorados) {
        return replanejador.replanejar(QUARTA, dias, blocos, execucoes,
                Map.of(), transferidos, ignorados);
    }

    private List<DisponibilidadeDoDia> dias(int minutos) {
        List<DisponibilidadeDoDia> resultado = new ArrayList<>();
        for (int i = 0; i < 7; i++) resultado.add(disponibilidade(SEGUNDA.plusDays(i), minutos));
        return resultado;
    }

    private DisponibilidadeDoDia disponibilidade(LocalDate data, int minutos) {
        return new DisponibilidadeDoDia(UUID.randomUUID(), PLANO, data, minutos,
                AGORA, AGORA, 0);
    }

    private BlocoDeEstudo futuro(int id, UUID materia, LocalDate data, int minutos) {
        return bloco(id, materia, data, minutos, id, EstadoDoBlocoDeEstudo.PLANEJADO, 0);
    }

    private BlocoDeEstudo bloco(int id, UUID materia, LocalDate data, int minutos,
            int ordem, EstadoDoBlocoDeEstudo estado, int reagendamentos) {
        return new BlocoDeEstudo(uuid(id), PLANO, materia, null, "Bloco " + id,
                materia == null ? TipoDeAtividade.OUTRA : TipoDeAtividade.TEORIA,
                data, minutos, ordem, null, null, OrigemDoBlocoDeEstudo.MANUAL,
                null, null, estado, reagendamentos,
                reagendamentos == 0 ? null : AGORA, AGORA, AGORA, 0);
    }

    private ExecucaoDoBloco execucao(BlocoDeEstudo bloco, int minutos) {
        return new ExecucaoDoBloco(UUID.randomUUID(), uuid(999), bloco.identificador(),
                AGORA.minusHours(1), AGORA, minutos,
                ResultadoDaExecucao.PARCIALMENTE_CONCLUIDO, null, null,
                AGORA.minusHours(1), AGORA, 0);
    }

    private static UUID uuid(int fim) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(fim));
    }
}
