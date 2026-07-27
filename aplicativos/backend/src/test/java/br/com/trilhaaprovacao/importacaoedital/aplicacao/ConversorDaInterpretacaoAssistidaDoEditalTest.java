package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.CargoInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.ConcursoInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.DadoDecimalInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.DadoInteiroInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.DadoTextualInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.EditalInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.EvidenciaInterpretada;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.GrupoInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.ItemInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.MateriaInterpretada;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.ProvaInterpretada;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.TopicoInterpretado;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.FonteDoEdital;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversorDaInterpretacaoAssistidaDoEditalTest {

    @Test
    void geraAssociacoesEConfereEvidenciaContraPaginaLocal() {
        String texto = """
                CONCURSO: Tribunal
                EDITAL: Edital 1
                CARGO: Analista
                ESCOLARIDADE: SUPERIOR
                PROVA: Objetiva
                TIPO: OBJETIVA
                CARÁTER: ELIMINATORIO
                GRUPO: Conhecimentos
                MATÉRIA: Direito
                TÓPICO: 1 - Atos
                \fCARGO: ENGENHEIRO DE DADOS
                CONTEÚDO: Banco de dados relacional
                """;
        var atual = new ParserDeterministicoDoEdital().extrair(texto,
                new FonteDoEdital("edital.pdf", "a".repeat(64), 2));
        String chaveDoCargo = atual.cargos().getFirst().chave();
        var arvore = arvore(
                dado("Engenheiro de Dados", 2,
                        "CARGO: ENGENHEIRO DE DADOS"),
                dado("Banco de Dados", 2,
                        "Banco de dados relacional"));

        var resultado = new ConversorDaInterpretacaoAssistidaDoEdital()
                .converter(atual, arvore, chaveDoCargo, texto);

        var cargo = resultado.extracao().cargos().stream()
                .filter(item -> chaveDoCargo.equals(item.chave()))
                .findFirst().orElseThrow();
        assertThat(cargo.nome().inferido()).isFalse();
        assertThat(cargo.nome().fonte().pagina()).isEqualTo(2);
        assertThat(resultado.extracao().provas())
                .allMatch(prova -> chaveDoCargo.equals(
                        prova.chaveDoCargo()));
        var materia = resultado.extracao().materias().stream()
                .filter(item -> chaveDoCargo.equals(item.chaveDoCargo()))
                .findFirst().orElseThrow();
        assertThat(materia.chaveDaProva())
                .isEqualTo(resultado.extracao().provas().getLast().chave());
        assertThat(resultado.extracao().provas().getLast().grupos())
                .extracting(grupo -> grupo.chave())
                .contains(materia.chaveDoGrupo());
        assertThat(materia.topicos()).hasSize(1);
        assertThat(materia.itensDoEdital().getFirst()
                .chaveDoTopicoSugerido())
                .isEqualTo(materia.topicos().getFirst().chave());
    }

    @Test
    void evidenciaFalsaViraInferenciaEProblemaDeRevisao() {
        String texto = """
                CONCURSO: Tribunal
                EDITAL: Edital 1
                CARGO: Analista
                ESCOLARIDADE: SUPERIOR
                PROVA: Objetiva
                TIPO: OBJETIVA
                CARÁTER: ELIMINATORIO
                GRUPO: Conhecimentos
                MATÉRIA: Direito
                TÓPICO: 1 - Atos
                """;
        var atual = new ParserDeterministicoDoEdital().extrair(texto,
                new FonteDoEdital("edital.pdf", "a".repeat(64), 1));
        String chaveDoCargo = atual.cargos().getFirst().chave();
        var arvore = arvore(
                dado("Engenheiro de Dados", 1, "trecho inexistente"),
                dado("Banco de Dados", null, null));

        var resultado = new ConversorDaInterpretacaoAssistidaDoEdital()
                .converter(atual, arvore, chaveDoCargo, texto);

        var cargo = resultado.extracao().cargos().getFirst();
        assertThat(cargo.nome().inferido()).isTrue();
        assertThat(cargo.nome().fonte().pagina()).isNull();
        assertThat(resultado.problemasAdicionais()).anySatisfy(problema -> {
            assertThat(problema.codigo())
                    .isEqualTo("EVIDENCIA_ASSISTIDA_NAO_VERIFICADA");
            assertThat(problema.chaveDoRecurso()).isEqualTo(chaveDoCargo);
            assertThat(problema.campo()).isEqualTo("nome");
        });
    }

    @Test
    void omiteVinculosCujasReferenciasNaoPossuemEvidenciaVerificavel() {
        String texto = """
                CONCURSO: Tribunal
                EDITAL: Edital 1
                CARGO: Analista
                TÓPICO: 1 - Banco de dados
                TÓPICO: 1.1 - Modelagem
                """;
        var atual = new ParserDeterministicoDoEdital().extrair(texto,
                new FonteDoEdital("edital.pdf", "a".repeat(64), 1));
        String chaveDoCargo = atual.cargos().getFirst().chave();
        var arvore = arvoreComVinculosNaoVerificados();

        var resultado = new ConversorDaInterpretacaoAssistidaDoEdital()
                .converter(atual, arvore, chaveDoCargo, texto);

        var materia = resultado.extracao().materias().getFirst();
        assertThat(materia.topicos().get(1).chaveDoPai()).isNull();
        assertThat(materia.itensDoEdital().getFirst()
                .chaveDoTopicoSugerido()).isNull();
        assertThat(resultado.problemasAdicionais())
                .filteredOn(problema -> "EVIDENCIA_ASSISTIDA_NAO_VERIFICADA"
                        .equals(problema.codigo()))
                .extracting("campo")
                .contains("chaveDoPai", "chaveDoTopicoSugerido");
    }

    private ArvoreInterpretadaDoEdital arvore(
            DadoTextualInterpretado nomeDoCargo,
            DadoTextualInterpretado nomeDaMateria) {
        DadoTextualInterpretado ausente = dado(null, null, null);
        var topico = new TopicoInterpretado(
                dado("1", 1, "TÓPICO: 1 - Atos"), ausente,
                dado("Banco de dados", null, null), ausente);
        var item = new ItemInterpretado(
                dado("1", null, null),
                dado("1", 1, "TÓPICO: 1 - Atos"),
                dado("Banco de dados relacional", null, null));
        var materia = new MateriaInterpretada(nomeDaMateria, ausente,
                decimalAusente(), inteiroAusente(), decimalAusente(),
                List.of(topico), List.of(item));
        var grupo = new GrupoInterpretado(
                dado("Conhecimentos específicos", null, null),
                inteiroAusente(), decimalAusente(), decimalAusente(),
                List.of(materia));
        var prova = new ProvaInterpretada(
                dado("Prova objetiva", null, null),
                dado("OBJETIVA", null, null),
                dado("ELIMINATORIO_E_CLASSIFICATORIO", null, null),
                ausente, inteiroAusente(), inteiroAusente(),
                decimalAusente(), decimalAusente(), List.of(grupo),
                List.of());
        return new ArvoreInterpretadaDoEdital(
                new ConcursoInterpretado(ausente, ausente, ausente, ausente),
                new EditalInterpretado(ausente, ausente, inteiroAusente(),
                        ausente),
                new CargoInterpretado(nomeDoCargo, ausente, ausente,
                        dado("SUPERIOR", null, null), List.of(prova)));
    }

    private ArvoreInterpretadaDoEdital
            arvoreComVinculosNaoVerificados() {
        DadoTextualInterpretado ausente = dado(null, null, null);
        var raiz = new TopicoInterpretado(
                dado("1", 1, "TÓPICO: 1 - Banco de dados"), ausente,
                dado("Banco de dados", 1,
                        "TÓPICO: 1 - Banco de dados"), ausente);
        var filho = new TopicoInterpretado(
                dado("1.1", 1, "TÓPICO: 1.1 - Modelagem"),
                dado("1", null, null),
                dado("Modelagem", 1, "TÓPICO: 1.1 - Modelagem"), ausente);
        var item = new ItemInterpretado(
                dado("1", null, null), dado("1.1", null, null),
                dado("Modelagem relacional", null, null));
        var materia = new MateriaInterpretada(
                dado("Banco de Dados", null, null), ausente,
                decimalAusente(), inteiroAusente(), decimalAusente(),
                List.of(raiz, filho), List.of(item));
        var grupo = new GrupoInterpretado(
                dado("Conhecimentos", null, null), inteiroAusente(),
                decimalAusente(), decimalAusente(), List.of(materia));
        var prova = new ProvaInterpretada(
                dado("Objetiva", null, null), dado("OBJETIVA", null, null),
                dado("CLASSIFICATORIO", null, null), ausente,
                inteiroAusente(), inteiroAusente(), decimalAusente(),
                decimalAusente(), List.of(grupo), List.of());
        return new ArvoreInterpretadaDoEdital(
                new ConcursoInterpretado(ausente, ausente, ausente, ausente),
                new EditalInterpretado(ausente, ausente, inteiroAusente(),
                        ausente),
                new CargoInterpretado(dado("Analista", null, null),
                        ausente, ausente, dado("SUPERIOR", null, null),
                        List.of(prova)));
    }

    private DadoTextualInterpretado dado(String valor, Integer pagina,
            String trecho) {
        return new DadoTextualInterpretado(valor,
                new EvidenciaInterpretada(pagina, trecho));
    }

    private DadoInteiroInterpretado inteiroAusente() {
        return new DadoInteiroInterpretado(null,
                new EvidenciaInterpretada(null, null));
    }

    private DadoDecimalInterpretado decimalAusente() {
        return new DadoDecimalInterpretado(null,
                new EvidenciaInterpretada(null, null));
    }
}
