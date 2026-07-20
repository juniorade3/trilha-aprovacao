package br.com.trilhaaprovacao.planejamento.dominio;

import java.time.LocalDate;
import java.util.List;

public record EntradaDoDiaParaGeracao(
        LocalDate data,
        int minutosDisponiveis,
        List<BlocoPreservadoNaGeracao> blocosPreservados) {

    public EntradaDoDiaParaGeracao {
        if (minutosDisponiveis < 0) throw new IllegalArgumentException("Disponibilidade invalida.");
        blocosPreservados = List.copyOf(blocosPreservados);
    }
}
