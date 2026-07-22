package br.com.trilhaaprovacao.automacao.dominio;

public enum EstadoDaOperacaoAssistida {
    PREPARADA,
    AGUARDANDO_CONFIRMACAO,
    CONFIRMADA,
    APLICADA,
    CANCELADA,
    EXPIRADA,
    FALHOU
}
