package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.importacaoedital.dominio.ProblemaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.TipoDaFonteDoEdital;
import java.util.List;

public record ResultadoDaExtracaoDoArquivo(
        TipoDaFonteDoEdital tipoDaFonte,
        String texto,
        int quantidadeDePaginas,
        List<ProblemaDaImportacao> problemas) {

    public ResultadoDaExtracaoDoArquivo {
        problemas = problemas == null ? List.of() : List.copyOf(problemas);
    }
}
