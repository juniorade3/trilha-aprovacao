package br.com.trilhaaprovacao.evidencias.api;

import br.com.trilhaaprovacao.evidencias.dominio.EvidenciaDeAprendizagem;
import br.com.trilhaaprovacao.evidencias.dominio.ResultadoDaRevisao;
import java.util.List;
import java.util.UUID;

public record RespostaDeEvidencia(
        UUID identificador,
        RequisicaoDeResultadoDeQuestoes resultadoDeQuestoes,
        Integer quantidadeDeErros,
        Integer nivelDeRecordacao,
        Integer dificuldadePercebida,
        ResultadoDaRevisao resultadoDaRevisao,
        List<RequisicaoDePadraoDeErro> padroesDeErro) {

    public static RespostaDeEvidencia de(EvidenciaDeAprendizagem evidencia) {
        if (evidencia == null) {
            return null;
        }
        return new RespostaDeEvidencia(evidencia.identificador(),
                evidencia.quantidadeDeQuestoes() == null ? null
                        : new RequisicaoDeResultadoDeQuestoes(
                                evidencia.quantidadeDeQuestoes(), evidencia.quantidadeDeAcertos()),
                evidencia.quantidadeDeQuestoes() == null ? null : evidencia.quantidadeDeErros(),
                evidencia.nivelDeRecordacao(), evidencia.dificuldadePercebida(),
                evidencia.resultadoDaRevisao(), evidencia.padroesDeErro().stream()
                        .map(p -> new RequisicaoDePadraoDeErro(
                                p.descricao(), p.quantidadeDeOcorrencias()))
                        .toList());
    }
}
