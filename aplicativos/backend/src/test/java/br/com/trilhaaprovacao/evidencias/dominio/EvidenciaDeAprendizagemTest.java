package br.com.trilhaaprovacao.evidencias.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidenciaDeAprendizagemTest {
    @Test
    void deveDerivarErrosEClassificarRevisao() {
        var evidencia = EvidenciaDeAprendizagem.criar(UUID.randomUUID(),
                20, 14, 3, 4,
                List.of(new DadosDoPadraoDeErro("Erro de sinal", 2)));

        assertThat(evidencia.quantidadeDeErros()).isEqualTo(6);
        assertThat(evidencia.resultadoDaRevisao()).isEqualTo(ResultadoDaRevisao.PARCIAL);
        assertThat(ResultadoDaRevisao.classificar(1)).isEqualTo(ResultadoDaRevisao.PRECISA_REFORCO);
        assertThat(ResultadoDaRevisao.classificar(5)).isEqualTo(ResultadoDaRevisao.CONSOLIDADA);
    }

    @Test
    void deveNormalizarEDetectarPadroesDuplicados() {
        assertThat(NormalizacaoDePadraoDeErro.normalizar("  Erro   de Acentuação  "))
                .isEqualTo("erro de acentuacao");
        assertThatThrownBy(() -> EvidenciaDeAprendizagem.criar(UUID.randomUUID(),
                10, 7, null, null, List.of(
                        new DadosDoPadraoDeErro("Erro de sinal", 1),
                        new DadosDoPadraoDeErro("  erro   de sinal ", 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao pode ser repetido");
    }

    @Test
    void deveValidarEscalasAcertosEOcorrencias() {
        assertThatThrownBy(() -> EvidenciaDeAprendizagem.criar(UUID.randomUUID(),
                5, 6, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EvidenciaDeAprendizagem.criar(UUID.randomUUID(),
                10, 9, 6, null,
                List.of(new DadosDoPadraoDeErro("Conceito trocado", 2))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EvidenciaDeAprendizagem.criar(UUID.randomUUID(),
                Integer.MAX_VALUE, 0, null, null, List.of(
                        new DadosDoPadraoDeErro("Primeiro padrão", Integer.MAX_VALUE),
                        new DadosDoPadraoDeErro("Segundo padrão", Integer.MAX_VALUE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao pode superar");
    }
}
