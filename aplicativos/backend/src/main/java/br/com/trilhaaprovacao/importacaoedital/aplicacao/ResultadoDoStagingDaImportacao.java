package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ProblemaDaImportacao;
import java.util.List;

public record ResultadoDoStagingDaImportacao(
        ImportacaoDeEdital importacao,
        ExtracaoEstruturadaDoEdital extracao,
        List<ProblemaDaImportacao> problemas) {

    public ResultadoDoStagingDaImportacao {
        problemas = problemas == null ? List.of() : List.copyOf(problemas);
    }
}
