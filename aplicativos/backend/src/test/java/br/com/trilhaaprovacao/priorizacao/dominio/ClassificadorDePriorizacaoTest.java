package br.com.trilhaaprovacao.priorizacao.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ClassificadorDePriorizacaoTest {
    private static final LocalDate INICIO_DA_JANELA = LocalDate.of(2026, 6, 22);
    private static final LocalDate DATA_RECENTE = LocalDate.of(2026, 7, 21);

    @Test
    void deveClassificarLacunasNaOrdemObjetiva() {
        assertThat(classificar(sinais(0, 0, null, 0, null, null, null, 0, false)))
                .extracting(ClassificacaoDaPriorizacao::grupo,
                        ClassificacaoDaPriorizacao::faixa,
                        ClassificacaoDaPriorizacao::acaoSugerida)
                .containsExactly(GrupoDePriorizacao.LACUNA,
                        FaixaDePriorizacao.SEM_ESTUDO, AcaoSugerida.TEORIA);

        assertThat(classificar(sinais(1, 0, null, 0, null, null, null, 0, true)).faixa())
                .isEqualTo(FaixaDePriorizacao.SEM_EVIDENCIA);
        assertThat(classificar(sinais(1, 1, INICIO_DA_JANELA.minusDays(1),
                100, BigDecimal.valueOf(100), 5, 1, 1, true)).faixa())
                .isEqualTo(FaixaDePriorizacao.EVIDENCIA_DESATUALIZADA);
        assertThat(classificar(sinais(1, 1, DATA_RECENTE,
                19, BigDecimal.valueOf(100), null, 5, 0, true)).faixa())
                .isEqualTo(FaixaDePriorizacao.DADOS_INSUFICIENTES);
    }

    @Test
    void deveAplicarOsLimitesInclusivosDeQuestoesEPercentual() {
        assertThat(classificar(sinais(1, 1, DATA_RECENTE,
                20, new BigDecimal("69.99"), null, null, 0, true)).faixa())
                .isEqualTo(FaixaDePriorizacao.PRECISA_REFORCO);
        assertThat(classificar(sinais(1, 1, DATA_RECENTE,
                20, new BigDecimal("70.00"), null, null, 0, true)).faixa())
                .isEqualTo(FaixaDePriorizacao.DESEMPENHO_PARCIAL);
        assertThat(classificar(sinais(1, 1, DATA_RECENTE,
                20, new BigDecimal("84.99"), null, null, 0, true)).faixa())
                .isEqualTo(FaixaDePriorizacao.DESEMPENHO_PARCIAL);
        assertThat(classificar(sinais(1, 1, DATA_RECENTE,
                20, new BigDecimal("85.00"), null, null, 0, true)).faixa())
                .isEqualTo(FaixaDePriorizacao.CONSOLIDADO);
    }

    @Test
    void deveUsarPiorSinalEntreQuestoesRevisaoEPadraoRepetido() {
        assertThat(classificar(sinais(1, 2, DATA_RECENTE,
                20, BigDecimal.valueOf(95), 2, 1, 0, true)).faixa())
                .isEqualTo(FaixaDePriorizacao.PRECISA_REFORCO);
        assertThat(classificar(sinais(1, 2, DATA_RECENTE,
                20, BigDecimal.valueOf(95), 3, 1, 0, true)).faixa())
                .isEqualTo(FaixaDePriorizacao.DESEMPENHO_PARCIAL);
        assertThat(classificar(sinais(1, 2, DATA_RECENTE,
                20, BigDecimal.valueOf(95), 5, 1, 1, true)).faixa())
                .isEqualTo(FaixaDePriorizacao.PRECISA_REFORCO);
        assertThat(classificar(sinais(1, 2, DATA_RECENTE,
                0, null, 5, 1, 0, true)).faixa())
                .isEqualTo(FaixaDePriorizacao.CONSOLIDADO);
    }

    @Test
    void dificuldadeAltaEMaterialAusenteDevemApenasJustificar() {
        ClassificacaoDaPriorizacao classificacao = classificar(sinais(
                1, 1, DATA_RECENTE, 20, BigDecimal.valueOf(90),
                5, 5, 0, false));

        assertThat(classificacao.faixa()).isEqualTo(FaixaDePriorizacao.CONSOLIDADO);
        assertThat(classificacao.justificativas())
                .contains(JustificativaDaPriorizacao.DIFICULDADE_PERCEBIDA_ALTA,
                        JustificativaDaPriorizacao.SEM_MATERIAL_ATIVO);
    }

    @Test
    void deveTratarInicioDaJanelaComoInclusivoERejeitarSinaisInvalidos() {
        assertThat(classificar(sinais(1, 1, INICIO_DA_JANELA,
                19, BigDecimal.valueOf(50), null, null, 0, true)).faixa())
                .isEqualTo(FaixaDePriorizacao.DADOS_INSUFICIENTES);

        assertThatThrownBy(() -> sinais(-1, 0, null,
                0, null, null, null, 0, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sinais(1, 1, DATA_RECENTE,
                20, BigDecimal.valueOf(101), null, null, 0, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sinais(1, 1, DATA_RECENTE,
                20, BigDecimal.valueOf(80), 0, null, 0, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sinais(1, 1, DATA_RECENTE,
                20, BigDecimal.valueOf(80), null, 6, 0, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ClassificacaoDaPriorizacao classificar(SinaisDePriorizacao sinais) {
        return ClassificadorDePriorizacao.classificar(sinais, INICIO_DA_JANELA);
    }

    private SinaisDePriorizacao sinais(long estudos, long evidencias,
            LocalDate ultimaEvidencia, long questoes, BigDecimal percentual,
            Integer recordacao, Integer dificuldade, long padroes,
            boolean materialAtivo) {
        return new SinaisDePriorizacao(estudos, evidencias, ultimaEvidencia,
                questoes, percentual, recordacao, dificuldade, padroes, materialAtivo);
    }
}
