package br.com.trilhaaprovacao.revisoes.dominio;

/**
 * Limites compartilhados entre a apresentacao da fila e a geracao de blocos de revisao.
 */
public final class ConfiguracaoDaFilaDeRevisoes {
    public static final int LIMITE_DE_PRIORIDADES_DA_FILA = 3;
    public static final int DURACAO_ESTIMADA_POR_REVISAO_EM_MINUTOS = 20;

    private ConfiguracaoDaFilaDeRevisoes() {
    }
}
