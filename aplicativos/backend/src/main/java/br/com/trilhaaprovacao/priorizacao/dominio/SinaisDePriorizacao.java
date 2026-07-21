package br.com.trilhaaprovacao.priorizacao.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SinaisDePriorizacao(
        long quantidadeDeEstudos,
        long quantidadeDeEvidencias,
        LocalDate dataDaUltimaEvidencia,
        long quantidadeDeQuestoesRecentes,
        BigDecimal percentualRecenteDeAcertos,
        Integer recordacaoDaUltimaRevisaoRecente,
        Integer ultimaDificuldade,
        long quantidadeDePadroesRepetidosRecentes,
        boolean possuiMaterialAtivo) {

    public SinaisDePriorizacao {
        if (quantidadeDeEstudos < 0 || quantidadeDeEvidencias < 0
                || quantidadeDeQuestoesRecentes < 0
                || quantidadeDePadroesRepetidosRecentes < 0) {
            throw new IllegalArgumentException("As quantidades da priorizacao nao podem ser negativas.");
        }
        if (percentualRecenteDeAcertos != null
                && (percentualRecenteDeAcertos.signum() < 0
                || percentualRecenteDeAcertos.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException("O percentual de acertos deve estar entre zero e cem.");
        }
        validarEscala(recordacaoDaUltimaRevisaoRecente, "recordacao");
        validarEscala(ultimaDificuldade, "dificuldade");
    }

    private static void validarEscala(Integer valor, String nome) {
        if (valor != null && (valor < 1 || valor > 5)) {
            throw new IllegalArgumentException("A " + nome + " deve estar entre um e cinco.");
        }
    }
}
