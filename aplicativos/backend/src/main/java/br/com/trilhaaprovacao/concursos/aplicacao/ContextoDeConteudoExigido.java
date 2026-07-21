package br.com.trilhaaprovacao.concursos.aplicacao;

import java.util.UUID;

public record ContextoDeConteudoExigido(
        UUID identificadorDoConcurso,
        String nomeDoConcurso,
        String orgaoDoConcurso,
        String bancaDoConcurso,
        String situacaoDoConcurso,
        UUID identificadorDoCargo,
        String nomeDoCargo,
        UUID identificadorDoEdital,
        String tituloDoEdital) {

    public boolean possuiCargoSelecionado() {
        return identificadorDoCargo != null;
    }

    public boolean possuiEditalPrincipal() {
        return identificadorDoEdital != null;
    }

    public boolean estaCompleto() {
        return identificadorDoConcurso != null
                && possuiCargoSelecionado()
                && possuiEditalPrincipal();
    }
}
