package br.com.trilhaaprovacao.evidencias.api;

import br.com.trilhaaprovacao.evidencias.aplicacao.DadosDaEvidencia;
import br.com.trilhaaprovacao.evidencias.dominio.DadosDoPadraoDeErro;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RequisicaoDeEvidencia(
        @Valid RequisicaoDeResultadoDeQuestoes resultadoDeQuestoes,
        @Min(1) @Max(5) Integer nivelDeRecordacao,
        @Min(1) @Max(5) Integer dificuldadePercebida,
        List<@NotNull @Valid RequisicaoDePadraoDeErro> padroesDeErro) {

    public DadosDaEvidencia paraDados() {
        return new DadosDaEvidencia(
                resultadoDeQuestoes == null ? null : resultadoDeQuestoes.quantidadeDeQuestoes(),
                resultadoDeQuestoes == null ? null : resultadoDeQuestoes.quantidadeDeAcertos(),
                nivelDeRecordacao, dificuldadePercebida,
                padroesDeErro == null ? List.of() : padroesDeErro.stream()
                        .map(p -> new DadosDoPadraoDeErro(
                                p.descricao(), p.quantidadeDeOcorrencias()))
                        .toList());
    }
}
