package br.com.trilhaaprovacao.planejamento.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.trilhaaprovacao.priorizacao.dominio.FaixaDePriorizacao;
import br.com.trilhaaprovacao.priorizacao.dominio.GrupoDePriorizacao;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regressao das regras de carga, pesos, alternancia e capacidade do gerador original. */
class GeradorDeterministicoDePlanoTest {
    private static final UUID PLANO = uuid(1);
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 7, 20);
    private final GeradorDeterministicoDePlano gerador = new GeradorDeterministicoDePlano();

    @Test
    void deveManterResultadoParaPermutacoesDeMateriasTopicosEDias() {
        List<CandidatoDeMateriaParaGeracao> materias = materias();
        List<CandidatoDeTopicoParaGeracao> topicos = topicos(materias);
        List<EntradaDoDiaParaGeracao> dias = dias(150);
        var esperada = gerar(materias, topicos, dias, 50);

        assertThat(gerar(inverter(materias), inverter(topicos), inverter(dias), 50))
                .isEqualTo(esperada);
        assertThat(esperada.dias().getFirst().blocosSugeridos())
                .hasSize(3)
                .extracting(BlocoSugerido::identificadorDaMateria)
                .doesNotHaveDuplicates();
    }

    @Test
    void deveManterResultadoComPreservadosEmOrdensDeEntradaDiferentes() {
        List<CandidatoDeMateriaParaGeracao> materias = materias();
        var primeiro = preservado(101, materias.get(0), TipoDeAtividade.QUESTOES, 25, 2);
        var segundo = preservado(102, materias.get(1), TipoDeAtividade.TEORIA, 25, 1);
        List<EntradaDoDiaParaGeracao> entradasA = dias(150);
        List<EntradaDoDiaParaGeracao> entradasB = dias(150);
        entradasA.set(0, new EntradaDoDiaParaGeracao(
                SEGUNDA, 150, List.of(primeiro, segundo)));
        entradasB.set(0, new EntradaDoDiaParaGeracao(
                SEGUNDA, 150, List.of(segundo, primeiro)));

        assertThat(gerar(materias, topicos(materias), entradasB, 50))
                .isEqualTo(gerar(materias, topicos(materias), entradasA, 50));
    }

    @Test
    void devePreservarTresMateriasPrincipaisDeCinquentaMinutos() {
        var dia = gerar(materias(), topicos(materias()), dias(150), 50)
                .dias().getFirst();

        assertThat(dia.blocosSugeridos()).hasSize(3)
                .allSatisfy(item -> {
                    assertThat(item.tipoDeAtividade()).isNotEqualTo(TipoDeAtividade.REVISAO);
                    assertThat(item.duracaoEmMinutos()).isEqualTo(50);
                });
        assertThat(dia.capacidade().minutosSugeridos()).isEqualTo(150);
        assertThat(dia.capacidade().minutosLivres()).isZero();
    }

    @Test
    void deveDistribuirSetentaMinutosEmDoisBlocosSemViolarDuracaoMinima() {
        var dia = gerar(materias(), topicos(materias()), dias(70), 50)
                .dias().getFirst();

        assertThat(dia.blocosSugeridos()).hasSize(2)
                .allSatisfy(item -> assertThat(item.duracaoEmMinutos()).isEqualTo(35));
        assertThat(dia.capacidade().minutosSugeridos()).isEqualTo(70);
        assertThat(dia.avisos()).extracting(JustificativaDaGeracao::codigo)
                .contains("DISPONIBILIDADE_INSUFICIENTE");
    }

    @Test
    void deveRespeitarPesosAlternanciaExclusaoELimiteDeMaterias() {
        List<CandidatoDeMateriaParaGeracao> materias = new ArrayList<>(materias());
        materias.set(0, comPrioridade(materias.get(0), PrioridadeDaMateriaNoPlano.ALTA));
        materias.set(2, comPrioridade(
                materias.get(2), PrioridadeDaMateriaNoPlano.NAO_INCLUIR));

        var previa = gerar(materias, topicos(materias), dias(70), 50);

        assertThat(previa.dias().getFirst().blocosSugeridos()).hasSize(2);
        assertThat(previa.dias().getFirst().blocosSugeridos().getFirst()
                .identificadorDaMateria()).isEqualTo(materias.getFirst().identificadorDaMateria());
        assertThat(previa.dias().get(1).blocosSugeridos().getFirst()
                .identificadorDaMateria()).isNotEqualTo(materias.getFirst()
                        .identificadorDaMateria());
        assertThat(previa.dias()).allSatisfy(dia -> {
            assertThat(dia.blocosSugeridos()).hasSizeLessThanOrEqualTo(3);
            assertThat(dia.capacidade().minutosPreservados()
                    + dia.capacidade().minutosSugeridos())
                    .isLessThanOrEqualTo(dia.capacidade().minutosDisponiveis());
        });
    }

    @Test
    void somaDiariaNuncaDeveExcederDisponibilidade() {
        List<CandidatoDeMateriaParaGeracao> materias = materias();
        for (int minutos = 0; minutos <= 1440; minutos++) {
            var previa = gerar(materias, topicos(materias), dias(minutos), 180);
            int disponibilidade = minutos;
            assertThat(previa.dias()).allSatisfy(dia ->
                    assertThat(dia.capacidade().minutosPreservados()
                            + dia.capacidade().minutosSugeridos())
                            .isLessThanOrEqualTo(disponibilidade));
        }
    }

    private PreviaDaGeracaoDaSemana gerar(
            List<CandidatoDeMateriaParaGeracao> materias,
            List<CandidatoDeTopicoParaGeracao> topicos,
            List<EntradaDoDiaParaGeracao> dias, int duracao) {
        return gerador.gerar(PLANO, SEGUNDA, materias, topicos, List.of(), dias,
                new ConfiguracaoDaGeracaoDeterministica(duracao));
    }

    private List<CandidatoDeMateriaParaGeracao> materias() {
        return List.of(
                materia(11, "Banco de dados", 1),
                materia(12, "Engenharia de software", 2),
                materia(13, "Redes", 3),
                materia(14, "Seguranca", 4));
    }

    private CandidatoDeMateriaParaGeracao materia(int id, String nome, int ordem) {
        return new CandidatoDeMateriaParaGeracao(uuid(id), nome,
                nome.toLowerCase(), ordem, PrioridadeDaMateriaNoPlano.NORMAL);
    }

    private CandidatoDeMateriaParaGeracao comPrioridade(
            CandidatoDeMateriaParaGeracao materia,
            PrioridadeDaMateriaNoPlano prioridade) {
        return new CandidatoDeMateriaParaGeracao(materia.identificadorDaMateria(),
                materia.nome(), materia.nomeNormalizado(), materia.ordemEstavel(), prioridade);
    }

    private List<CandidatoDeTopicoParaGeracao> topicos(
            List<CandidatoDeMateriaParaGeracao> materias) {
        List<CandidatoDeTopicoParaGeracao> resultado = new ArrayList<>();
        int indice = 0;
        for (CandidatoDeMateriaParaGeracao materia : materias) {
            resultado.add(new CandidatoDeTopicoParaGeracao(
                    materia.identificadorDaMateria(), uuid(200 + indice * 2),
                    materia.nome(), "Lacuna " + indice, "lacuna " + indice,
                    indice * 2 + 1, 1, GrupoDePriorizacao.LACUNA,
                    FaixaDePriorizacao.SEM_ESTUDO, false));
            resultado.add(new CandidatoDeTopicoParaGeracao(
                    materia.identificadorDaMateria(), uuid(201 + indice * 2),
                    materia.nome(), "Fraqueza " + indice, "fraqueza " + indice,
                    indice * 2 + 2, 1, GrupoDePriorizacao.FRAQUEZA,
                    FaixaDePriorizacao.PRECISA_REFORCO, true));
            indice++;
        }
        return resultado;
    }

    private List<EntradaDoDiaParaGeracao> dias(int minutos) {
        List<EntradaDoDiaParaGeracao> resultado = new ArrayList<>();
        for (int indice = 0; indice < 7; indice++) {
            resultado.add(new EntradaDoDiaParaGeracao(
                    SEGUNDA.plusDays(indice), minutos, List.of()));
        }
        return resultado;
    }

    private BlocoPreservadoNaGeracao preservado(int id,
            CandidatoDeMateriaParaGeracao materia, TipoDeAtividade tipo,
            int minutos, int ordem) {
        return new BlocoPreservadoNaGeracao(uuid(id), materia.identificadorDaMateria(),
                null, materia.nome(), "Bloco existente", tipo, SEGUNDA,
                minutos, ordem);
    }

    private <T> List<T> inverter(List<T> original) {
        List<T> resultado = new ArrayList<>(original);
        Collections.reverse(resultado);
        return resultado;
    }

    private static UUID uuid(int fim) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(fim));
    }
}
