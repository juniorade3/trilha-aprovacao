package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import java.util.Map;
import java.util.UUID;

/** Decisoes humanas referenciadas por chaves estaveis da extracao. */
public record DecisoesDaImportacaoDoEdital(
        Map<String, UUID> recursosParaReutilizar,
        boolean definirEditalComoPrincipal,
        boolean selecionarCargoCriado) {

    public DecisoesDaImportacaoDoEdital {
        recursosParaReutilizar = recursosParaReutilizar == null
                ? Map.of() : Map.copyOf(recursosParaReutilizar);
        if (recursosParaReutilizar.keySet().stream().anyMatch(
                chave -> chave == null || chave.isBlank())) {
            throw new IllegalArgumentException(
                    "Chave da decisao de reutilizacao invalida.");
        }
        if (recursosParaReutilizar.values().stream().anyMatch(
                java.util.Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Recurso da decisao de reutilizacao invalido.");
        }
    }

    public static DecisoesDaImportacaoDoEdital vazias() {
        return new DecisoesDaImportacaoDoEdital(Map.of(), false, false);
    }
}
