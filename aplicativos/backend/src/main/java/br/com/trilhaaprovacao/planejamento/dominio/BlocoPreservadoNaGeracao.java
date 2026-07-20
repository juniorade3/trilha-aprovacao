package br.com.trilhaaprovacao.planejamento.dominio;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record BlocoPreservadoNaGeracao(
        UUID identificador,
        UUID identificadorDaMateria,
        String nomeDaMateria,
        String titulo,
        TipoDeAtividade tipoDeAtividade,
        LocalDate data,
        int duracaoEmMinutos,
        int ordem) {

    public BlocoPreservadoNaGeracao {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(titulo);
        Objects.requireNonNull(tipoDeAtividade);
        Objects.requireNonNull(data);
        if (duracaoEmMinutos < 1 || ordem < 1) {
            throw new IllegalArgumentException("Bloco preservado invalido.");
        }
    }
}
