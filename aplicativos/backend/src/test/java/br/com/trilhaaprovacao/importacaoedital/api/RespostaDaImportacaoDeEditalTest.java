package br.com.trilhaaprovacao.importacaoedital.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.trilhaaprovacao.importacaoedital.aplicacao.ConsultaDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ParserDeterministicoDoEdital;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ResultadoDoStagingDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ValidadorDaExtracaoDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.EstadoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.FonteDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ModoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.PoliticaDeReutilizacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.TipoDaFonteDoEdital;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RespostaDaImportacaoDeEditalTest {

    @Test
    void depoisDaSelecaoExibeSomentePendenciasGlobaisEDaArvoreDoCargo() {
        String texto = """
                CONCURSO: Tribunal
                EDITAL: Edital 1
                CARGO: Engenheiro de Dados
                ESCOLARIDADE: SUPERIOR
                PROVA: Objetiva
                TIPO: OBJETIVA
                CARÁTER: ELIMINATORIO_E_CLASSIFICATORIO
                GRUPO: Conhecimentos
                MATÉRIA: Banco de Dados
                TÓPICO: 1 - SQL
                CARGO: Analista sem escolaridade
                PROVA: Objetiva
                TIPO: OBJETIVA
                CARÁTER: ELIMINATORIO
                GRUPO: Conhecimentos
                MATÉRIA: Banco de Dados
                TÓPICO: 1 - Atos
                """;
        var extracao = new ParserDeterministicoDoEdital().extrair(texto,
                new FonteDoEdital("edital.txt", "a".repeat(64), 1));
        var validador = new ValidadorDaExtracaoDoEdital();
        String selecionado = extracao.cargos().getFirst().chave();
        OffsetDateTime agora = OffsetDateTime.now();
        ImportacaoDeEdital importacao = new ImportacaoDeEdital(
                UUID.randomUUID(), UUID.randomUUID(),
                EstadoDaImportacaoDeEdital.VALIDADA,
                TipoDaFonteDoEdital.TEXTO, "edital.txt", "text/plain",
                "a".repeat(64), texto.length(), 1, "b".repeat(64),
                selecionado, null, agora, agora);
        var consulta = new ConsultaDaImportacaoDeEdital(
                new ResultadoDoStagingDaImportacao(importacao, extracao,
                        validador.validar(extracao)),
                ModoDaImportacaoDeEdital.CRIAR_NOVO, null,
                PoliticaDeReutilizacao.EXIGIR_DECISAO, null, 1);

        RespostaDaImportacaoDeEdital resposta =
                RespostaDaImportacaoDeEdital.de(consulta, true);

        assertThat(resposta.interpretacaoAssistidaDisponivel()).isTrue();
        assertThat(resposta.problemas())
                .noneMatch(problema -> "CARGO_SEM_ESCOLARIDADE"
                        .equals(problema.codigo()));
        assertThat(resposta.avaliacoesDosCargos()).hasSize(2);
        assertThat(resposta.avaliacoesDosCargos().getFirst().pronto())
                .as(resposta.avaliacoesDosCargos().getFirst().problemas()
                        .toString())
                .isTrue();
        assertThat(resposta.avaliacoesDosCargos().getLast().pronto())
                .isFalse();
        assertThat(resposta.avaliacoesDosCargos().getLast().problemas())
                .anyMatch(problema -> "CARGO_SEM_ESCOLARIDADE"
                        .equals(problema.codigo()));
        assertThat(resposta.avaliacoesDosCargos())
                .allSatisfy(avaliacao ->
                        assertThat(avaliacao.problemas())
                                .noneMatch(problema ->
                                        "MATERIA_DUPLICADA".equals(
                                                problema.codigo())));
    }
}
