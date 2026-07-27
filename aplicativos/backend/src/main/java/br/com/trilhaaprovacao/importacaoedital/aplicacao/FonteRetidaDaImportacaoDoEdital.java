package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.TipoDaFonteDoEdital;

public record FonteRetidaDaImportacaoDoEdital(
        int versaoDaExtracao,
        TipoDaFonteDoEdital tipoDaFonte,
        String nomeDoArquivo,
        byte[] conteudoOriginal,
        String textoExtraido,
        ExtracaoEstruturadaDoEdital extracaoAtual) {

    public FonteRetidaDaImportacaoDoEdital {
        conteudoOriginal = conteudoOriginal == null
                ? new byte[0] : conteudoOriginal.clone();
    }

    @Override
    public byte[] conteudoOriginal() {
        return conteudoOriginal.clone();
    }
}
