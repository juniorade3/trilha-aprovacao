package br.com.trilhaaprovacao.evidencias.aplicacao;

import br.com.trilhaaprovacao.evidencias.aplicacao.DiagnosticoDeTopico.PadraoRepetido;
import br.com.trilhaaprovacao.evidencias.aplicacao.DiagnosticoDeTopico.TotaisDeQuestoes;
import br.com.trilhaaprovacao.evidencias.dominio.ResultadoDaRevisao;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaDeDiagnosticoDeTopicos {
    private final JdbcTemplate banco;

    public ConsultaDeDiagnosticoDeTopicos(JdbcTemplate banco) {
        this.banco = banco;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<DiagnosticoDeTopico> consultar(UUID usuario, LocalDate referencia,
            UUID materia, boolean somenteExigidos) {
        LocalDate inicio = referencia.minusDays(29);
        Map<UUID, List<PadraoRepetido>> repetidos = padroesRepetidos(
                usuario, referencia, materia, somenteExigidos);
        return banco.query("""
                WITH contexto_oficial AS (
                    SELECT cargo.identificador AS cargo_id,
                           ed.identificador AS edital_id
                    FROM concursos c
                    JOIN cargos_do_concurso cargo
                      ON cargo.concurso_id = c.identificador
                     AND cargo.selecionado = TRUE
                    JOIN editais ed
                      ON ed.concurso_id = c.identificador
                     AND ed.principal = TRUE
                    WHERE c.usuario_id = ? AND c.ativo = TRUE
                ), topicos_exigidos AS (
                    SELECT DISTINCT topico_mapeado.identificador AS topico_id
                    FROM contexto_oficial contexto
                    JOIN itens_do_edital i ON i.edital_id = contexto.edital_id
                    JOIN materias_da_prova mp
                      ON mp.identificador = i.materia_da_prova_id
                    JOIN grupos_de_conteudo g
                      ON g.identificador = mp.grupo_de_conteudo_id
                    JOIN provas p
                      ON p.identificador = g.prova_id
                     AND p.cargo_id = contexto.cargo_id
                    JOIN mapeamentos_de_itens_do_edital mapa
                      ON mapa.item_do_edital_id = i.identificador
                     AND mapa.confirmado = TRUE
                    JOIN topicos_da_materia topico_mapeado
                      ON topico_mapeado.identificador = mapa.topico_da_materia_id
                     AND topico_mapeado.materia_id = mp.materia_id
                ), fatos AS (
                    SELECT r.topico_id, r.data_hora, r.tipo_de_estudo,
                           e.identificador AS evidencia_id,
                           e.quantidade_de_questoes, e.quantidade_de_acertos,
                           e.nivel_de_recordacao, e.dificuldade_percebida
                    FROM registros_de_estudo r
                    JOIN evidencias_de_aprendizagem e
                      ON e.registro_de_estudo_id = r.identificador
                    JOIN topicos_da_materia t ON t.identificador = r.topico_id
                    JOIN materias m ON m.identificador = t.materia_id
                    WHERE m.usuario_id = ? AND r.situacao = 'ATIVO'
                      AND (r.data_hora AT TIME ZONE 'America/Sao_Paulo')::date <= ?
                )
                SELECT t.identificador AS topico_id, t.nome AS topico_nome,
                       m.identificador AS materia_id, m.nome AS materia_nome,
                       (te.topico_id IS NOT NULL) AS exigido,
                       COUNT(f.evidencia_id) AS evidencias,
                       COALESCE(SUM(f.quantidade_de_questoes), 0) AS questoes,
                       COALESCE(SUM(f.quantidade_de_acertos), 0) AS acertos,
                       COALESCE(SUM(f.quantidade_de_questoes - f.quantidade_de_acertos), 0) AS erros,
                       COALESCE(SUM(f.quantidade_de_questoes) FILTER (WHERE
                           (f.data_hora AT TIME ZONE 'America/Sao_Paulo')::date BETWEEN ? AND ?), 0) AS questoes_recentes,
                       COALESCE(SUM(f.quantidade_de_acertos) FILTER (WHERE
                           (f.data_hora AT TIME ZONE 'America/Sao_Paulo')::date BETWEEN ? AND ?), 0) AS acertos_recentes,
                       COALESCE(SUM(f.quantidade_de_questoes - f.quantidade_de_acertos) FILTER (WHERE
                           (f.data_hora AT TIME ZONE 'America/Sao_Paulo')::date BETWEEN ? AND ?), 0) AS erros_recentes,
                       (ARRAY_AGG(f.nivel_de_recordacao
                           ORDER BY f.data_hora DESC, f.evidencia_id DESC)
                           FILTER (WHERE f.nivel_de_recordacao IS NOT NULL))[1] AS ultima_recordacao,
                       AVG(f.nivel_de_recordacao) FILTER (WHERE f.nivel_de_recordacao IS NOT NULL AND
                           (f.data_hora AT TIME ZONE 'America/Sao_Paulo')::date BETWEEN ? AND ?) AS media_recordacao,
                       (ARRAY_AGG(f.dificuldade_percebida
                           ORDER BY f.data_hora DESC, f.evidencia_id DESC)
                           FILTER (WHERE f.dificuldade_percebida IS NOT NULL))[1] AS ultima_dificuldade,
                       AVG(f.dificuldade_percebida) FILTER (WHERE f.dificuldade_percebida IS NOT NULL AND
                           (f.data_hora AT TIME ZONE 'America/Sao_Paulo')::date BETWEEN ? AND ?) AS media_dificuldade,
                       (ARRAY_AGG(f.nivel_de_recordacao
                           ORDER BY f.data_hora DESC, f.evidencia_id DESC)
                           FILTER (WHERE f.tipo_de_estudo = 'REVISAO'
                             AND f.nivel_de_recordacao IS NOT NULL))[1] AS ultima_revisao,
                       MAX(f.data_hora) AS ultima_evidencia
                FROM topicos_da_materia t
                JOIN materias m ON m.identificador = t.materia_id
                LEFT JOIN topicos_exigidos te ON te.topico_id = t.identificador
                LEFT JOIN fatos f ON f.topico_id = t.identificador
                WHERE m.usuario_id = ? AND m.arquivada = FALSE AND t.arquivado = FALSE
                  AND (CAST(? AS UUID) IS NULL OR m.identificador = CAST(? AS UUID))
                  AND (NOT ? OR te.topico_id IS NOT NULL)
                GROUP BY t.identificador, t.nome, t.ordem,
                         m.identificador, m.nome, te.topico_id
                ORDER BY m.nome_normalizado, t.ordem, t.nome_normalizado, t.identificador
                """, (resultado, linha) -> mapear(resultado,
                        repetidos.getOrDefault(
                                resultado.getObject("topico_id", UUID.class), List.of())),
                usuario, usuario, referencia,
                inicio, referencia, inicio, referencia, inicio, referencia,
                inicio, referencia, inicio, referencia,
                usuario, materia, materia, somenteExigidos);
    }

    private DiagnosticoDeTopico mapear(ResultSet r, List<PadraoRepetido> repetidos)
            throws SQLException {
        long questoesRecentes = r.getLong("questoes_recentes");
        long acertosRecentes = r.getLong("acertos_recentes");
        Integer ultimaRevisao = inteiro(r, "ultima_revisao");
        return new DiagnosticoDeTopico(
                r.getObject("topico_id", UUID.class), r.getString("topico_nome"),
                r.getObject("materia_id", UUID.class), r.getString("materia_nome"),
                r.getBoolean("exigido"), r.getLong("evidencias"),
                new TotaisDeQuestoes(r.getLong("questoes"), r.getLong("acertos"),
                        r.getLong("erros")),
                new TotaisDeQuestoes(questoesRecentes, acertosRecentes,
                        r.getLong("erros_recentes")),
                questoesRecentes == 0 ? null : BigDecimal.valueOf(acertosRecentes)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(questoesRecentes), 2, RoundingMode.HALF_UP),
                inteiro(r, "ultima_recordacao"), decimal(r, "media_recordacao"),
                inteiro(r, "ultima_dificuldade"), decimal(r, "media_dificuldade"),
                ultimaRevisao == null ? null : ResultadoDaRevisao.classificar(ultimaRevisao),
                r.getObject("ultima_evidencia", OffsetDateTime.class), repetidos);
    }

    private Map<UUID, List<PadraoRepetido>> padroesRepetidos(UUID usuario,
            LocalDate referencia, UUID materia, boolean somenteExigidos) {
        var mapa = new HashMap<UUID, List<PadraoRepetido>>();
        banco.query("""
                WITH contexto_oficial AS (
                    SELECT cargo.identificador AS cargo_id,
                           ed.identificador AS edital_id
                    FROM concursos c
                    JOIN cargos_do_concurso cargo
                      ON cargo.concurso_id = c.identificador
                     AND cargo.selecionado = TRUE
                    JOIN editais ed
                      ON ed.concurso_id = c.identificador
                     AND ed.principal = TRUE
                    WHERE c.usuario_id = ? AND c.ativo = TRUE
                ), topicos_exigidos AS (
                    SELECT DISTINCT topico_mapeado.identificador AS topico_id
                    FROM contexto_oficial contexto
                    JOIN itens_do_edital i ON i.edital_id = contexto.edital_id
                    JOIN materias_da_prova mp
                      ON mp.identificador = i.materia_da_prova_id
                    JOIN grupos_de_conteudo g
                      ON g.identificador = mp.grupo_de_conteudo_id
                    JOIN provas p
                      ON p.identificador = g.prova_id
                     AND p.cargo_id = contexto.cargo_id
                    JOIN mapeamentos_de_itens_do_edital mapa
                      ON mapa.item_do_edital_id = i.identificador
                     AND mapa.confirmado = TRUE
                    JOIN topicos_da_materia topico_mapeado
                      ON topico_mapeado.identificador = mapa.topico_da_materia_id
                     AND topico_mapeado.materia_id = mp.materia_id
                )
                SELECT p.topico_id, p.identificador, p.descricao,
                       COUNT(DISTINCT e.identificador) AS evidencias,
                       SUM(o.quantidade_de_ocorrencias) AS ocorrencias
                FROM padroes_de_erro p
                JOIN ocorrencias_de_padrao_de_erro o ON o.padrao_de_erro_id = p.identificador
                JOIN evidencias_de_aprendizagem e ON e.identificador = o.evidencia_id
                JOIN registros_de_estudo r ON r.identificador = e.registro_de_estudo_id
                JOIN topicos_da_materia t ON t.identificador = p.topico_id
                LEFT JOIN topicos_exigidos te ON te.topico_id = p.topico_id
                WHERE p.usuario_id = ? AND r.situacao = 'ATIVO'
                  AND r.topico_id = p.topico_id
                  AND (r.data_hora AT TIME ZONE 'America/Sao_Paulo')::date <= ?
                  AND (CAST(? AS UUID) IS NULL OR t.materia_id = CAST(? AS UUID))
                  AND (NOT ? OR te.topico_id IS NOT NULL)
                GROUP BY p.topico_id, p.identificador, p.descricao
                HAVING COUNT(DISTINCT e.identificador) >= 2
                ORDER BY p.topico_id, COUNT(DISTINCT e.identificador) DESC,
                         SUM(o.quantidade_de_ocorrencias) DESC, p.descricao
                """, resultado -> {
                    UUID topico = resultado.getObject("topico_id", UUID.class);
                    mapa.computeIfAbsent(topico, chave -> new ArrayList<>()).add(
                            new PadraoRepetido(
                                    resultado.getObject("identificador", UUID.class),
                                    resultado.getString("descricao"),
                                    resultado.getLong("evidencias"),
                                    resultado.getLong("ocorrencias")));
                }, usuario, usuario, referencia, materia, materia, somenteExigidos);
        mapa.replaceAll((topico, valores) -> List.copyOf(valores));
        return mapa;
    }

    private Integer inteiro(ResultSet resultado, String coluna) throws SQLException {
        int valor = resultado.getInt(coluna);
        return resultado.wasNull() ? null : valor;
    }

    private BigDecimal decimal(ResultSet resultado, String coluna) throws SQLException {
        BigDecimal valor = resultado.getBigDecimal(coluna);
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
    }
}
