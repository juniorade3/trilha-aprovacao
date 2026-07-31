package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.trilhaaprovacao.concursos.dominio.NivelDeEscolaridade;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.CargoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ConcursoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.EditalExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.FonteDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ProvenienciaDoDado;
import br.com.trilhaaprovacao.importacaoedital.dominio.ValorExtraido;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SanitizadorDaCorrecaoDoEditalTest {

    @Test
    void preservaMetadadoOriginalSomenteQuandoValorNaoMudou() {
        ProvenienciaDoDado fonteOriginal = new ProvenienciaDoDado(
                7, "CARGO", "Analista de dados");
        ExtracaoEstruturadaDoEdital anterior = extracao(
                valor("Edital 1", fonteOriginal),
                valor("Analista", fonteOriginal));
        ProvenienciaDoDado fonteForjada = new ProvenienciaDoDado(
                99, "CONFIÁVEL", "Trecho inventado pelo navegador");
        ExtracaoEstruturadaDoEdital recebida = extracao(
                valorComConfianca("Edital 1", fonteForjada,
                        new BigDecimal("0.0100"), true),
                valorComConfianca("Engenheiro de Dados", fonteForjada,
                        new BigDecimal("0.9900"), true));

        ExtracaoEstruturadaDoEdital sanitizada =
                new SanitizadorDaCorrecaoDoEdital().sanitizar(
                        anterior, recebida);

        assertThat(sanitizada.edital().titulo())
                .isSameAs(anterior.edital().titulo());
        ValorExtraido<String> nome = sanitizada.cargos().getFirst().nome();
        assertThat(nome.valor()).isEqualTo("Engenheiro de Dados");
        assertThat(nome.confianca()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(nome.inferido()).isFalse();
        assertThat(nome.fonte().pagina()).isNull();
        assertThat(nome.fonte().secao()).isEqualTo("Correção do usuário");
        assertThat(nome.fonte().trecho())
                .isEqualTo("Valor informado pelo usuário.");
    }

    @Test
    void marcaComoCorrecaoHumanaValorDeRecursoNovo() {
        ExtracaoEstruturadaDoEdital anterior = extracao(
                valor("Edital 1", new ProvenienciaDoDado(
                        1, "EDITAL", "Edital 1")),
                valor("Analista", new ProvenienciaDoDado(
                        1, "CARGO", "Analista")));
        ExtracaoEstruturadaDoEdital recebida =
                new ExtracaoEstruturadaDoEdital(
                        anterior.versaoDoContrato(), anterior.fonte(),
                        anterior.concurso(), anterior.edital(),
                        List.of(anterior.cargos().getFirst(),
                                new CargoExtraido("cargo-manual",
                                        valorComConfianca("Auditor", null,
                                                BigDecimal.ZERO, true),
                                        valorComConfianca(null, null,
                                                BigDecimal.ZERO, false),
                                        valorComConfianca(null, null,
                                                BigDecimal.ZERO, false),
                                        valorComConfianca(
                                                NivelDeEscolaridade.SUPERIOR,
                                                null, BigDecimal.ZERO, true),
                                        2)),
                        List.of(), List.of(), List.of(), List.of());

        ExtracaoEstruturadaDoEdital sanitizada =
                new SanitizadorDaCorrecaoDoEdital().sanitizar(
                        anterior, recebida);

        CargoExtraido novo = sanitizada.cargos().get(1);
        assertThat(novo.nome().fonte().secao())
                .isEqualTo("Correção do usuário");
        assertThat(novo.nivelDeEscolaridade().fonte().secao())
                .isEqualTo("Correção do usuário");
    }

    @Test
    void ignoraAvisosEIncertezasForjadosEPreservaOsDoServidor() {
        ExtracaoEstruturadaDoEdital base = extracao(
                valor("Edital 1", null), valor("Analista", null));
        ExtracaoEstruturadaDoEdital anterior =
                new ExtracaoEstruturadaDoEdital(
                        base.versaoDoContrato(), base.fonte(),
                        base.concurso(), base.edital(), base.cargos(),
                        base.provas(), base.materias(),
                        List.of("Aviso calculado no servidor"),
                        List.of("Incerteza do parser"));
        ExtracaoEstruturadaDoEdital recebida =
                new ExtracaoEstruturadaDoEdital(
                        base.versaoDoContrato(), base.fonte(),
                        base.concurso(), base.edital(), base.cargos(),
                        base.provas(), base.materias(),
                        List.of("Tudo seguro segundo o navegador"),
                        List.of());

        ExtracaoEstruturadaDoEdital sanitizada =
                new SanitizadorDaCorrecaoDoEdital().sanitizar(
                        anterior, recebida);

        assertThat(sanitizada.avisos())
                .containsExactly("Aviso calculado no servidor");
        assertThat(sanitizada.incertezas())
                .containsExactly("Incerteza do parser");
    }

    @Test
    void confirmacaoExplicitaDoMesmoValorViraCorrecaoHumana() {
        ExtracaoEstruturadaDoEdital base = extracao(
                valor("Edital 1", null),
                valorComConfianca("Analista",
                        new ProvenienciaDoDado(null,
                                "Interpretação assistida não verificada",
                                null),
                        new BigDecimal("0.5000"), true));
        ConfirmacaoDeCampoDaExtracao confirmacao =
                new ConfirmacaoDeCampoDaExtracao(
                        "cargo", "cargo-1", "nome");

        var resultado = new SanitizadorDaCorrecaoDoEdital()
                .sanitizarComResultado(base, base, Set.of(confirmacao));

        assertThat(resultado.camposAlterados()).containsExactly(confirmacao);
        assertThat(resultado.extracao().cargos().getFirst().nome())
                .satisfies(nome -> {
                    assertThat(nome.valor()).isEqualTo("Analista");
                    assertThat(nome.inferido()).isFalse();
                    assertThat(nome.fonte().secao())
                            .isEqualTo("Correção do usuário");
                });
    }

    @Test
    void removerRecursoMarcaSeusCamposComoAlterados() {
        ExtracaoEstruturadaDoEdital anterior = extracao(
                valor("Edital 1", null), valor("Analista", null));
        ExtracaoEstruturadaDoEdital semCargos =
                new ExtracaoEstruturadaDoEdital(
                        anterior.versaoDoContrato(), anterior.fonte(),
                        anterior.concurso(), anterior.edital(), List.of(),
                        anterior.provas(), anterior.materias(),
                        anterior.avisos(), anterior.incertezas());

        var resultado = new SanitizadorDaCorrecaoDoEdital()
                .sanitizarComResultado(anterior, semCargos, Set.of());

        assertThat(resultado.camposAlterados()).contains(
                new ConfirmacaoDeCampoDaExtracao(
                        "cargo", "cargo-1", "nome"),
                new ConfirmacaoDeCampoDaExtracao(
                        "cargo", "cargo-1", "nivelDeEscolaridade"));
    }

    private ExtracaoEstruturadaDoEdital extracao(
            ValorExtraido<String> titulo,
            ValorExtraido<String> nomeDoCargo) {
        ValorExtraido<String> ausente = valorComConfianca(
                null, null, BigDecimal.ZERO, false);
        return new ExtracaoEstruturadaDoEdital(
                "1", new FonteDoEdital("edital.pdf", "a".repeat(64), 10),
                new ConcursoExtraido(ausente, ausente, ausente, ausente,
                        valorComConfianca(null, null, BigDecimal.ZERO, false)),
                new EditalExtraido(titulo, ausente,
                        valorComConfianca(null, null, BigDecimal.ZERO, false),
                        ausente,
                        valorComConfianca(null, null, BigDecimal.ZERO, false)),
                List.of(new CargoExtraido("cargo-1", nomeDoCargo, ausente,
                        ausente, valorComConfianca(
                                NivelDeEscolaridade.SUPERIOR,
                                new ProvenienciaDoDado(
                                        1, "CARGO", "nível superior"),
                                new BigDecimal("0.9900"), false), 1)),
                List.of(), List.of(), List.of(), List.of());
    }

    private <T> ValorExtraido<T> valor(T valor,
            ProvenienciaDoDado fonte) {
        return valorComConfianca(valor, fonte,
                new BigDecimal("0.9900"), false);
    }

    private <T> ValorExtraido<T> valorComConfianca(T valor,
            ProvenienciaDoDado fonte, BigDecimal confianca,
            boolean inferido) {
        return new ValorExtraido<>(valor, confianca, fonte, inferido);
    }
}
