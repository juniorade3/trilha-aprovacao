package br.com.trilhaaprovacao.automacao.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ServicoDeSegredosDaAutomacaoTest {

    @Test
    void deveExigirSegredoComAoMenosTrintaEDoisBytesAoHabilitar() {
        assertThatThrownBy(() -> new ServicoDeSegredosDaAutomacao(
                "segredo-curto", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SEGREDO_DE_HASH_DA_AUTOMACAO")
                .hasMessageContaining("32 bytes UTF-8");

        var servico = new ServicoDeSegredosDaAutomacao("á".repeat(16), true);
        assertThat(servico.configurado()).isTrue();
        assertThat(servico.hash("valor")).hasSize(64);
    }

    @Test
    void devePreservarAplicacaoDesabilitadaEFalharClaramenteNoUso() {
        var semSegredo = new ServicoDeSegredosDaAutomacao("", false);
        assertThat(semSegredo.configurado()).isFalse();
        assertThatCode(semSegredo::gerarCodigoDeVinculo).doesNotThrowAnyException();

        assertThatThrownBy(() -> semSegredo.hash("valor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SEGREDO_DE_HASH_DA_AUTOMACAO");

        var segredoCurto = new ServicoDeSegredosDaAutomacao("curto", false);
        assertThat(segredoCurto.configurado()).isFalse();
        assertThatThrownBy(() -> segredoCurto.derivarToken("material"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes UTF-8");
    }
}
