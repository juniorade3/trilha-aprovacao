package br.com.trilhaaprovacao.compartilhado.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InformacoesDaAplicacaoTest {

    @Test
    void deveExporIdentidadeDaAplicacao() {
        InformacoesDaAplicacao informacoes = InformacoesDaAplicacao.daFundacao();

        assertThat(informacoes.nome()).isEqualTo("Trilha da Aprovacao");
        assertThat(informacoes.versao()).isEqualTo("v1");
    }
}
