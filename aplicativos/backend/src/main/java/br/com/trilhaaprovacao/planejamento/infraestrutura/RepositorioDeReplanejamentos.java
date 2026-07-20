package br.com.trilhaaprovacao.planejamento.infraestrutura;

import br.com.trilhaaprovacao.planejamento.dominio.BlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.ReplanejadorDeterministicoDePlano.Proposta;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioDeReplanejamentos {
    private final JdbcTemplate banco;

    public RepositorioDeReplanejamentos(JdbcTemplate banco) {
        this.banco = banco;
    }

    public void capturarSnapshot(UUID plano) {
        banco.update("""
                INSERT INTO blocos_originais_dos_planos (
                    identificador, plano_id, bloco_id, materia_id, topico_id, titulo,
                    tipo_de_atividade, data, duracao_prevista_em_minutos, ordem,
                    horario_previsto, observacao, origem, justificativa_da_geracao, capturado_em
                )
                SELECT gen_random_uuid(), b.plano_id, b.identificador, b.materia_id,
                       b.topico_id, b.titulo, b.tipo_de_atividade, b.data,
                       b.duracao_prevista_em_minutos, b.ordem, b.horario_previsto,
                       b.observacao, b.origem, b.justificativa_da_geracao, CURRENT_TIMESTAMP
                FROM blocos_de_estudo b WHERE b.plano_id = ?
                ON CONFLICT (bloco_id) DO NOTHING
                """, plano);
    }

    public void bloquearEstadoDoPlano(UUID plano) {
        banco.queryForList("SELECT identificador FROM blocos_de_estudo "
                + "WHERE plano_id = ? ORDER BY identificador FOR UPDATE", UUID.class, plano);
        banco.queryForList("SELECT identificador FROM disponibilidades_do_dia "
                + "WHERE plano_id = ? ORDER BY identificador FOR UPDATE", UUID.class, plano);
        banco.queryForList("SELECT identificador FROM prioridades_de_materias_no_plano "
                + "WHERE plano_id = ? ORDER BY identificador FOR UPDATE", UUID.class, plano);
    }

    public Set<UUID> blocosJaTransferidos(UUID plano) {
        return new HashSet<>(banco.queryForList("""
                SELECT i.bloco_original_id
                FROM itens_de_replanejamento i
                JOIN replanejamentos r ON r.identificador = i.replanejamento_id
                WHERE r.plano_id = ?
                """, UUID.class, plano));
    }

    public UUID registrar(UUID plano, LocalDate referencia, List<Proposta> propostas,
            Map<UUID, List<BlocoDeEstudo>> criadosPorOrigem) {
        UUID replanejamento = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now();
        banco.update("INSERT INTO replanejamentos "
                        + "(identificador, plano_id, data_de_referencia, aplicado_em) "
                        + "VALUES (?, ?, ?, ?)",
                replanejamento, plano, Date.valueOf(referencia), Timestamp.from(agora.toInstant()));
        for (Proposta proposta : propostas) {
            List<BlocoDeEstudo> criados = criadosPorOrigem.getOrDefault(
                    proposta.pendencia().bloco().identificador(), List.of());
            if (criados.isEmpty()) continue;
            UUID item = UUID.randomUUID();
            banco.update("""
                    INSERT INTO itens_de_replanejamento (
                        identificador, replanejamento_id, bloco_original_id, decisao,
                        motivo, minutos_previstos, minutos_executados, minutos_pendentes,
                        quantidade_de_reagendamentos_anterior, limite_confirmado, justificativa
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, item, replanejamento, proposta.pendencia().bloco().identificador(),
                    proposta.decisao().name(), proposta.pendencia().motivo().name(),
                    proposta.pendencia().bloco().duracaoPrevistaEmMinutos(),
                    proposta.pendencia().minutosExecutados(),
                    proposta.pendencia().minutosPendentes(),
                    proposta.pendencia().bloco().quantidadeDeReagendamentos(),
                    proposta.exigeConfirmacao(), proposta.justificativa());
            for (int indice = 0; indice < criados.size(); indice++) {
                BlocoDeEstudo criado = criados.get(indice);
                banco.update("""
                        INSERT INTO fragmentos_de_replanejamento (
                            identificador, item_de_replanejamento_id, bloco_criado_id,
                            sequencia, data, duracao_em_minutos
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), item, criado.identificador(), indice + 1,
                        Date.valueOf(criado.data()), criado.duracaoPrevistaEmMinutos());
            }
        }
        return replanejamento;
    }

    public List<Snapshot> snapshots(UUID plano) {
        return banco.query("""
                SELECT bloco_id, materia_id, topico_id, titulo, tipo_de_atividade,
                       data, duracao_prevista_em_minutos, ordem, origem, capturado_em
                FROM blocos_originais_dos_planos WHERE plano_id = ?
                ORDER BY data, ordem, bloco_id
                """, (rs, linha) -> new Snapshot(
                rs.getObject("bloco_id", UUID.class),
                rs.getObject("materia_id", UUID.class),
                rs.getObject("topico_id", UUID.class), rs.getString("titulo"),
                rs.getString("tipo_de_atividade"), rs.getDate("data").toLocalDate(),
                rs.getInt("duracao_prevista_em_minutos"), rs.getInt("ordem"),
                rs.getString("origem"), rs.getObject("capturado_em", OffsetDateTime.class)), plano);
    }

    public List<Transferencia> transferencias(UUID plano) {
        return banco.query("""
                SELECT r.identificador replanejamento_id, r.data_de_referencia, r.aplicado_em,
                       i.bloco_original_id, i.decisao, i.motivo, i.minutos_pendentes,
                       f.bloco_criado_id, f.sequencia, f.data, f.duracao_em_minutos
                FROM replanejamentos r
                JOIN itens_de_replanejamento i ON i.replanejamento_id = r.identificador
                JOIN fragmentos_de_replanejamento f ON f.item_de_replanejamento_id = i.identificador
                WHERE r.plano_id = ?
                ORDER BY r.aplicado_em, i.bloco_original_id, f.sequencia
                """, (rs, linha) -> new Transferencia(
                rs.getObject("replanejamento_id", UUID.class),
                rs.getDate("data_de_referencia").toLocalDate(),
                rs.getObject("aplicado_em", OffsetDateTime.class),
                rs.getObject("bloco_original_id", UUID.class), rs.getString("decisao"),
                rs.getString("motivo"), rs.getInt("minutos_pendentes"),
                rs.getObject("bloco_criado_id", UUID.class), rs.getInt("sequencia"),
                rs.getDate("data").toLocalDate(), rs.getInt("duracao_em_minutos")), plano);
    }

    public record Snapshot(UUID identificadorDoBloco, UUID identificadorDaMateria,
            UUID identificadorDoTopico, String titulo, String tipoDeAtividade,
            LocalDate data, int duracaoPrevistaEmMinutos, int ordem, String origem,
            OffsetDateTime capturadoEm) { }

    public record Transferencia(UUID identificadorDoReplanejamento,
            LocalDate dataDeReferencia, OffsetDateTime aplicadoEm,
            UUID identificadorDoBlocoOriginal, String decisao, String motivo,
            int minutosPendentes, UUID identificadorDoBlocoCriado, int sequencia,
            LocalDate data, int duracaoEmMinutos) { }
}
