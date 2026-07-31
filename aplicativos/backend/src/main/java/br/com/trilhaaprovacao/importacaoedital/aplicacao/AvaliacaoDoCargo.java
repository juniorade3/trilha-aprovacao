package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.importacaoedital.dominio.ProblemaDaImportacao;
import java.util.List;

public record AvaliacaoDoCargo(
        String chaveDoCargo,
        boolean pronto,
        List<ProblemaDaImportacao> problemas) {

    public AvaliacaoDoCargo {
        if (chaveDoCargo == null || chaveDoCargo.isBlank()) {
            throw new IllegalArgumentException("Chave do cargo obrigatoria.");
        }
        chaveDoCargo = chaveDoCargo.strip();
        problemas = problemas == null ? List.of() : List.copyOf(problemas);
    }
}
