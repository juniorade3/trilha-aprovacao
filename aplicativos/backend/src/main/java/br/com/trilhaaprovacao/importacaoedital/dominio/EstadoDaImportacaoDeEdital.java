package br.com.trilhaaprovacao.importacaoedital.dominio;

public enum EstadoDaImportacaoDeEdital {
    RECEBIDA,
    EXTRAINDO,
    EXTRAIDA,
    AGUARDANDO_SELECAO,
    AGUARDANDO_CORRECOES,
    VALIDADA,
    AGUARDANDO_CONFIRMACAO,
    APLICANDO,
    APLICADA,
    FALHOU,
    CANCELADA
}
