package br.com.trilhaaprovacao.automacao.dominio;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record EventoDeAuditoriaDaAutomacao(
        UUID identificador,
        UUID identificadorDoUsuario,
        UUID identificadorDoVinculo,
        UUID identificadorDaOperacao,
        String ator,
        String ferramenta,
        String acao,
        String hashDaEntrada,
        String hashDaSaida,
        String fonte,
        String resultado,
        UUID identificadorDeCorrelacao,
        String metadados,
        OffsetDateTime ocorridoEm) {

    public EventoDeAuditoriaDaAutomacao {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(identificadorDoUsuario);
        ator = texto(ator, "Ator");
        acao = texto(acao, "Acao");
        fonte = texto(fonte, "Fonte");
        resultado = texto(resultado, "Resultado");
        Objects.requireNonNull(identificadorDeCorrelacao);
        metadados = metadados == null || metadados.isBlank() ? "{}" : metadados.trim();
        Objects.requireNonNull(ocorridoEm);
    }

    public static EventoDeAuditoriaDaAutomacao criar(UUID usuario, UUID vinculo,
            UUID operacao, String ator, String ferramenta, String acao,
            String hashDaEntrada, String hashDaSaida, String fonte,
            String resultado, UUID correlacao, String metadados,
            OffsetDateTime agora) {
        return new EventoDeAuditoriaDaAutomacao(UUID.randomUUID(), usuario, vinculo,
                operacao, ator, ferramenta, acao, hashDaEntrada, hashDaSaida,
                fonte, resultado, correlacao, metadados, agora);
    }

    private static String texto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " e obrigatorio.");
        }
        return valor.trim();
    }
}
