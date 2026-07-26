package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ConcursoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.FonteDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ValorExtraido;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ParserEValidadorDoEditalTest {
    private static final String HASH = "0".repeat(64);

    @Test
    void preservaLiteralHierarquiaProvenienciaEMultiplosCargos() {
        String texto = """
                CONCURSO: Controle Público
                ÓRGÃO: Tribunal de Contas
                BANCA: Cebraspe
                EDITAL: Edital de abertura
                NÚMERO: 1
                ANO: 2026
                PUBLICAÇÃO: 20/07/2026
                CARGO: Auditor
                ESCOLARIDADE: SUPERIOR
                PROVA: Prova objetiva
                TIPO: OBJETIVA
                CARÁTER: ELIMINATORIO_E_CLASSIFICATORIO
                GRUPO: Conhecimentos específicos
                MATÉRIA: Direito Administrativo
                TÓPICO: 1 - Atos administrativos
                TÓPICO: 1.1 - Elementos do ato
                ITEM: 1 - Atos administrativos
                ITEM: 1.1 - Competência, finalidade, forma, motivo e objeto.
                CARGO: Técnico
                ESCOLARIDADE: MEDIO
                PROVA: Prova objetiva
                TIPO: OBJETIVA
                CARÁTER: CLASSIFICATORIO
                GRUPO: Conhecimentos básicos
                MATÉRIA: Língua Portuguesa
                TÓPICO: 1 - Interpretação de textos
                ITEM: 1 - Compreensão e interpretação de textos.
                """;
        var extracao = new ParserDeterministicoDoEdital().extrair(texto,
                new FonteDoEdital("edital.txt", HASH, 1));

        assertThat(extracao.cargos()).hasSize(2);
        assertThat(extracao.provas()).hasSize(2)
                .allSatisfy(prova -> assertThat(prova.ordem()).isPositive());
        assertThat(extracao.materias()).hasSize(2);
        var primeira = extracao.materias().getFirst();
        assertThat(primeira.topicos().get(1).chaveDoPai())
                .isEqualTo(primeira.topicos().getFirst().chave());
        assertThat(primeira.itensDoEdital().get(1).descricaoLiteral().valor())
                .isEqualTo("Competência, finalidade, forma, motivo e objeto.");
        assertThat(primeira.itensDoEdital().get(1).descricaoLiteral()
                .fonte().pagina()).isEqualTo(1);

        var problemas = new ValidadorDaExtracaoDoEdital().validar(extracao);
        assertThat(problemas).extracting("codigo")
                .contains("SELECAO_DE_CARGO_OBRIGATORIA")
                .doesNotContain("ORDEM_INVALIDA",
                        "ASSOCIACAO_DA_MATERIA_INVALIDA",
                        "HIERARQUIA_DE_TOPICOS_INVALIDA");
    }

    @Test
    void normalizaSemDestruirTextoLiteral() {
        var normalizador = new NormalizadorDoTextoDoEdital();
        assertThat(normalizador.normalizarNome(
                "  Língua   Portuguesa — Gramática! "))
                .isEqualTo("lingua portuguesa gramatica");
    }

    @Test
    void ignoraWrappersOpcionaisNulosEValidaValoresPresentes() {
        var original = new ParserDeterministicoDoEdital().extrair("""
                CONCURSO: Controle Público
                EDITAL: Edital de abertura
                CARGO: Auditor
                ESCOLARIDADE: SUPERIOR
                PROVA: Prova objetiva
                TIPO: OBJETIVA
                CARÁTER: CLASSIFICATORIO
                GRUPO: Conhecimentos específicos
                MATÉRIA: Direito Administrativo
                TÓPICO: Atos administrativos
                """, new FonteDoEdital("edital.txt", HASH, 1));
        var concursoComOpcionaisNulos = new ConcursoExtraido(
                original.concurso().nome(), null,
                new ValorExtraido<>("Tribunal de Contas",
                        new BigDecimal("0.9000"), null, false),
                null, null);
        var extracao = new ExtracaoEstruturadaDoEdital(
                original.versaoDoContrato(), original.fonte(),
                concursoComOpcionaisNulos, original.edital(),
                original.cargos(), original.provas(), original.materias(),
                original.avisos(), original.incertezas());

        var problemas = new ValidadorDaExtracaoDoEdital().validar(extracao);

        assertThat(problemas).extracting("codigo")
                .contains("PROVENIENCIA_AUSENTE");
    }

    @Test
    void preservaItemLiteralDaFixtureComTabela() throws Exception {
        var extracao = new ParserDeterministicoDoEdital().extrair(
                fixture("edital-com-tabela.txt"),
                new FonteDoEdital("edital-com-tabela.txt", HASH, 1));

        assertThat(extracao.materias().getFirst().itensDoEdital())
                .extracting(item -> item.descricaoLiteral().valor())
                .containsExactly(
                        "Proposições | 2 questões | 2 pontos",
                        "Equivalências lógicas | 3 questões | 3 pontos");
    }

    @Test
    void rejeitaFixtureComOrdemEstruturalInvalida() throws Exception {
        assertThatThrownBy(() -> new ParserDeterministicoDoEdital().extrair(
                fixture("arquivo-invalido.txt"),
                new FonteDoEdital("arquivo-invalido.txt", HASH, 1)))
                .isInstanceOf(FalhaNaExtracaoDoEdital.class)
                .extracting("codigo").isEqualTo("PROVA_AUSENTE");
    }

    private static String fixture(String nome) throws Exception {
        try (var entrada = ParserEValidadorDoEditalTest.class
                .getResourceAsStream("/fixtures/editais/" + nome)) {
            if (entrada == null) throw new IllegalStateException(
                    "Fixture ausente: " + nome);
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
