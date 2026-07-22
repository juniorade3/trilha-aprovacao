package br.com.trilhaaprovacao.automacao.infraestrutura;

import java.util.UUID;

public record ContextoDaChamadaMcp(
        IdentidadeDaIntegracaoMcp identidade,
        UUID identificadorDaCorrelacao,
        String identificadorDoEventoExterno) {
}
