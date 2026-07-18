package br.com.trilhaaprovacao.autenticacao.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.UsuarioDaAplicacao;
import java.util.UUID;

public record RespostaDeUsuario(UUID identificador, String nome, String email) {
    static RespostaDeUsuario de(UsuarioDaAplicacao usuario) {
        return new RespostaDeUsuario(usuario.identificador(), usuario.nome(), usuario.email());
    }
}
