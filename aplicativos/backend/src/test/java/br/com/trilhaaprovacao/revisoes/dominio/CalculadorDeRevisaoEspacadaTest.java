package br.com.trilhaaprovacao.revisoes.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CalculadorDeRevisaoEspacadaTest {
    private static final LocalDate INICIO = LocalDate.of(2026, 7, 1);

    @Test
    void deveUsarOsSeisIntervalosDasEtapas() {
        assertThat(List.of(0, 1, 2, 3, 4, 5).stream()
                .map(CalculadorDeRevisaoEspacada::intervaloDaEtapa))
                .containsExactly(1, 3, 7, 14, 30, 60);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CalculadorDeRevisaoEspacada.intervaloDaEtapa(-1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CalculadorDeRevisaoEspacada.intervaloDaEtapa(6));
    }

    @Test
    void deveIniciarNaPrimeiraEvidenciaEIgnorarFatosComunsPosteriores() {
        var calculo = CalculadorDeRevisaoEspacada.calcular(List.of(
                evento(1, INICIO, 8, false, null),
                evento(2, INICIO.plusDays(5), 9, false, 5))).orElseThrow();

        assertThat(calculo.etapa()).isZero();
        assertThat(calculo.intervaloEmDias()).isEqualTo(1);
        assertThat(calculo.dataDevida()).isEqualTo(INICIO.plusDays(1));
        assertThat(calculo.ultimaRevisao()).isNull();
        assertThat(calculo.ultimaRecordacao()).isNull();
    }

    @Test
    void deveAplicarARecordacaoQuandoAPrimeiraEvidenciaForRevisao() {
        var calculo = CalculadorDeRevisaoEspacada.calcular(List.of(
                evento(1, INICIO, 8, true, 4))).orElseThrow();

        assertThat(calculo.etapa()).isEqualTo(1);
        assertThat(calculo.intervaloEmDias()).isEqualTo(3);
        assertThat(calculo.dataDevida()).isEqualTo(INICIO.plusDays(3));
        assertThat(calculo.ultimaRevisao()).isEqualTo(INICIO);
        assertThat(calculo.ultimaRecordacao()).isEqualTo(4);
    }

    @Test
    void deveAplicarTodasAsTransicoesComLimitesEntreZeroECinco() {
        List<EventoDeRevisaoEspacada> eventos = new ArrayList<>();
        eventos.add(evento(1, INICIO, 8, false, null));

        adicionarEVerificar(eventos, 2, 1, 5, 2);
        adicionarEVerificar(eventos, 3, 2, 5, 4);
        adicionarEVerificar(eventos, 4, 3, 5, 5);
        adicionarEVerificar(eventos, 5, 4, 4, 5);
        adicionarEVerificar(eventos, 6, 5, 2, 4);
        adicionarEVerificar(eventos, 7, 6, 3, 4);
        adicionarEVerificar(eventos, 8, 7, 1, 0);
        adicionarEVerificar(eventos, 9, 8, 2, 0);
    }

    @Test
    void deveConsiderarSomenteAUltimaRevisaoDoDiaPorInstanteEIdentificador() {
        UUID menor = new UUID(0, 10);
        UUID maior = new UUID(0, 11);
        var calculo = CalculadorDeRevisaoEspacada.calcular(List.of(
                evento(1, INICIO, 8, false, null),
                evento(2, INICIO.plusDays(1), 9, true, 5),
                evento(3, INICIO.plusDays(1), 10, true, 1),
                evento(menor, INICIO.plusDays(1), 11, true, 1),
                evento(maior, INICIO.plusDays(1), 11, true, 4))).orElseThrow();

        assertThat(calculo.etapa()).isEqualTo(1);
        assertThat(calculo.dataDevida()).isEqualTo(INICIO.plusDays(4));
        assertThat(calculo.ultimaRecordacao()).isEqualTo(4);
    }

    @Test
    void revisaoSemRecordacaoNaoDeveAlterarEtapaNemDataBase() {
        var calculo = CalculadorDeRevisaoEspacada.calcular(List.of(
                evento(1, INICIO, 8, false, null),
                evento(2, INICIO.plusDays(1), 8, true, 4),
                evento(3, INICIO.plusDays(2), 8, true, null),
                evento(4, INICIO.plusDays(3), 8, false, 5))).orElseThrow();

        assertThat(calculo.etapa()).isEqualTo(1);
        assertThat(calculo.dataDevida()).isEqualTo(INICIO.plusDays(4));
        assertThat(calculo.ultimaRevisao()).isEqualTo(INICIO.plusDays(1));
        assertThat(calculo.ultimaRecordacao()).isEqualTo(4);
    }

    @Test
    void deveOrdenarEventosRecebidosForaDeOrdem() {
        EventoDeRevisaoEspacada inicial = evento(1, INICIO, 8, false, null);
        EventoDeRevisaoEspacada avancou = evento(
                2, INICIO.plusDays(1), 8, true, 5);
        EventoDeRevisaoEspacada recuou = evento(
                3, INICIO.plusDays(2), 8, true, 2);

        var emOrdem = CalculadorDeRevisaoEspacada.calcular(
                List.of(inicial, avancou, recuou));
        var foraDeOrdem = CalculadorDeRevisaoEspacada.calcular(
                List.of(recuou, inicial, avancou));

        assertThat(foraDeOrdem).isEqualTo(emOrdem);
        assertThat(foraDeOrdem.orElseThrow().etapa()).isEqualTo(1);
    }

    @Test
    void deveRetornarVazioSemEvidencias() {
        assertThat(CalculadorDeRevisaoEspacada.calcular(List.of())).isEmpty();
    }

    private void adicionarEVerificar(List<EventoDeRevisaoEspacada> eventos,
            int identificador, int dias, int recordacao, int etapaEsperada) {
        eventos.add(evento(identificador, INICIO.plusDays(dias), 8, true, recordacao));
        assertThat(CalculadorDeRevisaoEspacada.calcular(eventos).orElseThrow().etapa())
                .isEqualTo(etapaEsperada);
    }

    private EventoDeRevisaoEspacada evento(int identificador, LocalDate data,
            int hora, boolean revisao, Integer recordacao) {
        return evento(new UUID(0, identificador), data, hora, revisao, recordacao);
    }

    private EventoDeRevisaoEspacada evento(UUID identificador, LocalDate data,
            int hora, boolean revisao, Integer recordacao) {
        return new EventoDeRevisaoEspacada(identificador,
                data.atTime(hora, 0).atOffset(ZoneOffset.UTC), data,
                revisao, recordacao);
    }
}
