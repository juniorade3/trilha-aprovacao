package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ConcursoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.FonteDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.SeveridadeDoProblemaDaImportacao;
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
        assertThat(normalizador.criarChave("prefixo-".repeat(30), 1,
                "nome muito longo ".repeat(30))).hasSizeLessThanOrEqualTo(160);
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
    void extraiEstruturaCebraspeSemBloquearRotuloAnteriorAoCargo()
            throws Exception {
        var extracao = new ParserDeterministicoDoEdital().extrair(
                fixture("edital-cebraspe-tcu.txt"),
                new FonteDoEdital("edital-cebraspe-tcu.txt", HASH, 3));

        assertThat(extracao.concurso().nome().valor())
                .isEqualTo("CONCURSO PÚBLICO PARA O PROVIMENTO DE VAGAS "
                        + "PARA AUDITORIA");
        assertThat(extracao.edital().numero().valor()).isEqualTo("1");
        assertThat(extracao.edital().ano().valor()).isEqualTo(2025);
        assertThat(extracao.cargos()).singleElement().satisfies(cargo -> {
            assertThat(cargo.nome().valor())
                    .isEqualTo("AUDITOR FEDERAL DE CONTROLE EXTERNO");
            assertThat(cargo.area().valor()).isEqualTo("CONTROLE EXTERNO");
            assertThat(cargo.especialidade().valor()).isEqualTo(
                    "CONTROLE EXTERNO – ORIENTAÇÃO: "
                            + "AUDITORIA DE TECNOLOGIA DA INFORMAÇÃO");
            assertThat(cargo.nivelDeEscolaridade().valor().name())
                    .isEqualTo("SUPERIOR");
        });
        assertThat(extracao.provas()).singleElement().satisfies(prova -> {
            assertThat(prova.nome().valor()).isEqualTo("Provas objetivas");
            assertThat(prova.tipo().valor().name()).isEqualTo("OBJETIVA");
            assertThat(prova.carater().valor().name())
                    .isEqualTo("ELIMINATORIO_E_CLASSIFICATORIO");
            assertThat(prova.grupos()).extracting(
                                grupo -> grupo.nome().valor())
                        .containsExactly("CONHECIMENTOS BÁSICOS",
                                "CONHECIMENTOS ESPECÍFICOS");
        });
        assertThat(extracao.materias()).extracting(
                        materia -> materia.nome().valor())
                .containsExactly("LÍNGUA PORTUGUESA",
                        "INFRAESTRUTURA DE TI");

        var linguaPortuguesa = extracao.materias().getFirst();
        assertThat(linguaPortuguesa.itensDoEdital()).extracting(
                        item -> item.numeroOficial().valor())
                .containsExactly("1", "1.1", "2");
        assertThat(linguaPortuguesa.itensDoEdital().get(1).chaveDoPai())
                .isEqualTo(linguaPortuguesa.itensDoEdital().getFirst()
                        .chave());
        assertThat(linguaPortuguesa.itensDoEdital()).extracting(
                        item -> item.descricaoLiteral().valor())
                .containsExactly("Compreensão e interpretação de textos.",
                        "Estratégias de leitura.", "Coesão textual.");
        assertThat(linguaPortuguesa.topicos()).hasSize(3)
                .allSatisfy(topico -> {
                    assertThat(topico.nome().inferido()).isTrue();
                    assertThat(topico.nome().valor()).hasSizeLessThanOrEqualTo(
                            160);
                });
        assertThat(linguaPortuguesa.itensDoEdital()).allSatisfy(item ->
                assertThat(item.chaveDoTopicoSugerido()).isNotNull());

        var infraestrutura = extracao.materias().get(1);
        assertThat(infraestrutura.itensDoEdital()).extracting(
                        item -> item.numeroOficial().valor())
                .containsExactly("1", "1.1", "2");
        assertThat(infraestrutura.itensDoEdital().getLast()
                .descricaoLiteral().valor())
                .isEqualTo("Redes e comunicação de dados.");
        assertThat(infraestrutura.itensDoEdital().get(1)
                .descricaoLiteral().fonte().pagina()).isEqualTo(3);
        assertThat(infraestrutura.itensDoEdital().get(1).chaveDoPai())
                .isEqualTo(infraestrutura.itensDoEdital().getFirst()
                        .chave());

        var problemas = new ValidadorDaExtracaoDoEdital().validar(extracao);
        assertThat(problemas).noneMatch(problema -> problema.severidade()
                == SeveridadeDoProblemaDaImportacao.BLOQUEANTE);
        assertThat(problemas).extracting("codigo")
                .doesNotContain("PROVA_SEM_TIPO", "PROVA_SEM_CARATER");
    }

    @Test
    void mantemRotulosForaDeContextoComoIncerteza() throws Exception {
        var extracao = new ParserDeterministicoDoEdital().extrair(
                fixture("arquivo-invalido.txt"),
                new FonteDoEdital("arquivo-invalido.txt", HASH, 1));

        assertThat(extracao.provas()).isEmpty();
        assertThat(extracao.materias()).isEmpty();
        assertThat(extracao.incertezas())
                .anyMatch(item -> item.contains("sem prova anterior"))
                .anyMatch(item -> item.contains(
                        "sem cargo, prova e grupo anterior"));
    }

    @Test
    void toleraSeparadoresDentroDosObjetosDeAvaliacao() {
        var extracao = new ParserDeterministicoDoEdital().extrair("""
                CONCURSO: Controle Público
                CARGO: Auditor
                PROVA: Prova objetiva
                TIPO: OBJETIVA
                CARÁTER: CLASSIFICATORIO
                18 DOS OBJETOS DE AVALIAÇÃO
                --------------------
                18.1 CONHECIMENTOS BÁSICOS
                LÍNGUA PORTUGUESA: 1 Interpretação de textos.
                """, new FonteDoEdital("edital.txt", HASH, 1));

        assertThat(extracao.materias()).singleElement().satisfies(materia ->
                assertThat(materia.itensDoEdital()).singleElement()
                        .satisfies(item -> assertThat(
                                item.descricaoLiteral().valor())
                                .isEqualTo("Interpretação de textos.")));
    }

    @Test
    void preservaOrientacaoIsoladaECombinaFontesDistintas() {
        var apenasOrientacao = new ParserDeterministicoDoEdital().extrair("""
                CONCURSO: Controle Público
                ORIENTAÇÃO: Auditoria de TI
                CARGO: Auditor
                """, new FonteDoEdital("orientacao.txt", HASH, 1));

        assertThat(apenasOrientacao.cargos().getFirst().especialidade())
                .satisfies(valor -> {
                    assertThat(valor.valor())
                            .isEqualTo("ORIENTAÇÃO: Auditoria de TI");
                    assertThat(valor.fonte().secao()).isEqualTo("ORIENTAÇÃO");
                    assertThat(valor.inferido()).isFalse();
                });

        var camposSeparados = new ParserDeterministicoDoEdital().extrair("""
                CONCURSO: Controle Público
                ESPECIALIDADE: Controle Externo
                \f
                ORIENTAÇÃO: Auditoria de TI
                CARGO: Auditor
                """, new FonteDoEdital("campos-separados.txt", HASH, 2));

        assertThat(camposSeparados.cargos().getFirst().especialidade())
                .satisfies(valor -> {
                    assertThat(valor.valor()).isEqualTo(
                            "Controle Externo – ORIENTAÇÃO: Auditoria de TI");
                    assertThat(valor.inferido()).isTrue();
                    assertThat(valor.fonte().pagina()).isNull();
                    assertThat(valor.fonte().trecho())
                            .contains("ESPECIALIDADE: Controle Externo",
                                    "ORIENTAÇÃO: Auditoria de TI");
                });
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
