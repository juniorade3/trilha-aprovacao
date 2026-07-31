package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.importacaoedital.dominio.TipoDaFonteDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ProblemaDaImportacao;
import java.util.List;

public record InspecaoDoArquivoDoEdital(
        String nomeDoArquivo,
        TipoDaFonteDoEdital tipoDaFonte,
        String tipoMime,
        String sha256,
        long tamanhoEmBytes,
        List<ProblemaDaImportacao> problemas) {

    public InspecaoDoArquivoDoEdital {
        problemas = problemas == null ? List.of() : List.copyOf(problemas);
    }
}
