package br.com.trilhaaprovacao.autenticacao.api;

public record RespostaDeSessao(boolean autenticada, RespostaDeUsuario usuario) {
}
