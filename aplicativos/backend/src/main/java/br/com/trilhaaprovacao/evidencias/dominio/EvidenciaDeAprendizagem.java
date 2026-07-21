package br.com.trilhaaprovacao.evidencias.dominio;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EvidenciaDeAprendizagem(
        UUID identificador,
        UUID identificadorDoRegistroDeEstudo,
        Integer quantidadeDeQuestoes,
        Integer quantidadeDeAcertos,
        Integer nivelDeRecordacao,
        Integer dificuldadePercebida,
        List<DadosDoPadraoDeErro> padroesDeErro,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public EvidenciaDeAprendizagem {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(identificadorDoRegistroDeEstudo);
        padroesDeErro = padroesDeErro == null ? List.of() : List.copyOf(padroesDeErro);
        if ((quantidadeDeQuestoes == null) != (quantidadeDeAcertos == null)) {
            throw new IllegalArgumentException("Questoes e acertos devem ser informados juntos.");
        }
        if (quantidadeDeQuestoes != null
                && (quantidadeDeQuestoes < 1 || quantidadeDeAcertos < 0
                || quantidadeDeAcertos > quantidadeDeQuestoes)) {
            throw new IllegalArgumentException("Quantidade de questoes e acertos e invalida.");
        }
        validarEscala(nivelDeRecordacao, "Nivel de recordacao");
        validarEscala(dificuldadePercebida, "Dificuldade percebida");
        if (quantidadeDeQuestoes == null && nivelDeRecordacao == null
                && dificuldadePercebida == null) {
            throw new IllegalArgumentException("Informe ao menos um resultado da aprendizagem.");
        }
        var normalizados = new HashSet<String>();
        long ocorrencias = 0;
        for (DadosDoPadraoDeErro padrao : padroesDeErro) {
            if (!normalizados.add(padrao.descricaoNormalizada())) {
                throw new IllegalArgumentException("Um padrao de erro nao pode ser repetido.");
            }
            ocorrencias += (long) padrao.quantidadeDeOcorrencias();
        }
        if (!padroesDeErro.isEmpty() && quantidadeDeQuestoes == null) {
            throw new IllegalArgumentException("Padroes de erro exigem resultado de questoes.");
        }
        int errosDerivados = quantidadeDeQuestoes == null
                ? 0 : quantidadeDeQuestoes - quantidadeDeAcertos;
        if (quantidadeDeQuestoes != null && ocorrencias > errosDerivados) {
            throw new IllegalArgumentException(
                    "A soma dos padroes nao pode superar a quantidade de erros.");
        }
        Objects.requireNonNull(criadoEm);
        Objects.requireNonNull(atualizadoEm);
    }

    public static EvidenciaDeAprendizagem criar(UUID registro, Integer questoes,
            Integer acertos, Integer recordacao, Integer dificuldade,
            List<DadosDoPadraoDeErro> padroes) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new EvidenciaDeAprendizagem(UUID.randomUUID(), registro, questoes,
                acertos, recordacao, dificuldade, padroes, agora, agora, 0);
    }

    public int quantidadeDeErros() {
        return quantidadeDeQuestoes == null ? 0 : quantidadeDeQuestoes - quantidadeDeAcertos;
    }

    public ResultadoDaRevisao resultadoDaRevisao() {
        return nivelDeRecordacao == null ? null : ResultadoDaRevisao.classificar(nivelDeRecordacao);
    }

    private static void validarEscala(Integer valor, String campo) {
        if (valor != null && (valor < 1 || valor > 5)) {
            throw new IllegalArgumentException(campo + " deve estar entre 1 e 5.");
        }
    }
}
