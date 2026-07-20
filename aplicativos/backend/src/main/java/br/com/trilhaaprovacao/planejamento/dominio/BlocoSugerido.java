package br.com.trilhaaprovacao.planejamento.dominio;

import java.util.List;
import java.util.UUID;

public record BlocoSugerido(
        UUID identificadorDaMateria,
        String nomeDaMateria,
        String titulo,
        TipoDeAtividade tipoDeAtividade,
        int duracaoEmMinutos,
        List<JustificativaDaGeracao> justificativas) {

    public BlocoSugerido {
        justificativas = List.copyOf(justificativas);
        if (duracaoEmMinutos < 1) throw new IllegalArgumentException("Duracao invalida.");
    }
}
