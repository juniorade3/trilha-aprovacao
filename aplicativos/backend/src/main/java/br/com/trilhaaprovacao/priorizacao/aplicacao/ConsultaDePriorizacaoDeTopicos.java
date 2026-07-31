package br.com.trilhaaprovacao.priorizacao.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.concursos.aplicacao.ConsultaDoContextoDeConteudoExigido;
import br.com.trilhaaprovacao.concursos.aplicacao.ContextoDeConteudoExigido;
import br.com.trilhaaprovacao.priorizacao.aplicacao.ResultadoDaPriorizacaoDeTopicos.Contexto;
import br.com.trilhaaprovacao.priorizacao.aplicacao.ResultadoDaPriorizacaoDeTopicos.Indicadores;
import br.com.trilhaaprovacao.priorizacao.aplicacao.ResultadoDaPriorizacaoDeTopicos.ItemSemMapeamento;
import br.com.trilhaaprovacao.priorizacao.aplicacao.ResultadoDaPriorizacaoDeTopicos.MateriaPriorizada;
import br.com.trilhaaprovacao.priorizacao.aplicacao.ResultadoDaPriorizacaoDeTopicos.ReferenciaOficial;
import br.com.trilhaaprovacao.priorizacao.aplicacao.ResultadoDaPriorizacaoDeTopicos.Resumo;
import br.com.trilhaaprovacao.priorizacao.aplicacao.ResultadoDaPriorizacaoDeTopicos.TopicoPriorizado;
import br.com.trilhaaprovacao.priorizacao.dominio.ClassificacaoDaPriorizacao;
import br.com.trilhaaprovacao.priorizacao.dominio.ClassificadorDePriorizacao;
import br.com.trilhaaprovacao.priorizacao.dominio.GrupoDePriorizacao;
import br.com.trilhaaprovacao.priorizacao.dominio.SinaisDePriorizacao;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaDePriorizacaoDeTopicos {
    private static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate banco;
    private final ConsultaDoContextoDeConteudoExigido contextos;

    public ConsultaDePriorizacaoDeTopicos(
            JdbcTemplate banco, ConsultaDoContextoDeConteudoExigido contextos) {
        this.banco = banco;
        this.contextos = contextos;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ,
            noRollbackFor = RegraDeDominio.class)
    public ResultadoDaPriorizacaoDeTopicos consultar(
            UUID usuario, LocalDate referencia, UUID materia) {
        if (usuario == null || referencia == null) {
            throw new IllegalArgumentException("Usuario e data de referencia sao obrigatorios.");
        }
        ContextoDeConteudoExigido contexto = contextos.consultar(usuario)
                .filter(ContextoDeConteudoExigido::estaCompleto)
                .orElseThrow(() -> new RegraDeDominio(
                        "CONTEXTO_DE_PRIORIZACAO_INCOMPLETO",
                        "Ative um concurso, selecione um cargo e defina o edital principal."));
        LocalDate inicio = referencia.minusDays(29);
        List<TopicoLido> topicos = consultarTopicos(
                usuario, contexto, inicio, referencia, materia);
        List<ItemSemMapeamento> itensSemMapeamento = consultarItensSemMapeamento(
                usuario, contexto, materia);

        Map<UUID, List<TopicoLido>> porMateria = new LinkedHashMap<>();
        topicos.stream().sorted(ordemDasMaterias().thenComparing(ordemDosTopicos()))
                .forEach(topico -> porMateria.computeIfAbsent(
                        topico.identificadorDaMateria(), ignorado -> new ArrayList<>())
                        .add(topico));

        List<MateriaPriorizada> materias = new ArrayList<>();
        for (List<TopicoLido> topicosDaMateria : porMateria.values()) {
            List<TopicoPriorizado> priorizados = priorizar(topicosDaMateria);
            TopicoLido primeiro = topicosDaMateria.getFirst();
            materias.add(new MateriaPriorizada(primeiro.identificadorDaMateria(),
                    primeiro.nomeDaMateria(), priorizados));
        }

        long lacunas = topicos.stream()
                .filter(item -> item.classificacao().grupo() == GrupoDePriorizacao.LACUNA)
                .count();
        long fraquezas = topicos.stream()
                .filter(item -> item.classificacao().grupo() == GrupoDePriorizacao.FRAQUEZA)
                .count();
        long consolidados = topicos.size() - lacunas - fraquezas;
        long itensOficiais = contarItensOficiais(usuario, contexto, materia);
        Resumo resumo = new Resumo(itensOficiais, itensSemMapeamento.size(), topicos.size(),
                lacunas, fraquezas, consolidados);
        return new ResultadoDaPriorizacaoDeTopicos(
                contexto(contexto, referencia, inicio), resumo,
                List.copyOf(itensSemMapeamento), List.copyOf(materias));
    }

    private List<TopicoLido> consultarTopicos(UUID usuario,
            ContextoDeConteudoExigido contexto, LocalDate inicio,
            LocalDate referencia, UUID materia) {
        return banco.query("""
                WITH itens_oficiais AS (
                    SELECT i.identificador AS item_id, i.ordem AS item_ordem,
                           mp.materia_id, mp.ordem AS materia_ordem,
                           mapa.topico_da_materia_id AS topico_id
                    FROM itens_do_edital i
                    JOIN materias_da_prova mp
                      ON mp.identificador = i.materia_da_prova_id
                    JOIN grupos_de_conteudo g
                      ON g.identificador = mp.grupo_de_conteudo_id
                    JOIN provas p ON p.identificador = g.prova_id
                    JOIN mapeamentos_de_itens_do_edital mapa
                      ON mapa.item_do_edital_id = i.identificador
                     AND mapa.confirmado = TRUE
                    WHERE i.edital_id = ? AND p.cargo_id = ?
                ), topicos_exigidos AS (
                    SELECT io.topico_id, io.materia_id,
                           COUNT(DISTINCT io.item_id) AS quantidade_itens,
                           MIN(io.materia_ordem) AS materia_ordem,
                           MIN(io.item_ordem) AS item_ordem
                    FROM itens_oficiais io
                    JOIN topicos_da_materia t ON t.identificador = io.topico_id
                    JOIN materias m ON m.identificador = t.materia_id
                    WHERE t.materia_id = io.materia_id
                      AND t.arquivado = FALSE AND m.arquivada = FALSE
                      AND m.usuario_id = ?
                    GROUP BY io.topico_id, io.materia_id
                ), fatos AS (
                    SELECT r.identificador AS registro_id, r.topico_id,
                           r.data_hora, r.tipo_de_estudo,
                           e.identificador AS evidencia_id,
                           e.quantidade_de_questoes, e.quantidade_de_acertos,
                           e.nivel_de_recordacao, e.dificuldade_percebida
                    FROM registros_de_estudo r
                    JOIN topicos_da_materia topico_do_fato
                      ON topico_do_fato.identificador = r.topico_id
                    JOIN materias materia_do_fato
                      ON materia_do_fato.identificador = topico_do_fato.materia_id
                     AND materia_do_fato.usuario_id = ?
                    LEFT JOIN evidencias_de_aprendizagem e
                      ON e.registro_de_estudo_id = r.identificador
                    WHERE r.situacao = 'ATIVO'
                      AND (r.data_hora AT TIME ZONE 'America/Sao_Paulo')::date <= ?
                ), fatos_agregados AS (
                    SELECT f.topico_id,
                           COUNT(DISTINCT f.registro_id) AS estudos,
                           COUNT(f.evidencia_id) AS evidencias,
                           COALESCE(SUM(f.quantidade_de_questoes) FILTER (WHERE
                             (f.data_hora AT TIME ZONE 'America/Sao_Paulo')::date
                               BETWEEN ? AND ?), 0) AS questoes_recentes,
                           COALESCE(SUM(f.quantidade_de_acertos) FILTER (WHERE
                             (f.data_hora AT TIME ZONE 'America/Sao_Paulo')::date
                               BETWEEN ? AND ?), 0) AS acertos_recentes,
                           COALESCE(SUM(f.quantidade_de_questoes - f.quantidade_de_acertos)
                             FILTER (WHERE (f.data_hora AT TIME ZONE
                               'America/Sao_Paulo')::date BETWEEN ? AND ?), 0)
                             AS erros_recentes,
                           (ARRAY_AGG(f.nivel_de_recordacao ORDER BY f.data_hora DESC,
                             f.evidencia_id DESC) FILTER (WHERE f.tipo_de_estudo = 'REVISAO'
                             AND f.nivel_de_recordacao IS NOT NULL
                             AND (f.data_hora AT TIME ZONE 'America/Sao_Paulo')::date
                               BETWEEN ? AND ?))[1] AS ultima_recordacao,
                           (ARRAY_AGG(f.dificuldade_percebida ORDER BY f.data_hora DESC,
                             f.evidencia_id DESC) FILTER (WHERE
                             f.dificuldade_percebida IS NOT NULL))[1] AS ultima_dificuldade,
                           MAX(f.data_hora) FILTER (WHERE f.evidencia_id IS NOT NULL)
                             AS ultima_evidencia
                    FROM fatos f GROUP BY f.topico_id
                ), materiais_ativos AS (
                    SELECT DISTINCT cobertura.topico_id
                    FROM coberturas_de_topicos_por_material cobertura
                    JOIN materiais_de_estudo material
                      ON material.identificador = cobertura.material_id
                    WHERE material.usuario_id = ? AND material.arquivado = FALSE
                ), padroes_repetidos AS (
                    SELECT p.topico_id, p.identificador,
                           MAX((r.data_hora AT TIME ZONE 'America/Sao_Paulo')::date)
                             AS ultima_ocorrencia
                    FROM padroes_de_erro p
                    JOIN ocorrencias_de_padrao_de_erro o
                      ON o.padrao_de_erro_id = p.identificador
                    JOIN evidencias_de_aprendizagem e ON e.identificador = o.evidencia_id
                    JOIN registros_de_estudo r
                      ON r.identificador = e.registro_de_estudo_id
                    WHERE p.usuario_id = ? AND r.situacao = 'ATIVO'
                      AND r.topico_id = p.topico_id
                      AND (r.data_hora AT TIME ZONE 'America/Sao_Paulo')::date <= ?
                    GROUP BY p.topico_id, p.identificador
                    HAVING COUNT(DISTINCT e.identificador) >= 2
                       AND MAX((r.data_hora AT TIME ZONE 'America/Sao_Paulo')::date) >= ?
                ), padroes_agregados AS (
                    SELECT topico_id, COUNT(*) AS quantidade,
                           MAX(ultima_ocorrencia) AS ultima_ocorrencia
                    FROM padroes_repetidos GROUP BY topico_id
                )
                SELECT te.topico_id, t.nome AS topico_nome,
                       t.nome_normalizado AS topico_nome_normalizado,
                       t.ordem AS topico_ordem,
                       m.identificador AS materia_id, m.nome AS materia_nome,
                       m.nome_normalizado AS materia_nome_normalizado,
                       te.materia_ordem, te.item_ordem, te.quantidade_itens,
                       (ma.topico_id IS NOT NULL) AS possui_material,
                       COALESCE(fa.estudos, 0) AS estudos,
                       COALESCE(fa.evidencias, 0) AS evidencias,
                       COALESCE(fa.questoes_recentes, 0) AS questoes_recentes,
                       COALESCE(fa.acertos_recentes, 0) AS acertos_recentes,
                       COALESCE(fa.erros_recentes, 0) AS erros_recentes,
                       fa.ultima_recordacao, fa.ultima_dificuldade,
                       fa.ultima_evidencia,
                       COALESCE(pa.quantidade, 0) AS quantidade_padroes,
                       pa.ultima_ocorrencia
                FROM topicos_exigidos te
                JOIN topicos_da_materia t ON t.identificador = te.topico_id
                JOIN materias m ON m.identificador = te.materia_id
                LEFT JOIN fatos_agregados fa ON fa.topico_id = te.topico_id
                LEFT JOIN materiais_ativos ma ON ma.topico_id = te.topico_id
                LEFT JOIN padroes_agregados pa ON pa.topico_id = te.topico_id
                WHERE (CAST(? AS UUID) IS NULL OR m.identificador = CAST(? AS UUID))
                """, (resultado, linha) -> mapearTopico(resultado, inicio),
                contexto.identificadorDoEdital(), contexto.identificadorDoCargo(), usuario,
                usuario, referencia, inicio, referencia, inicio, referencia, inicio, referencia,
                inicio, referencia, usuario, usuario, referencia, inicio, materia, materia);
    }

    private TopicoLido mapearTopico(ResultSet resultado, LocalDate inicio)
            throws SQLException {
        long questoes = resultado.getLong("questoes_recentes");
        long acertos = resultado.getLong("acertos_recentes");
        BigDecimal percentual = questoes == 0 ? null : BigDecimal.valueOf(acertos)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(questoes), 2, RoundingMode.HALF_UP);
        OffsetDateTime ultimaEvidencia = resultado.getObject(
                "ultima_evidencia", OffsetDateTime.class);
        Integer recordacao = inteiro(resultado, "ultima_recordacao");
        Integer dificuldade = inteiro(resultado, "ultima_dificuldade");
        LocalDate ultimaOcorrencia = resultado.getObject(
                "ultima_ocorrencia", LocalDate.class);
        SinaisDePriorizacao sinais = new SinaisDePriorizacao(
                resultado.getLong("estudos"), resultado.getLong("evidencias"),
                ultimaEvidencia == null ? null
                        : ultimaEvidencia.atZoneSameInstant(FUSO_HORARIO).toLocalDate(),
                questoes, percentual, recordacao, dificuldade,
                resultado.getLong("quantidade_padroes"),
                resultado.getBoolean("possui_material"));
        ClassificacaoDaPriorizacao classificacao =
                ClassificadorDePriorizacao.classificar(sinais, inicio);
        return new TopicoLido(
                resultado.getObject("topico_id", UUID.class),
                resultado.getString("topico_nome"),
                resultado.getString("topico_nome_normalizado"),
                resultado.getInt("topico_ordem"),
                resultado.getObject("materia_id", UUID.class),
                resultado.getString("materia_nome"),
                resultado.getString("materia_nome_normalizado"),
                resultado.getInt("materia_ordem"), resultado.getInt("item_ordem"),
                resultado.getLong("quantidade_itens"), sinais, classificacao,
                resultado.getLong("estudos"), resultado.getLong("evidencias"),
                questoes, acertos, resultado.getLong("erros_recentes"), percentual,
                recordacao, dificuldade, ultimaEvidencia,
                resultado.getLong("quantidade_padroes"), ultimaOcorrencia);
    }

    private List<TopicoPriorizado> priorizar(List<TopicoLido> lidos) {
        List<TopicoLido> ordenados = lidos.stream().sorted(ordemDosTopicos()).toList();
        Map<br.com.trilhaaprovacao.priorizacao.dominio.GrupoDePriorizacao, Integer>
                posicoes = new java.util.EnumMap<>(
                        br.com.trilhaaprovacao.priorizacao.dominio.GrupoDePriorizacao.class);
        return ordenados.stream().map(item -> {
            int posicao = posicoes.merge(item.classificacao().grupo(), 1, Integer::sum);
            Indicadores indicadores = new Indicadores(item.estudos(), item.evidencias(),
                    item.questoesRecentes(), item.acertosRecentes(), item.errosRecentes(),
                    item.percentual(), item.ultimaRecordacao(), item.ultimaDificuldade(),
                    item.ultimaEvidencia(), item.quantidadeDePadroes(),
                    item.ultimaOcorrencia());
            return new TopicoPriorizado(item.identificador(), item.nome(),
                    item.classificacao().grupo(), item.classificacao().faixa(), posicao,
                    item.classificacao().acaoSugerida(), item.sinais().possuiMaterialAtivo(),
                    item.quantidadeDeItens(), indicadores,
                    item.classificacao().justificativas().stream()
                            .map(justificativa -> justificativa.mensagem()).toList());
        }).toList();
    }

    private Comparator<TopicoLido> ordemDasMaterias() {
        return Comparator.comparingInt(TopicoLido::ordemDaMateria)
                .thenComparing(TopicoLido::nomeNormalizadoDaMateria)
                .thenComparing(TopicoLido::identificadorDaMateria);
    }

    private Comparator<TopicoLido> ordemDosTopicos() {
        Comparator<Integer> inteirosNulos = Comparator.nullsLast(Integer::compareTo);
        Comparator<BigDecimal> decimaisNulos = Comparator.nullsLast(BigDecimal::compareTo);
        Comparator<OffsetDateTime> datasNulasPrimeiro =
                Comparator.nullsFirst(OffsetDateTime::compareTo);
        return Comparator.comparingInt(
                        (TopicoLido item) -> item.classificacao().grupo().ordem())
                .thenComparingInt(item -> item.classificacao().faixa().ordem())
                .thenComparing(TopicoLido::ultimaRecordacao, inteirosNulos)
                .thenComparing(TopicoLido::percentual, decimaisNulos)
                .thenComparing(TopicoLido::quantidadeDePadroes, Comparator.reverseOrder())
                .thenComparing(TopicoLido::errosRecentes, Comparator.reverseOrder())
                .thenComparing(TopicoLido::ultimaDificuldade,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(TopicoLido::ultimaEvidencia, datasNulasPrimeiro)
                .thenComparing(TopicoLido::quantidadeDeItens, Comparator.reverseOrder())
                .thenComparingInt(TopicoLido::ordemDoItem)
                .thenComparingInt(TopicoLido::ordemDoTopico)
                .thenComparing(TopicoLido::nomeNormalizado)
                .thenComparing(TopicoLido::identificador);
    }

    private List<ItemSemMapeamento> consultarItensSemMapeamento(UUID usuario,
            ContextoDeConteudoExigido contexto, UUID materia) {
        return banco.query("""
                SELECT i.identificador, i.descricao_original, mp.materia_id,
                       m.nome AS materia_nome, i.ordem
                FROM itens_do_edital i
                JOIN materias_da_prova mp ON mp.identificador = i.materia_da_prova_id
                JOIN grupos_de_conteudo g ON g.identificador = mp.grupo_de_conteudo_id
                JOIN provas p ON p.identificador = g.prova_id
                JOIN materias m ON m.identificador = mp.materia_id
                WHERE i.edital_id = ? AND p.cargo_id = ? AND m.usuario_id = ?
                  AND m.arquivada = FALSE
                  AND (CAST(? AS UUID) IS NULL OR m.identificador = CAST(? AS UUID))
                  AND NOT EXISTS (
                    SELECT 1 FROM mapeamentos_de_itens_do_edital mapa
                    JOIN topicos_da_materia t
                      ON t.identificador = mapa.topico_da_materia_id
                    WHERE mapa.item_do_edital_id = i.identificador
                      AND mapa.confirmado = TRUE AND t.arquivado = FALSE
                      AND t.materia_id = mp.materia_id
                  )
                ORDER BY mp.ordem, m.nome_normalizado, i.ordem, i.identificador
                """, (resultado, linha) -> new ItemSemMapeamento(
                        resultado.getObject("identificador", UUID.class),
                        resultado.getString("descricao_original"),
                        resultado.getObject("materia_id", UUID.class),
                        resultado.getString("materia_nome"), resultado.getInt("ordem")),
                contexto.identificadorDoEdital(), contexto.identificadorDoCargo(), usuario,
                materia, materia);
    }

    private long contarItensOficiais(UUID usuario,
            ContextoDeConteudoExigido contexto, UUID materia) {
        Long total = banco.queryForObject("""
                SELECT COUNT(*) FROM itens_do_edital i
                JOIN materias_da_prova mp ON mp.identificador = i.materia_da_prova_id
                JOIN grupos_de_conteudo g ON g.identificador = mp.grupo_de_conteudo_id
                JOIN provas p ON p.identificador = g.prova_id
                JOIN materias m ON m.identificador = mp.materia_id
                WHERE i.edital_id = ? AND p.cargo_id = ?
                  AND m.usuario_id = ? AND m.arquivada = FALSE
                  AND (CAST(? AS UUID) IS NULL OR mp.materia_id = CAST(? AS UUID))
                """, Long.class, contexto.identificadorDoEdital(),
                contexto.identificadorDoCargo(), usuario, materia, materia);
        return total == null ? 0 : total;
    }

    private Contexto contexto(ContextoDeConteudoExigido dados,
            LocalDate referencia, LocalDate inicio) {
        return new Contexto(
                new ReferenciaOficial(dados.identificadorDoConcurso(), dados.nomeDoConcurso()),
                new ReferenciaOficial(dados.identificadorDoCargo(), dados.nomeDoCargo()),
                new ReferenciaOficial(dados.identificadorDoEdital(), dados.tituloDoEdital()),
                referencia, inicio);
    }

    private Integer inteiro(ResultSet resultado, String coluna) throws SQLException {
        int valor = resultado.getInt(coluna);
        return resultado.wasNull() ? null : valor;
    }

    private record TopicoLido(
            UUID identificador,
            String nome,
            String nomeNormalizado,
            int ordemDoTopico,
            UUID identificadorDaMateria,
            String nomeDaMateria,
            String nomeNormalizadoDaMateria,
            int ordemDaMateria,
            int ordemDoItem,
            long quantidadeDeItens,
            SinaisDePriorizacao sinais,
            ClassificacaoDaPriorizacao classificacao,
            long estudos,
            long evidencias,
            long questoesRecentes,
            long acertosRecentes,
            long errosRecentes,
            BigDecimal percentual,
            Integer ultimaRecordacao,
            Integer ultimaDificuldade,
            OffsetDateTime ultimaEvidencia,
            long quantidadeDePadroes,
            LocalDate ultimaOcorrencia) {
    }
}
