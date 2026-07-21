package br.com.trilhaaprovacao.estudos.dominio;

public enum TipoDeEstudo {
    TEORIA,
    QUESTOES,
    REVISAO,
    CADERNO_DE_ERROS,
    SIMULADO,
    DISCURSIVA,
    OUTRA;

    public boolean exigeResultadoDeQuestoes() {
        return this == QUESTOES || this == SIMULADO || this == CADERNO_DE_ERROS;
    }

    public boolean exigeRecordacao() {
        return this == REVISAO;
    }
}
