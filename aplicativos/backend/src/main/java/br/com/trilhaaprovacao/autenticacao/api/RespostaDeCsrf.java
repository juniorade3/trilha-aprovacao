package br.com.trilhaaprovacao.autenticacao.api;

public record RespostaDeCsrf(String token, String cabecalho, String parametro) {
}
