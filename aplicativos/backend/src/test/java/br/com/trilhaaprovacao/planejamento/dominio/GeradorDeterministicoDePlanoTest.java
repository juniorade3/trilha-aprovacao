package br.com.trilhaaprovacao.planejamento.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeradorDeterministicoDePlanoTest {
    private static final UUID PLANO = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 7, 20);
    private final GeradorDeterministicoDePlano gerador = new GeradorDeterministicoDePlano();

    @Test
    void deveSerDeterministicoMesmoComEntradasEmOrdensDiferentes() {
        List<CandidatoDeMateriaParaGeracao> candidatos = candidatos();
        List<EntradaDoDiaParaGeracao> dias = dias(170);
        List<CandidatoDeMateriaParaGeracao> candidatosInvertidos = new ArrayList<>(candidatos);
        List<EntradaDoDiaParaGeracao> diasInvertidos = new ArrayList<>(dias);
        Collections.reverse(candidatosInvertidos);
        Collections.reverse(diasInvertidos);

        var primeira = gerador.gerar(PLANO, candidatos, dias,
                new ConfiguracaoDaGeracaoDeterministica(50, 20));
        var segunda = gerador.gerar(PLANO, candidatosInvertidos, diasInvertidos,
                new ConfiguracaoDaGeracaoDeterministica(50, 20));

        assertThat(segunda).isEqualTo(primeira);
        assertThat(primeira.dias().getFirst().blocosSugeridos()).hasSize(4);
        assertThat(primeira.dias().getFirst().blocosSugeridos().getFirst()
                .tipoDeAtividade()).isEqualTo(TipoDeAtividade.REVISAO);
        assertThat(primeira.dias().getFirst().blocosSugeridos().stream()
                .filter(item -> item.identificadorDaMateria() != null)
                .map(BlocoSugerido::identificadorDaMateria)).doesNotHaveDuplicates();
    }

    @Test
    void deveRespeitarPesosAlternanciaExclusaoELimites() {
        List<CandidatoDeMateriaParaGeracao> candidatos = new ArrayList<>(candidatos());
        candidatos.set(0, comPrioridade(candidatos.get(0), PrioridadeDaMateriaNoPlano.ALTA));
        candidatos.set(2, comPrioridade(candidatos.get(2), PrioridadeDaMateriaNoPlano.NAO_INCLUIR));

        var previa = gerador.gerar(PLANO, candidatos, dias(70),
                new ConfiguracaoDaGeracaoDeterministica(50, 20));

        assertThat(previa.dias().getFirst().blocosSugeridos()).hasSize(3);
        assertThat(previa.dias().getFirst().blocosSugeridos().get(1)
                .identificadorDaMateria()).isEqualTo(candidatos.get(0).identificadorDaMateria());
        assertThat(previa.dias().get(1).blocosSugeridos().get(1)
                .identificadorDaMateria()).isNotEqualTo(candidatos.get(0).identificadorDaMateria());
        assertThat(previa.dias()).allSatisfy(dia ->
                assertThat(dia.capacidade().minutosPreservados()
                        + dia.capacidade().minutosSugeridos())
                        .isLessThanOrEqualTo(dia.capacidade().minutosDisponiveis()));
    }

    @Test
    void devePreservarRevisaoEContarMateriaManualNaMetaECarga() {
        UUID materiaManual = candidatos().getFirst().identificadorDaMateria();
        var revisao = preservado(null, TipoDeAtividade.REVISAO, 20, 1);
        var manual = preservado(materiaManual, TipoDeAtividade.QUESTOES, 50, 2);
        List<EntradaDoDiaParaGeracao> entradas = dias(170);
        entradas.set(0, new EntradaDoDiaParaGeracao(SEGUNDA, 170, List.of(revisao, manual)));

        var previa = gerador.gerar(PLANO, candidatos(), entradas,
                new ConfiguracaoDaGeracaoDeterministica(50, 20));
        var primeiroDia = previa.dias().getFirst();

        assertThat(primeiroDia.blocosSugeridos()).hasSize(2);
        assertThat(primeiroDia.blocosSugeridos())
                .noneMatch(item -> item.tipoDeAtividade() == TipoDeAtividade.REVISAO);
        assertThat(primeiroDia.blocosSugeridos())
                .noneMatch(item -> materiaManual.equals(item.identificadorDaMateria()));
        assertThat(primeiroDia.avisos()).extracting(JustificativaDaGeracao::codigo)
                .contains("REVISAO_JA_EXISTENTE");
    }

    @Test
    void somaDiariaNuncaDeveExcederDisponibilidade() {
        for (int minutos = 0; minutos <= 1440; minutos++) {
            int disponibilidade = minutos;
            var previa = gerador.gerar(PLANO, candidatos(), dias(disponibilidade),
                    new ConfiguracaoDaGeracaoDeterministica(180, 120));
            assertThat(previa.dias()).allSatisfy(dia ->
                    assertThat(dia.capacidade().minutosPreservados()
                            + dia.capacidade().minutosSugeridos())
                            .isLessThanOrEqualTo(disponibilidade));
        }
    }

    private List<CandidatoDeMateriaParaGeracao> candidatos() {
        return List.of(
                candidato(11, "Banco de dados", 1),
                candidato(12, "Engenharia de software", 2),
                candidato(13, "Redes", 3),
                candidato(14, "Seguranca", 4));
    }

    private CandidatoDeMateriaParaGeracao candidato(int finalDoUuid, String nome, int ordem) {
        UUID identificador = UUID.fromString(
                "00000000-0000-0000-0000-%012d".formatted(finalDoUuid));
        return new CandidatoDeMateriaParaGeracao(identificador, nome,
                nome.toLowerCase(), ordem, PrioridadeDaMateriaNoPlano.NORMAL);
    }

    private CandidatoDeMateriaParaGeracao comPrioridade(
            CandidatoDeMateriaParaGeracao candidato, PrioridadeDaMateriaNoPlano prioridade) {
        return new CandidatoDeMateriaParaGeracao(candidato.identificadorDaMateria(),
                candidato.nome(), candidato.nomeNormalizado(), candidato.ordemEstavel(), prioridade);
    }

    private List<EntradaDoDiaParaGeracao> dias(int minutos) {
        List<EntradaDoDiaParaGeracao> resultado = new ArrayList<>();
        for (int indice = 0; indice < 7; indice++) {
            resultado.add(new EntradaDoDiaParaGeracao(
                    SEGUNDA.plusDays(indice), minutos, List.of()));
        }
        return resultado;
    }

    private BlocoPreservadoNaGeracao preservado(UUID materia,
            TipoDeAtividade tipo, int minutos, int ordem) {
        return new BlocoPreservadoNaGeracao(UUID.randomUUID(), materia,
                materia == null ? null : "Materia manual", "Bloco existente",
                tipo, SEGUNDA, minutos, ordem);
    }
}
