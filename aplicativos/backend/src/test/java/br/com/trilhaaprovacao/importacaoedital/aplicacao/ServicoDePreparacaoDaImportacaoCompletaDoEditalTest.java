package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.PreparadorDaImportacaoCompletaDoEdital.SolicitacaoDePreparacaoDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.EstadoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.FonteDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ModoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.PoliticaDeReutilizacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.ProblemaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.SeveridadeDoProblemaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.TipoDaFonteDoEdital;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.ImportacaoDeEditalPersistida;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.RepositorioDeImportacoesDeEdital;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.RepositorioDeProvenienciasDaImportacaoDoEdital;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.RepositorioDeRelatoriosDaImportacaoDoEdital;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import tools.jackson.databind.ObjectMapper;

class ServicoDePreparacaoDaImportacaoCompletaDoEditalTest {
    private static final String HASH_DA_FONTE = "0".repeat(64);
    private static final String HASH_DA_EXTRACAO = "1".repeat(64);

    @Test
    void detectaTodasAsMateriasEquivalentesSemEscolherCandidatoSilenciosamente() {
        UUID primeira = UUID.randomUUID();
        UUID segunda = UUID.randomUUID();
        BancoDeCatalogoFake banco = new BancoDeCatalogoFake();
        banco.materias.add(registro("identificador", primeira, "nome",
                "LINGUA   PORTUGUESA - GRAMATICA", "versao", 1L,
                "arquivada", false));
        banco.materias.add(registro("identificador", segunda, "nome",
                "Língua Portuguesa: Gramática", "versao", 2L,
                "arquivada", false));
        Cenario cenario = cenario(banco, Map.of());

        var previa = cenario.servico().previsualizar(cenario.solicitacao());

        assertThat(previa.conflitos()).extracting("codigo")
                .contains("MATERIA_EXISTENTE_AMBIGUA");
        assertThat(previa.itensAReutilizar()).filteredOn(
                        item -> item.tipo().equals("MATERIA"))
                .extracting("identificadorExistente")
                .containsExactlyInAnyOrder(primeira, segunda);
    }

    @Test
    void restringeCandidatosDeTopicoAMateriaEAoPaiEsperados() {
        UUID materia = UUID.randomUUID();
        UUID raiz = UUID.randomUUID();
        UUID filhoA = UUID.randomUUID();
        UUID filhoB = UUID.randomUUID();
        UUID filhoDeOutroPai = UUID.randomUUID();
        UUID outroPai = UUID.randomUUID();
        BancoDeCatalogoFake banco = new BancoDeCatalogoFake();
        banco.registrarRecurso(materia, "MATERIA", null);
        banco.registrarRecurso(raiz, "TOPICO", null);
        banco.topicos.add(registro("identificador", filhoA,
                "topico_pai_id", raiz, "nome", "Direitos—fundamentais",
                "versao", 1L, "arquivado", false));
        banco.topicos.add(registro("identificador", filhoB,
                "topico_pai_id", raiz, "nome",
                "  DIREITOS : FUNDAMENTAIS  ", "versao", 1L,
                "arquivado", false));
        banco.topicos.add(registro("identificador", filhoDeOutroPai,
                "topico_pai_id", outroPai, "nome", "Direitos fundamentais",
                "versao", 1L, "arquivado", false));
        ExtracaoEstruturadaDoEdital extracao = extracao();
        var materiaExtraida = extracao.materias().getFirst();
        var raizExtraida = materiaExtraida.topicos().getFirst();
        var filhoExtraido = materiaExtraida.topicos().get(1);
        Cenario cenario = cenario(banco, Map.of(
                materiaExtraida.chave(), materia,
                raizExtraida.chave(), raiz), extracao);

        var previa = cenario.servico().previsualizar(cenario.solicitacao());

        assertThat(previa.conflitos()).extracting("codigo")
                .contains("TOPICO_EXISTENTE_AMBIGUO");
        assertThat(previa.itensAReutilizar()).filteredOn(
                        item -> item.chave().equals(filhoExtraido.chave()))
                .extracting("identificadorExistente")
                .containsExactlyInAnyOrder(filhoA, filhoB)
                .doesNotContain(filhoDeOutroPai);
    }

