package br.com.trilhaaprovacao.autenticacao.aplicacao;

import java.util.UUID;

public record UsuarioDaAplicacao(UUID identificador, String nome, String email) {
}
