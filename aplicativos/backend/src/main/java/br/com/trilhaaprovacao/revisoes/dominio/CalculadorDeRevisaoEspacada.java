package br.com.trilhaaprovacao.revisoes.dominio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CalculadorDeRevisaoEspacada {
    private static final int[] INTERVALOS_EM_DIAS = {1, 3, 7, 14, 30, 60};
    private static final Comparator<EventoDeRevisaoEspacada> ORDEM_DOS_EVENTOS =
            Comparator.comparing(EventoDeRevisaoEspacada::instante)
                    .thenComparing(EventoDeRevisaoEspacada::identificadorDaEvidencia);

    private CalculadorDeRevisaoEspacada() {
    }

    public static Optional<RevisaoEspacadaCalculada> calcular(
            List<EventoDeRevisaoEspacada> eventosInformados) {
        if (eventosInformados == null) {
            throw new IllegalArgumentException("Eventos sao obrigatorios.");
        }
        if (eventosInformados.isEmpty()) {
            return Optional.empty();
        }

        List<EventoDeRevisaoEspacada> eventos = new ArrayList<>(eventosInformados);
        if (eventos.stream().anyMatch(evento -> evento == null)) {
            throw new IllegalArgumentException("Evento nao pode ser nulo.");
        }
        eventos.sort(ORDEM_DOS_EVENTOS);

        EventoDeRevisaoEspacada primeiro = eventos.getFirst();
        int etapa = 0;
        var dataBase = primeiro.data();
        java.time.LocalDate ultimaRevisao = null;
        Integer ultimaRecordacao = null;

        Map<java.time.LocalDate, EventoDeRevisaoEspacada> ultimaRevisaoPorDia =
                new LinkedHashMap<>();
        eventos.stream().filter(evento -> evento.revisao()
                        && evento.nivelDeRecordacao() != null)
                .forEach(evento -> ultimaRevisaoPorDia.merge(evento.data(), evento,
                        (atual, candidato) -> ORDEM_DOS_EVENTOS.compare(atual, candidato) < 0
                                ? candidato : atual));

        List<EventoDeRevisaoEspacada> revisoes = new ArrayList<>(
                ultimaRevisaoPorDia.values());
        revisoes.sort(ORDEM_DOS_EVENTOS);
        for (EventoDeRevisaoEspacada revisao : revisoes) {
            etapa = aplicarRecordacao(etapa, revisao.nivelDeRecordacao());
            dataBase = revisao.data();
            ultimaRevisao = revisao.data();
            ultimaRecordacao = revisao.nivelDeRecordacao();
        }

        int intervalo = intervaloDaEtapa(etapa);
        return Optional.of(new RevisaoEspacadaCalculada(etapa, intervalo,
                dataBase.plusDays(intervalo), ultimaRevisao, ultimaRecordacao));
    }

    public static int intervaloDaEtapa(int etapa) {
        if (etapa < 0 || etapa >= INTERVALOS_EM_DIAS.length) {
            throw new IllegalArgumentException("Etapa deve estar entre 0 e 5.");
        }
        return INTERVALOS_EM_DIAS[etapa];
    }

    private static int aplicarRecordacao(int etapa, Integer recordacao) {
        if (recordacao == null) {
            return etapa;
        }
        int novaEtapa = switch (recordacao) {
            case 1 -> 0;
            case 2 -> etapa - 1;
            case 3 -> etapa;
            case 4 -> etapa + 1;
            case 5 -> etapa + 2;
            default -> throw new IllegalArgumentException(
                    "Nivel de recordacao deve estar entre 1 e 5.");
        };
        return Math.max(0, Math.min(INTERVALOS_EM_DIAS.length - 1, novaEtapa));
    }
}
