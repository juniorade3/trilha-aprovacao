package br.com.trilhaaprovacao.priorizacao.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class ClassificadorDePriorizacao {
    public static final int QUANTIDADE_MINIMA_DE_QUESTOES = 20;
    public static final BigDecimal LIMITE_DE_REFORCO = BigDecimal.valueOf(70);
    public static final BigDecimal LIMITE_DE_CONSOLIDACAO = BigDecimal.valueOf(85);

    private ClassificadorDePriorizacao() {
    }

    public static ClassificacaoDaPriorizacao classificar(
            SinaisDePriorizacao sinais, LocalDate inicioDaJanelaRecente) {
        if (sinais == null || inicioDaJanelaRecente == null) {
            throw new IllegalArgumentException("Os sinais e a janela recente sao obrigatorios.");
        }

        FaixaDePriorizacao faixa = determinarFaixa(sinais, inicioDaJanelaRecente);
        List<JustificativaDaPriorizacao> justificativas = justificativas(
                sinais, faixa, inicioDaJanelaRecente);
        return new ClassificacaoDaPriorizacao(
                faixa.grupo(), faixa, faixa.acaoSugerida(), justificativas);
    }

    private static FaixaDePriorizacao determinarFaixa(
            SinaisDePriorizacao sinais, LocalDate inicioDaJanelaRecente) {
        if (sinais.quantidadeDeEstudos() == 0) {
            return FaixaDePriorizacao.SEM_ESTUDO;
        }
        if (sinais.quantidadeDeEvidencias() == 0
                || sinais.dataDaUltimaEvidencia() == null) {
            return FaixaDePriorizacao.SEM_EVIDENCIA;
        }
        if (sinais.dataDaUltimaEvidencia().isBefore(inicioDaJanelaRecente)) {
            return FaixaDePriorizacao.EVIDENCIA_DESATUALIZADA;
        }
        if (sinais.quantidadeDePadroesRepetidosRecentes() > 0) {
            return FaixaDePriorizacao.PRECISA_REFORCO;
        }

        Integer recordacao = sinais.recordacaoDaUltimaRevisaoRecente();
        if (recordacao != null && recordacao <= 2) {
            return FaixaDePriorizacao.PRECISA_REFORCO;
        }

        if (sinais.quantidadeDeQuestoesRecentes() >= QUANTIDADE_MINIMA_DE_QUESTOES) {
            BigDecimal percentual = sinais.percentualRecenteDeAcertos();
            if (percentual == null) {
                throw new IllegalArgumentException(
                        "O percentual recente e obrigatorio quando ha questoes suficientes.");
            }
            if (percentual.compareTo(LIMITE_DE_REFORCO) < 0) {
                return FaixaDePriorizacao.PRECISA_REFORCO;
            }
            if (percentual.compareTo(LIMITE_DE_CONSOLIDACAO) < 0) {
                return FaixaDePriorizacao.DESEMPENHO_PARCIAL;
            }
            if (recordacao != null && recordacao == 3) {
                return FaixaDePriorizacao.DESEMPENHO_PARCIAL;
            }
            return FaixaDePriorizacao.CONSOLIDADO;
        }

        if (recordacao != null) {
            return recordacao == 3
                    ? FaixaDePriorizacao.DESEMPENHO_PARCIAL
                    : FaixaDePriorizacao.CONSOLIDADO;
        }
        return FaixaDePriorizacao.DADOS_INSUFICIENTES;
    }

    private static List<JustificativaDaPriorizacao> justificativas(
            SinaisDePriorizacao sinais, FaixaDePriorizacao faixa,
            LocalDate inicioDaJanelaRecente) {
        List<JustificativaDaPriorizacao> resultado = new ArrayList<>();
        switch (faixa) {
            case SEM_ESTUDO -> resultado.add(
                    JustificativaDaPriorizacao.TOPICO_NUNCA_ESTUDADO);
            case SEM_EVIDENCIA -> resultado.add(
                    JustificativaDaPriorizacao.ESTUDO_SEM_EVIDENCIA);
            case EVIDENCIA_DESATUALIZADA -> resultado.add(
                    JustificativaDaPriorizacao.EVIDENCIA_FORA_DA_JANELA_RECENTE);
            case DADOS_INSUFICIENTES -> resultado.add(
                    JustificativaDaPriorizacao.QUESTOES_RECENTES_INSUFICIENTES);
            case PRECISA_REFORCO, DESEMPENHO_PARCIAL, CONSOLIDADO ->
                    adicionarJustificativaDoPercentual(resultado, sinais);
        }

        Integer recordacao = sinais.recordacaoDaUltimaRevisaoRecente();
        if (recordacao != null) {
            if (recordacao <= 2) {
                resultado.add(JustificativaDaPriorizacao.RECORDACAO_RECENTE_BAIXA);
            } else if (recordacao == 3) {
                resultado.add(JustificativaDaPriorizacao.RECORDACAO_RECENTE_PARCIAL);
            } else {
                resultado.add(JustificativaDaPriorizacao.RECORDACAO_RECENTE_ALTA);
            }
        }
        if (sinais.quantidadeDePadroesRepetidosRecentes() > 0) {
            resultado.add(JustificativaDaPriorizacao.PADRAO_DE_ERRO_REPETIDO);
        }
        if (sinais.ultimaDificuldade() != null && sinais.ultimaDificuldade() >= 4) {
            resultado.add(JustificativaDaPriorizacao.DIFICULDADE_PERCEBIDA_ALTA);
        }
        if (!sinais.possuiMaterialAtivo()) {
            resultado.add(JustificativaDaPriorizacao.SEM_MATERIAL_ATIVO);
        }
        return List.copyOf(resultado);
    }

    private static void adicionarJustificativaDoPercentual(
            List<JustificativaDaPriorizacao> resultado,
            SinaisDePriorizacao sinais) {
        BigDecimal percentual = sinais.percentualRecenteDeAcertos();
        if (sinais.quantidadeDeQuestoesRecentes() < QUANTIDADE_MINIMA_DE_QUESTOES
                || percentual == null) {
            resultado.add(JustificativaDaPriorizacao.QUESTOES_RECENTES_INSUFICIENTES);
        } else if (percentual.compareTo(LIMITE_DE_REFORCO) < 0) {
            resultado.add(JustificativaDaPriorizacao.PERCENTUAL_RECENTE_ABAIXO_DE_SETENTA);
        } else if (percentual.compareTo(LIMITE_DE_CONSOLIDACAO) < 0) {
            resultado.add(
                    JustificativaDaPriorizacao.PERCENTUAL_RECENTE_ENTRE_SETENTA_E_OITENTA_E_CINCO);
        } else {
            resultado.add(
                    JustificativaDaPriorizacao.PERCENTUAL_RECENTE_A_PARTIR_DE_OITENTA_E_CINCO);
        }
    }
}
