package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ModoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.PoliticaDeReutilizacao;
import java.util.Objects;
import java.util.UUID;

public record SolicitacaoDeAplicacaoDaImportacao(
        UUID identificadorDaImportacao,
        UUID identificadorDoUsuario,
        ExtracaoEstruturadaDoEdital extracao,
        String chaveDoCargoSelecionado,
        ModoDaImportacaoDeEdital modo,
        UUID identificadorDoConcursoExistente,
        PoliticaDeReutilizacao politicaDeReutilizacao,
        DecisoesDaImportacaoDoEdital decisoes) {

    public SolicitacaoDeAplicacaoDaImportacao {
        Objects.requireNonNull(identificadorDaImportacao);
        Objects.requireNonNull(identificadorDoUsuario);
        Objects.requireNonNull(extracao);
        if (chaveDoCargoSelecionado == null
                || chaveDoCargoSelecionado.isBlank()) {
            throw new IllegalArgumentException("Cargo selecionado obrigatorio.");
        }
        Objects.requireNonNull(modo);
        Objects.requireNonNull(politicaDeReutilizacao);
        decisoes = decisoes == null
                ? DecisoesDaImportacaoDoEdital.vazias() : decisoes;
        if (modo == ModoDaImportacaoDeEdital.COMPLEMENTAR_EXISTENTE
                && identificadorDoConcursoExistente == null) {
            throw new IllegalArgumentException(
                    "Concurso existente obrigatorio no complemento.");
        }
        if (modo == ModoDaImportacaoDeEdital.CRIAR_NOVO
                && identificadorDoConcursoExistente != null) {
            throw new IllegalArgumentException(
                    "Concurso existente nao se aplica a criacao.");
        }
    }
}
