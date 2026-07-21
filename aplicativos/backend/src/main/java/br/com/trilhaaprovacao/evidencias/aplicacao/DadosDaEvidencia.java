package br.com.trilhaaprovacao.evidencias.aplicacao;

import br.com.trilhaaprovacao.evidencias.dominio.DadosDoPadraoDeErro;
import java.util.List;

public record DadosDaEvidencia(
        Integer quantidadeDeQuestoes,
        Integer quantidadeDeAcertos,
        Integer nivelDeRecordacao,
        Integer dificuldadePercebida,
        List<DadosDoPadraoDeErro> padroesDeErro) {

    public DadosDaEvidencia {
        padroesDeErro = padroesDeErro == null ? List.of() : List.copyOf(padroesDeErro);
    }
}
