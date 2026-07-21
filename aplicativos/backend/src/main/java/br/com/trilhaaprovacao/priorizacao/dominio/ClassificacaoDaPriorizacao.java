package br.com.trilhaaprovacao.priorizacao.dominio;

import java.util.List;

public record ClassificacaoDaPriorizacao(
        GrupoDePriorizacao grupo,
        FaixaDePriorizacao faixa,
        AcaoSugerida acaoSugerida,
        List<JustificativaDaPriorizacao> justificativas) {

    public ClassificacaoDaPriorizacao {
        if (grupo == null || faixa == null || acaoSugerida == null) {
            throw new IllegalArgumentException("A classificacao da priorizacao deve estar completa.");
        }
        if (grupo != faixa.grupo() || acaoSugerida != faixa.acaoSugerida()) {
            throw new IllegalArgumentException("A classificacao da priorizacao e incoerente.");
        }
        justificativas = List.copyOf(justificativas);
    }
}