    @Test
    void rejeitaTopicoEscolhidoSobPaiDiferenteDoConfirmado() {
        UUID materia = UUID.randomUUID();
        UUID raiz = UUID.randomUUID();
        UUID filho = UUID.randomUUID();
        UUID outroPai = UUID.randomUUID();
        BancoDeCatalogoFake banco = new BancoDeCatalogoFake();
        banco.registrarRecurso(materia, "MATERIA", null);
        banco.registrarRecurso(raiz, "TOPICO", null);
        banco.registrarRecurso(filho, "TOPICO", outroPai);
        ExtracaoEstruturadaDoEdital extracao = extracao();
        var materiaExtraida = extracao.materias().getFirst();
        var raizExtraida = materiaExtraida.topicos().getFirst();
        var filhoExtraido = materiaExtraida.topicos().get(1);
        Cenario cenario = cenario(banco, Map.of(
                materiaExtraida.chave(), materia,
                raizExtraida.chave(), raiz,
                filhoExtraido.chave(), filho), extracao);

        assertThatThrownBy(() -> cenario.servico().previsualizar(
                cenario.solicitacao()))
                .isInstanceOfSatisfying(RegraDeDominio.class,
                        excecao -> assertThat(excecao.codigo()).isEqualTo(
                                "HIERARQUIA_DO_TOPICO_REUTILIZADO_DIVERGENTE"));
    }

    @Test
    void evidenciaAssistidaDoCargoBloqueiaPreparacaoAteSerConfirmada() {
        ExtracaoEstruturadaDoEdital extracao = extracao();
        String cargo = extracao.cargos().getFirst().chave();
        ProblemaDaImportacao evidencia = new ProblemaDaImportacao(
                SeveridadeDoProblemaDaImportacao.EXIGE_DECISAO,
                "EVIDENCIA_ASSISTIDA_NAO_VERIFICADA",
                "Confira o valor sugerido.", null,
                "cargo", cargo, "nome");
        Cenario pendente = cenario(new BancoDeCatalogoFake(), Map.of(),
                extracao, List.of(evidencia));

        assertThat(pendente.servico().previsualizar(
                pendente.solicitacao()).conflitos())
                .extracting("codigo")
                .contains("EVIDENCIA_ASSISTIDA_NAO_VERIFICADA");
        assertThatThrownBy(() -> pendente.servico().preparar(
                pendente.solicitacao()))
                .isInstanceOfSatisfying(RegraDeDominio.class,
                        erro -> assertThat(erro.codigo()).isEqualTo(
                                "IMPORTACAO_EXIGE_DECISOES"));

        Cenario confirmado = cenario(new BancoDeCatalogoFake(), Map.of(),
                extracao, List.of());
        assertThat(confirmado.servico().previsualizar(
                confirmado.solicitacao()).conflitos())
                .extracting("codigo")
                .doesNotContain("EVIDENCIA_ASSISTIDA_NAO_VERIFICADA");
    }

    private Cenario cenario(BancoDeCatalogoFake banco,
            Map<String, UUID> decisoes) {
        return cenario(banco, decisoes, extracao());
    }

    private Cenario cenario(BancoDeCatalogoFake banco,
            Map<String, UUID> decisoes,
            ExtracaoEstruturadaDoEdital extracao) {
        return cenario(banco, decisoes, extracao, List.of());
    }

    private Cenario cenario(BancoDeCatalogoFake banco,
            Map<String, UUID> decisoes,
            ExtracaoEstruturadaDoEdital extracao,
            List<ProblemaDaImportacao> problemas) {
        UUID usuario = UUID.randomUUID();
        byte[] conteudo = "edital".getBytes(StandardCharsets.UTF_8);
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        ImportacaoDeEdital importacao = ImportacaoDeEdital.receber(usuario,
                TipoDaFonteDoEdital.TEXTO, "edital.txt", "text/plain",
                HASH_DA_FONTE, conteudo.length, agora);
        importacao.iniciarExtracao(agora);
        importacao.registrarExtracao(1, HASH_DA_EXTRACAO,
                EstadoDaImportacaoDeEdital.VALIDADA, agora);
        ImportacaoDeEditalPersistida persistida =
                new ImportacaoDeEditalPersistida(importacao, conteudo,
                        agora.plusDays(1));
        RepositorioDeImportacoesDeEdital importacoes =
                mock(RepositorioDeImportacoesDeEdital.class);
        when(importacoes.encontrarParaAtualizacao(importacao.identificador(),
                usuario)).thenReturn(Optional.of(persistida));
        when(importacoes.findByIdentificadorAndIdentificadorDoUsuario(
                importacao.identificador(), usuario))
                .thenReturn(Optional.of(persistida));
        ServicoDeStagingDaImportacaoDeEdital staging =
                mock(ServicoDeStagingDaImportacaoDeEdital.class);
        when(staging.obterExtracaoAtual(usuario, importacao.identificador()))
                .thenReturn(new ResultadoDoStagingDaImportacao(importacao,
                        extracao, problemas));
        ServicoDePreparacaoDaImportacaoCompletaDoEdital servico =
                new ServicoDePreparacaoDaImportacaoCompletaDoEdital(staging,
                        mock(ServicoDeAplicacaoDaEstruturaDoEdital.class),
                        importacoes,
                        mock(RepositorioDeRelatoriosDaImportacaoDoEdital.class),
                        mock(RepositorioDeProvenienciasDaImportacaoDoEdital.class),
                        banco, new ObjectMapper());
        String cargo = extracao.cargos().getFirst().chave();
        var solicitacao = new SolicitacaoDePreparacaoDaImportacao(usuario,
                importacao.identificador(), cargo,
                ModoDaImportacaoDeEdital.CRIAR_NOVO, null,
                PoliticaDeReutilizacao.EXIGIR_DECISAO,
                new DecisoesDaImportacaoDoEdital(decisoes, true, true));
        return new Cenario(servico, solicitacao);
    }

