package br.com.trilhaaprovacao.autenticacao.api;

import br.com.trilhaaprovacao.autenticacao.infraestrutura.UsuarioPersistido;
import java.util.UUID;

public record RespostaDeUsuario(UUID identificador, String nome, String email) {
    static RespostaDeUsuario de(UsuarioPersistido usuario) {
        return new RespostaDeUsuario(usuario.identificador(), usuario.nome(), usuario.email());
    }
}
