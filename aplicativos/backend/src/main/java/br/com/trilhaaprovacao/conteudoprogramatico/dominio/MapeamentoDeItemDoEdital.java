package br.com.trilhaaprovacao.conteudoprogramatico.dominio;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record MapeamentoDeItemDoEdital(
        UUID identificador,
        UUID identificadorDoItemDoEdital,
        UUID identificadorDoTopicoDaMateria,
        boolean confirmado,
        OffsetDateTime criadoEm) {

    public MapeamentoDeItemDoEdital {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(identificadorDoItemDoEdital);
        Objects.requireNonNull(identificadorDoTopicoDaMateria);
        Objects.requireNonNull(criadoEm);
    }

    public static MapeamentoDeItemDoEdital criarManual(UUID item, UUID topico) {
        return new MapeamentoDeItemDoEdital(
                UUID.randomUUID(), item, topico, true, OffsetDateTime.now());
    }

    public static MapeamentoDeItemDoEdital criarSugerido(UUID item, UUID topico) {
        return new MapeamentoDeItemDoEdital(
                UUID.randomUUID(), item, topico, false, OffsetDateTime.now());
    }
}
