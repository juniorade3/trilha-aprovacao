package br.com.trilhaaprovacao.planejamento.dominio;

import java.util.Objects;
import java.util.UUID;

public record CandidatoDeMateriaParaGeracao(
        UUID identificadorDaMateria,
        String nome,
        String nomeNormalizado,
        int ordemEstavel,
        PrioridadeDaMateriaNoPlano prioridade) {

    public CandidatoDeMateriaParaGeracao {
        Objects.requireNonNull(identificadorDaMateria);
        Objects.requireNonNull(nome);
        Objects.requireNonNull(nomeNormalizado);
        Objects.requireNonNull(prioridade);
        if (ordemEstavel < 1) throw new IllegalArgumentException("Ordem estavel invalida.");
    }
}
