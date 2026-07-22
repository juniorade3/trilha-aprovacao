package br.com.trilhaaprovacao.automacao.infraestrutura;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

public record IdentidadeDaIntegracaoMcp(
        UUID identificadorDoUsuario,
        UUID identificadorDoVinculo,
        UUID identificadorDaCredencial,
        long identificadorDoBot,
        long identificadorDoTelegram,
        String identificadorDoAgente,
        String identificadorDaSessao,
        long versaoDoVinculo,
        Set<String> escopos) implements Principal {

    public IdentidadeDaIntegracaoMcp {
        escopos = Set.copyOf(escopos);
    }

    @Override
    public String getName() {
        return identificadorDaCredencial.toString();
    }

    public boolean possuiEscopo(String escopo) {
        return escopos.contains(escopo);
    }
}
