package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.importacaoedital.dominio.ModoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.PoliticaDeReutilizacao;
import java.util.Objects;
import java.util.UUID;

public record ConsultaDaImportacaoDeEdital(
        ResultadoDoStagingDaImportacao staging,
        ModoDaImportacaoDeEdital modo,
        UUID identificadorDoConcursoExistente,
        PoliticaDeReutilizacao politicaDeReutilizacao,
        UUID identificadorDaOperacaoAssistida,
        int tentativaDaPreparacao) {

    public ConsultaDaImportacaoDeEdital {
        Objects.requireNonNull(staging);
        if (tentativaDaPreparacao < 1) {
            throw new IllegalArgumentException(
                    "Tentativa da preparacao invalida.");
        }
    }
}
