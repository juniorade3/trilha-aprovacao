package br.com.trilhaaprovacao.autenticacao.aplicacao;

import br.com.trilhaaprovacao.autenticacao.infraestrutura.RepositorioDeUsuarios;
import br.com.trilhaaprovacao.autenticacao.infraestrutura.SituacaoDoUsuario;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class IdentidadeDoUsuarioAtual {
    private final RepositorioDeUsuarios repositorioDeUsuarios;

    public IdentidadeDoUsuarioAtual(RepositorioDeUsuarios repositorioDeUsuarios) {
        this.repositorioDeUsuarios = repositorioDeUsuarios;
    }

    public UUID obter(Authentication autenticacao) {
        return repositorioDeUsuarios.findByEmail(autenticacao.getName())
                .filter(usuario -> usuario.situacao() == SituacaoDoUsuario.ATIVO)
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "USUARIO_DA_SESSAO_NAO_ENCONTRADO", "A conta da sessao nao foi encontrada."))
                .identificador();
    }
}