    private ExtracaoEstruturadaDoEdital extracao() {
        return new ParserDeterministicoDoEdital().extrair("""
                CONCURSO: Controle Público
                EDITAL: Edital de abertura
                CARGO: Auditor
                ESCOLARIDADE: SUPERIOR
                PROVA: Prova objetiva
                TIPO: OBJETIVA
                CARÁTER: CLASSIFICATORIO
                GRUPO: Conhecimentos específicos
                MATÉRIA: Língua Portuguesa — Gramática!
                TÓPICO: 1 - Constituição
                TÓPICO: 1.1 - Direitos fundamentais!
                """, new FonteDoEdital("edital.txt", HASH_DA_FONTE, 1));
    }

    private static Map<String, Object> registro(Object... pares) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        for (int indice = 0; indice < pares.length; indice += 2) {
            resultado.put(pares[indice].toString(), pares[indice + 1]);
        }
        return resultado;
    }

    private record Cenario(
            ServicoDePreparacaoDaImportacaoCompletaDoEdital servico,
            SolicitacaoDePreparacaoDaImportacao solicitacao) {
    }

    private static final class BancoDeCatalogoFake extends JdbcTemplate {
        private final List<Map<String, Object>> materias = new ArrayList<>();
        private final List<Map<String, Object>> topicos = new ArrayList<>();
        private final Map<UUID, String> tiposDosRecursos = new LinkedHashMap<>();
        private final Map<UUID, UUID> paisDosTopicos = new LinkedHashMap<>();

        private void registrarRecurso(UUID identificador, String tipo,
                UUID pai) {
            tiposDosRecursos.put(identificador, tipo);
            if ("TOPICO".equals(tipo)) paisDosTopicos.put(identificador, pai);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql,
                Object... argumentos) {
            if (sql.contains("UNION ALL")) {
                UUID recurso = (UUID) argumentos[0];
                String tipo = tiposDosRecursos.get(recurso);
                return tipo == null ? List.of() : List.of(registro(
                        "tipo", tipo, "identificador", recurso, "versao", 1L,
                        "arquivado", false));
            }
            if (sql.contains("WHERE t.identificador = ?")) {
                UUID topico = (UUID) argumentos[0];
                if (!tiposDosRecursos.containsKey(topico)) return List.of();
                return List.of(registro("arquivado", false,
                        "topico_pai_id", paisDosTopicos.get(topico),
                        "materia_arquivada", false));
            }
            if (sql.contains("FROM materias")
                    && sql.contains("ORDER BY nome")) {
                return List.copyOf(materias);
            }
            if (sql.contains("FROM topicos_da_materia")
                    && sql.contains("ORDER BY nome")) {
                return List.copyOf(topicos);
            }
            throw new AssertionError("Consulta inesperada: " + sql);
        }

        @Override
        public Map<String, Object> queryForMap(String sql,
                Object... argumentos) {
            if (sql.contains("versoes_das_materias")) {
                return registro("materias", materias.size(),
                        "versoes_das_materias", 0L, "topicos",
                        topicos.size(), "versoes_dos_topicos", 0L);
            }
            throw new AssertionError("Consulta inesperada: " + sql);
        }

        @Override
        public <T> T query(String sql, ResultSetExtractor<T> extrator,
                Object... argumentos) {
            if (sql.contains("pg_advisory_xact_lock")) return null;
            throw new AssertionError("Consulta inesperada: " + sql);
        }
    }
}
