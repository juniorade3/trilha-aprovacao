package br.com.trilhaaprovacao.planejamento.dominio;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Revisao especifica calculada pela agenda, sem estado duplicado no planejamento. */
public record CandidatoDeRevisaoParaGeracao(
        UUID identificadorDaMateria,
        UUID identificadorDoTopico,
        String nomeDaMateria,
        String nomeDoTopico,
        LocalDate dataDevida,
        int etapa,
        Integer ultimaRecordacao,
        int ordemDoTopico,
        boolean possuiBlocoAberto) {

    public CandidatoDeRevisaoParaGeracao {
        Objects.requireNonNull(identificadorDaMateria);
        Objects.requireNonNull(identificadorDoTopico);
        Objects.requireNonNull(nomeDaMateria);
        Objects.requireNonNull(nomeDoTopico);
        Objects.requireNonNull(dataDevida);
        if (etapa < 0 || etapa > 5) {
            throw new IllegalArgumentException("Etapa da revisao invalida.");
        }
        if (ultimaRecordacao != null
                && (ultimaRecordacao < 1 || ultimaRecordacao > 5)) {
            throw new IllegalArgumentException("Recordacao da revisao invalida.");
        }
        if (ordemDoTopico < 1) {
            throw new IllegalArgumentException("Ordem do topico invalida.");
        }
    }
}
