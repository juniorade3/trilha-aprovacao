package br.com.trilhaaprovacao.planejamento.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.trilhaaprovacao.priorizacao.dominio.FaixaDePriorizacao;
import br.com.trilhaaprovacao.priorizacao.dominio.GrupoDePriorizacao;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeradorDeterministicoComPriorizacaoTest {
    private static final UUID PLANO = uuid(1);
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 7, 20);
    private final GeradorDeterministicoDePlano gerador = new GeradorDeterministicoDePlano();

    @Test
    void deveFalharQuandoNaoHaTopicoElegivelAposAplicarNaoIncluir() {
        CandidatoDeMateriaParaGeracao materia = materia(10,
                PrioridadeDaMateriaNoPlano.NAO_INCLUIR);

        assertThatThrownBy(() -> gerar(SEGUNDA, List.of(materia),
                List.of(topico(materia, 101, GrupoDePriorizacao.LACUNA,
                        FaixaDePriorizacao.SEM_ESTUDO, false, 1)),
                List.of(), dias(50)))
                .isInstanceOf(SemTopicosElegiveisParaGeracao.class)
                .hasMessage("Nao ha topicos elegiveis para a geracao automatica.");
    }

    @Test
    void prioridadeManualDeveOrdenarMateriasSemApagarDiagnosticoDoTopico() {
        CandidatoDeMateriaParaGeracao fracaComPrioridadeBaixa = materia(
                11, PrioridadeDaMateriaNoPlano.BAIXA);
        CandidatoDeMateriaParaGeracao consolidadaComPrioridadeAlta = materia(
                12, PrioridadeDaMateriaNoPlano.ALTA);
        CandidatoDeTopicoParaGeracao fraco = topico(
                fracaComPrioridadeBaixa, 111, GrupoDePriorizacao.FRAQUEZA,
                FaixaDePriorizacao.PRECISA_REFORCO, true, 1);
        CandidatoDeTopicoParaGeracao consolidado = topico(
                consolidadaComPrioridadeAlta, 121, GrupoDePriorizacao.CONSOLIDADO,
                FaixaDePriorizacao.CONSOLIDADO, true, 1);

        var previa = gerar(SEGUNDA,
                List.of(fracaComPrioridadeBaixa, consolidadaComPrioridadeAlta),
                List.of(fraco, consolidado), List.of(), dias(50));

        assertThat(principais(previa.dias().getFirst()))
                .extracting(BlocoSugerido::identificadorDoTopico)
                .containsExactly(consolidado.identificadorDoTopico(),
                        fraco.identificadorDoTopico());
        assertThat(principais(previa.dias().getFirst()).getFirst()
                .justificativas())
                .extracting(JustificativaDaGeracao::codigo)
                .contains("PRIORIDADE_ALTA", "GRUPO_CONSOLIDADO",
                        "FAIXA_CONSOLIDADO");
        assertThat(previa.dias())
                .flatExtracting(DiaDaPreviaDaGeracao::blocosSugeridos)
                .filteredOn(bloco -> bloco.identificadorDoTopico()
                        .equals(fraco.identificadorDoTopico()))
                .isNotEmpty()
                .allSatisfy(bloco -> assertThat(bloco.justificativas())
                        .extracting(JustificativaDaGeracao::codigo)
                        .contains("PRIORIDADE_BAIXA", "GRUPO_FRAQUEZA",
                                "FAIXA_PRECISA_REFORCO"));
    }

    @Test
    void deveSerDeterministicoSobPermutacoesDeTodasAsEntradas() {
        List<CandidatoDeMateriaParaGeracao> materias = materias(4);
        List<CandidatoDeTopicoParaGeracao> topicos = new ArrayList<>();
        List<CandidatoDeRevisaoParaGeracao> revisoes = new ArrayList<>();
        for (int indice = 0; indice < materias.size(); indice++) {
            CandidatoDeMateriaParaGeracao materia = materias.get(indice);
            topicos.add(topico(materia, 100 + indice * 10,
                    GrupoDePriorizacao.LACUNA, FaixaDePriorizacao.SEM_ESTUDO,
                    false, 1));
            topicos.add(topico(materia, 101 + indice * 10,
                    GrupoDePriorizacao.FRAQUEZA,
                    FaixaDePriorizacao.PRECISA_REFORCO, true, 1));
            revisoes.add(revisao(materia, 101 + indice * 10,
                    SEGUNDA, indice % 5, indice + 1, false));
        }
        List<EntradaDoDiaParaGeracao> dias = dias(210);
        var esperada = gerar(SEGUNDA, materias, topicos, revisoes, dias);
        List<CandidatoDeMateriaParaGeracao> materiasInvertidas = invertida(materias);
        List<CandidatoDeTopicoParaGeracao> topicosInvertidos = invertida(topicos);
        List<CandidatoDeRevisaoParaGeracao> revisoesInvertidas = invertida(revisoes);
        List<EntradaDoDiaParaGeracao> diasInvertidos = invertida(dias);

        assertThat(gerar(SEGUNDA, materiasInvertidas, topicosInvertidos,
                revisoesInvertidas, diasInvertidos)).isEqualTo(esperada);
    }

    @Test
    void deveAlternarLacunaEFraquezaPorDiaERepetirOMelhorTopicoDoGrupo() {
        CandidatoDeMateriaParaGeracao materia = materia(20,
                PrioridadeDaMateriaNoPlano.NORMAL);
        CandidatoDeTopicoParaGeracao lacuna = topico(materia, 201,
                GrupoDePriorizacao.LACUNA, FaixaDePriorizacao.SEM_ESTUDO,
                false, 1);
        CandidatoDeTopicoParaGeracao fraqueza = topico(materia, 202,
                GrupoDePriorizacao.FRAQUEZA, FaixaDePriorizacao.PRECISA_REFORCO,
                true, 1);

        var previa = gerar(SEGUNDA, List.of(materia), List.of(fraqueza, lacuna),
                List.of(), dias(50));

        assertThat(principais(previa.dias().get(0))).singleElement()
                .extracting(BlocoSugerido::identificadorDoTopico)
                .isEqualTo(lacuna.identificadorDoTopico());
        assertThat(principais(previa.dias().get(1))).singleElement()
                .extracting(BlocoSugerido::identificadorDoTopico)
                .isEqualTo(fraqueza.identificadorDoTopico());
        assertThat(principais(previa.dias().get(2))).singleElement()
                .extracting(BlocoSugerido::identificadorDoTopico)
                .isEqualTo(lacuna.identificadorDoTopico());
    }

    @Test
    void blocoPreservadoComTopicoOficialDeveParticiparDaAlternancia() {
        CandidatoDeMateriaParaGeracao materia = materia(30,
                PrioridadeDaMateriaNoPlano.NORMAL);
        CandidatoDeTopicoParaGeracao lacuna = topico(materia, 301,
                GrupoDePriorizacao.LACUNA, FaixaDePriorizacao.SEM_ESTUDO,
                false, 1);
        CandidatoDeTopicoParaGeracao fraqueza = topico(materia, 302,
                GrupoDePriorizacao.FRAQUEZA, FaixaDePriorizacao.DESEMPENHO_PARCIAL,
                true, 1);
        List<EntradaDoDiaParaGeracao> dias = dias(50);
        dias.set(0, new EntradaDoDiaParaGeracao(SEGUNDA, 50,
                List.of(preservado(900, materia, lacuna.identificadorDoTopico(),
                        TipoDeAtividade.TEORIA, 50, 1))));

        var previa = gerar(SEGUNDA, List.of(materia), List.of(lacuna, fraqueza),
                List.of(), dias);

        assertThat(principais(previa.dias().get(0))).isEmpty();
        assertThat(principais(previa.dias().get(1))).singleElement()
                .extracting(BlocoSugerido::identificadorDoTopico)
                .isEqualTo(fraqueza.identificadorDoTopico());
    }

    @Test
    void deveSugerirTeoriaParaNuncaEstudadoEQuestoesParaOsDemais() {
        CandidatoDeMateriaParaGeracao materia = materia(40,
                PrioridadeDaMateriaNoPlano.NORMAL);
        CandidatoDeTopicoParaGeracao lacuna = topico(materia, 401,
                GrupoDePriorizacao.LACUNA, FaixaDePriorizacao.SEM_ESTUDO,
                false, 1);
        CandidatoDeTopicoParaGeracao fraqueza = topico(materia, 402,
                GrupoDePriorizacao.FRAQUEZA, FaixaDePriorizacao.PRECISA_REFORCO,
                true, 1);

        var previa = gerar(SEGUNDA, List.of(materia), List.of(lacuna, fraqueza),
                List.of(), dias(50));

        assertThat(principais(previa.dias().get(0))).singleElement()
                .extracting(BlocoSugerido::tipoDeAtividade)
                .isEqualTo(TipoDeAtividade.TEORIA);
        assertThat(principais(previa.dias().get(1))).singleElement()
                .extracting(BlocoSugerido::tipoDeAtividade)
                .isEqualTo(TipoDeAtividade.QUESTOES);
    }

    @Test
    void deveReservarAteTresRevisoesFixasAntesDosPrincipaisSemAntecipar() {
        List<CandidatoDeMateriaParaGeracao> materias = materias(5);
        List<CandidatoDeTopicoParaGeracao> topicos = new ArrayList<>();
        List<CandidatoDeRevisaoParaGeracao> revisoes = new ArrayList<>();
        for (int indice = 0; indice < materias.size(); indice++) {
            CandidatoDeMateriaParaGeracao materia = materias.get(indice);
            int id = 500 + indice;
            topicos.add(topico(materia, id, GrupoDePriorizacao.LACUNA,
                    FaixaDePriorizacao.SEM_EVIDENCIA, true, 1));
            revisoes.add(revisao(materia, id,
                    indice == 4 ? SEGUNDA.plusDays(2) : SEGUNDA,
                    indice, indice + 1, false));
        }

        var previa = gerar(SEGUNDA, materias, topicos, revisoes, dias(210));
        List<BlocoSugerido> segunda = previa.dias().getFirst().blocosSugeridos();

        assertThat(segunda.subList(0, 3))
                .allSatisfy(item -> {
                    assertThat(item.tipoDeAtividade()).isEqualTo(TipoDeAtividade.REVISAO);
                    assertThat(item.duracaoEmMinutos()).isEqualTo(20);
                });
        assertThat(segunda).filteredOn(item ->
                item.tipoDeAtividade() == TipoDeAtividade.REVISAO).hasSize(3);
        assertThat(previa.dias().get(1).blocosSugeridos())
                .noneMatch(item -> item.identificadorDoTopico().equals(
                        revisoes.get(4).identificadorDoTopico()));
        assertThat(previa.dias().get(2).blocosSugeridos())
                .anyMatch(item -> item.tipoDeAtividade() == TipoDeAtividade.REVISAO
                        && item.identificadorDoTopico().equals(
                                revisoes.get(4).identificadorDoTopico()));
    }

    @Test
    void revisaoEspecificaNaoDeveContarNoLimiteDeTresMateriasPrincipais() {
        List<CandidatoDeMateriaParaGeracao> materias = materias(4);
        List<CandidatoDeTopicoParaGeracao> topicos = new ArrayList<>();
        for (int indice = 0; indice < 4; indice++) {
            topicos.add(topico(materias.get(indice), 600 + indice,
                    GrupoDePriorizacao.LACUNA, FaixaDePriorizacao.SEM_EVIDENCIA,
                    true, 1));
        }
        List<EntradaDoDiaParaGeracao> dias = dias(95);
        dias.set(0, new EntradaDoDiaParaGeracao(SEGUNDA, 95, List.of(
                preservado(910, materias.get(0), topicos.get(0).identificadorDoTopico(),
                        TipoDeAtividade.QUESTOES, 25, 1),
                preservado(911, materias.get(1), topicos.get(1).identificadorDoTopico(),
                        TipoDeAtividade.QUESTOES, 25, 2),
                preservado(912, materias.get(2), topicos.get(2).identificadorDoTopico(),
                        TipoDeAtividade.QUESTOES, 25, 3))));
        CandidatoDeRevisaoParaGeracao revisao = revisao(materias.get(3), 603,
                SEGUNDA, 0, 4, false);

        var dia = gerar(SEGUNDA, materias, topicos, List.of(revisao), dias)
                .dias().getFirst();

        assertThat(dia.blocosSugeridos()).singleElement().satisfies(item -> {
            assertThat(item.tipoDeAtividade()).isEqualTo(TipoDeAtividade.REVISAO);
            assertThat(item.identificadorDaMateria())
                    .isEqualTo(materias.get(3).identificadorDaMateria());
        });
        assertThat(dia.capacidade().minutosPreservados()
                + dia.capacidade().minutosSugeridos()).isEqualTo(95);
    }

    @Test
    void revisaoGenericaPreservadaNaoDeveConsumirVagaDeRevisaoEspecifica() {
        CandidatoDeMateriaParaGeracao materia = materia(65,
                PrioridadeDaMateriaNoPlano.NORMAL);
        CandidatoDeTopicoParaGeracao topico = topico(materia, 650,
                GrupoDePriorizacao.LACUNA, FaixaDePriorizacao.SEM_EVIDENCIA,
                true, 1);
        List<EntradaDoDiaParaGeracao> dias = dias(0);
        dias.set(0, new EntradaDoDiaParaGeracao(SEGUNDA, 80,
                List.of(preservado(915, materia, null,
                        TipoDeAtividade.REVISAO, 20, 1))));
        List<CandidatoDeRevisaoParaGeracao> revisoes = List.of(
                revisao(materia, 651, SEGUNDA, 0, 1, false),
                revisao(materia, 652, SEGUNDA, 0, 2, false),
                revisao(materia, 653, SEGUNDA, 0, 3, false));

        var dia = gerar(SEGUNDA, List.of(materia), List.of(topico), revisoes, dias)
                .dias().getFirst();

        assertThat(dia.blocosSugeridos())
                .filteredOn(item -> item.tipoDeAtividade() == TipoDeAtividade.REVISAO)
                .hasSize(3);
    }

    @Test
    void deveEvitarRevisaoEPrincipalDoMesmoTopicoTentandoOProximoDoGrupo() {
        CandidatoDeMateriaParaGeracao materia = materia(70,
                PrioridadeDaMateriaNoPlano.NORMAL);
        CandidatoDeTopicoParaGeracao primeiro = topico(materia, 701,
                GrupoDePriorizacao.LACUNA, FaixaDePriorizacao.SEM_EVIDENCIA,
                true, 1);
        CandidatoDeTopicoParaGeracao segundo = topico(materia, 702,
                GrupoDePriorizacao.LACUNA, FaixaDePriorizacao.DADOS_INSUFICIENTES,
                true, 2);
        CandidatoDeRevisaoParaGeracao revisao = revisao(materia, 701,
                SEGUNDA, 0, 1, false);

        var dia = gerar(SEGUNDA, List.of(materia), List.of(primeiro, segundo),
                List.of(revisao), dias(70)).dias().getFirst();

        assertThat(dia.blocosSugeridos()).extracting(BlocoSugerido::identificadorDoTopico)
                .containsExactly(primeiro.identificadorDoTopico(),
                        segundo.identificadorDoTopico());
    }

    @Test
    void naoDeveDuplicarRevisaoComBlocoAbertoOuPreservado() {
        CandidatoDeMateriaParaGeracao materia = materia(80,
                PrioridadeDaMateriaNoPlano.NORMAL);
        CandidatoDeTopicoParaGeracao topico = topico(materia, 801,
                GrupoDePriorizacao.LACUNA, FaixaDePriorizacao.SEM_EVIDENCIA,
                true, 1);
        List<EntradaDoDiaParaGeracao> dias = dias(50);
        dias.set(0, new EntradaDoDiaParaGeracao(SEGUNDA, 50,
                List.of(preservado(920, materia, topico.identificadorDoTopico(),
                        TipoDeAtividade.REVISAO, 20, 1))));

        var comPreservado = gerar(SEGUNDA, List.of(materia), List.of(topico),
                List.of(revisao(materia, 801, SEGUNDA, 0, 1, false)), dias);
        var comAberto = gerar(SEGUNDA, List.of(materia), List.of(topico),
                List.of(revisao(materia, 801, SEGUNDA, 0, 1, true)),
                dias(50));

        assertThat(comPreservado.dias()).flatExtracting(DiaDaPreviaDaGeracao::blocosSugeridos)
                .noneMatch(item -> item.tipoDeAtividade() == TipoDeAtividade.REVISAO);
        assertThat(comAberto.dias()).flatExtracting(DiaDaPreviaDaGeracao::blocosSugeridos)
                .noneMatch(item -> item.tipoDeAtividade() == TipoDeAtividade.REVISAO);
    }

    @Test
    void naoDeveGerarAntesDaDataDeReferenciaNemExcederCapacidade() {
        List<CandidatoDeMateriaParaGeracao> materias = materias(4);
        List<CandidatoDeTopicoParaGeracao> topicos = materias.stream()
                .map(materia -> topico(materia,
                        900 + materia.ordemEstavel(), GrupoDePriorizacao.LACUNA,
                        FaixaDePriorizacao.SEM_ESTUDO, false, 1))
                .toList();

        var previa = gerar(SEGUNDA.plusDays(2), materias, topicos,
                List.of(), dias(170));

        assertThat(previa.dias().subList(0, 2))
                .allSatisfy(dia -> assertThat(dia.blocosSugeridos()).isEmpty());
        assertThat(previa.dias().subList(2, 7)).allSatisfy(dia -> {
            assertThat(principais(dia)).hasSizeLessThanOrEqualTo(3);
            assertThat(dia.capacidade().minutosPreservados()
                    + dia.capacidade().minutosSugeridos())
                    .isLessThanOrEqualTo(dia.capacidade().minutosDisponiveis());
        });
    }

    private PreviaDaGeracaoDaSemana gerar(LocalDate referencia,
            List<CandidatoDeMateriaParaGeracao> materias,
            List<CandidatoDeTopicoParaGeracao> topicos,
            List<CandidatoDeRevisaoParaGeracao> revisoes,
            List<EntradaDoDiaParaGeracao> dias) {
        return gerador.gerar(PLANO, referencia, materias, topicos, revisoes, dias,
                new ConfiguracaoDaGeracaoDeterministica(50));
    }

    private List<BlocoSugerido> principais(DiaDaPreviaDaGeracao dia) {
        return dia.blocosSugeridos().stream()
                .filter(item -> item.tipoDeAtividade() != TipoDeAtividade.REVISAO)
                .toList();
    }

    private List<CandidatoDeMateriaParaGeracao> materias(int quantidade) {
        List<CandidatoDeMateriaParaGeracao> resultado = new ArrayList<>();
        for (int indice = 0; indice < quantidade; indice++) {
            resultado.add(materia(100 + indice, PrioridadeDaMateriaNoPlano.NORMAL));
        }
        return resultado;
    }

    private CandidatoDeMateriaParaGeracao materia(int id,
            PrioridadeDaMateriaNoPlano prioridade) {
        return new CandidatoDeMateriaParaGeracao(uuid(id), "Materia " + id,
                "materia " + id, id, prioridade);
    }

    private CandidatoDeTopicoParaGeracao topico(
            CandidatoDeMateriaParaGeracao materia, int id,
            GrupoDePriorizacao grupo, FaixaDePriorizacao faixa,
            boolean estudado, int posicao) {
        return new CandidatoDeTopicoParaGeracao(materia.identificadorDaMateria(), uuid(id),
                materia.nome(), "Topico " + id, "topico " + id, id, posicao,
                grupo, faixa, estudado);
    }

    private CandidatoDeRevisaoParaGeracao revisao(
            CandidatoDeMateriaParaGeracao materia, int topico, LocalDate dataDevida,
            int etapa, int ordem, boolean possuiBlocoAberto) {
        return new CandidatoDeRevisaoParaGeracao(materia.identificadorDaMateria(),
                uuid(topico), materia.nome(), "Topico " + topico, dataDevida,
                etapa, etapa == 0 ? null : Math.min(5, etapa), ordem,
                possuiBlocoAberto);
    }

    private BlocoPreservadoNaGeracao preservado(int id,
            CandidatoDeMateriaParaGeracao materia, UUID topico,
            TipoDeAtividade tipo, int minutos, int ordem) {
        return new BlocoPreservadoNaGeracao(uuid(id), materia.identificadorDaMateria(),
                topico, materia.nome(), "Preservado " + id, tipo, SEGUNDA,
                minutos, ordem);
    }

    private List<EntradaDoDiaParaGeracao> dias(int minutos) {
        List<EntradaDoDiaParaGeracao> resultado = new ArrayList<>();
        for (int indice = 0; indice < 7; indice++) {
            resultado.add(new EntradaDoDiaParaGeracao(
                    SEGUNDA.plusDays(indice), minutos, List.of()));
        }
        return resultado;
    }

    private <T> List<T> invertida(List<T> original) {
        List<T> copia = new ArrayList<>(original);
        Collections.reverse(copia);
        return copia;
    }

    private static UUID uuid(int fim) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(fim));
    }
}
