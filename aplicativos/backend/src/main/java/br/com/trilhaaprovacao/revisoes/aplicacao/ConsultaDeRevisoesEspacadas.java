package br.com.trilhaaprovacao.revisoes.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.concursos.aplicacao.ConsultaDoContextoDeConteudoExigido;
import br.com.trilhaaprovacao.concursos.aplicacao.ContextoDeConteudoExigido;
import br.com.trilhaaprovacao.revisoes.aplicacao.ResultadoDaAgendaDeRevisoes.BlocoAberto;
import br.com.trilhaaprovacao.revisoes.aplicacao.ResultadoDaAgendaDeRevisoes.Revisao;
import br.com.trilhaaprovacao.revisoes.dominio.CalculadorDeRevisaoEspacada;
import br.com.trilhaaprovacao.revisoes.dominio.EventoDeRevisaoEspacada;
import br.com.trilhaaprovacao.revisoes.dominio.RevisaoEspacadaCalculada;
import br.com.trilhaaprovacao.revisoes.dominio.SituacaoDaRevisaoEspacada;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaDeRevisoesEspacadas {
    private static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate banco;
    private final ConsultaDoContextoDeConteudoExigido contextos;

    public ConsultaDeRevisoesEspacadas(
            JdbcTemplate banco, ConsultaDoContextoDeConteudoExigido contextos) {
        this.banco = banco;
        this.contextos = contextos;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResultadoDaAgendaDeRevisoes consultar(
            UUID usuario, LocalDate dataDeReferencia, LocalDate ate) {
        if (usuario == null || dataDeReferencia == null || ate == null) {
            throw new IllegalArgumentException(
                    "Usuario, data de referencia e horizonte sao obrigatorios.");
        }
        if (ate.isBefore(dataDeReferencia)) {
            throw new RegraDeDominio("PERIODO_DE_REVISOES_INVALIDO",
                    "A data final deve ser igual ou posterior a data de referencia.");
        }

        ContextoDeConteudoExigido contexto = contextos.consultar(usuario)
                .filter(ContextoDeConteudoExigido::estaCompleto)
                .orElseThrow(() -> new RegraDeDominio(
                        "CONTEXTO_DE_REVISOES_INCOMPLETO",
                        "Ative um concurso, selecione um cargo e defina o edital principal."));

        Map<UUID, TopicoComEventos> topicos = consultarTopicosComEventos(
                usuario, contexto, dataDeReferencia);
        List<RevisaoComOrdem> calculadas = new ArrayList<>();
        for (TopicoComEventos topico : topicos.values()) {
            CalculadorDeRevisaoEspacada.calcular(topico.eventos()).ifPresent(calculo -> {
                if (!calculo.dataDevida().isAfter(ate)) {
                    calculadas.add(calcularResultado(topico, calculo, dataDeReferencia));
                }
            });
        }
        calculadas.sort(ordemDasRevisoes());
        return new ResultadoDaAgendaDeRevisoes(dataDeReferencia, ate,
                calculadas.stream().map(RevisaoComOrdem::revisao).toList());
    }

    private Map<UUID, TopicoComEventos> consultarTopicosComEventos(UUID usuario,
            ContextoDeConteudoExigido contexto, LocalDate dataDeReferencia) {
        OffsetDateTime limiteExclusivo = dataDeReferencia.plusDays(1)
                .atStartOfDay(FUSO_HORARIO).toOffsetDateTime();
        ResultSetExtractor<Map<UUID, TopicoComEventos>> extrator = this::extrairTopicos;
        return banco.query("""
                WITH topicos_oficiais AS (
                    SELECT t.identificador AS topico_id,
                           t.nome AS topico_nome,
                           t.nome_normalizado AS topico_nome_normalizado,
                           t.ordem AS topico_ordem,
                           m.identificador AS materia_id,
                           m.nome AS materia_nome,
                           m.nome_normalizado AS materia_nome_normalizado,
                           MIN(mp.ordem) AS materia_ordem,
                           MIN(i.ordem) AS item_ordem
                    FROM itens_do_edital i
                    JOIN materias_da_prova mp
                      ON mp.identificador = i.materia_da_prova_id
                    JOIN grupos_de_conteudo g
                      ON g.identificador = mp.grupo_de_conteudo_id
                    JOIN provas p ON p.identificador = g.prova_id
                    JOIN mapeamentos_de_itens_do_edital mapa
                      ON mapa.item_do_edital_id = i.identificador
                     AND mapa.confirmado = TRUE
                    JOIN topicos_da_materia t
                      ON t.identificador = mapa.topico_da_materia_id
                     AND t.materia_id = mp.materia_id
                    JOIN materias m ON m.identificador = t.materia_id
                    WHERE i.edital_id = ? AND p.cargo_id = ?
                      AND m.usuario_id = ?
                      AND m.arquivada = FALSE AND t.arquivado = FALSE
                    GROUP BY t.identificador, t.nome, t.nome_normalizado, t.ordem,
                             m.identificador, m.nome, m.nome_normalizado
                ), blocos_abertos AS (
                    SELECT DISTINCT ON (b.topico_id)
                           b.topico_id, b.identificador AS bloco_id,
                           b.plano_id, plano.data_inicial,
                           b.data AS bloco_data, b.estado AS bloco_estado
                    FROM blocos_de_estudo b
                    JOIN planos_semanais plano
                      ON plano.identificador = b.plano_id
                    WHERE plano.usuario_id = ?
                      AND plano.estado IN ('RASCUNHO', 'ATIVO')
                      AND b.tipo_de_atividade = 'REVISAO'
                      AND b.estado IN ('PLANEJADO', 'EM_ANDAMENTO')
                    ORDER BY b.topico_id,
                             CASE WHEN b.estado = 'EM_ANDAMENTO' THEN 0 ELSE 1 END,
                             CASE WHEN plano.estado = 'ATIVO' THEN 0 ELSE 1 END,
                             b.data, plano.data_inicial, b.ordem, b.identificador
                )
                SELECT topico.topico_id, topico.topico_nome,
                       topico.topico_nome_normalizado, topico.topico_ordem,
                       topico.materia_id, topico.materia_nome,
                       topico.materia_nome_normalizado, topico.materia_ordem,
                       topico.item_ordem,
                       evidencia.identificador AS evidencia_id,
                       registro.data_hora,
                       (registro.data_hora AT TIME ZONE 'America/Sao_Paulo')::date
                         AS data_local,
                       registro.tipo_de_estudo, evidencia.nivel_de_recordacao,
                       bloco.bloco_id, bloco.plano_id, bloco.data_inicial,
                       bloco.bloco_data, bloco.bloco_estado
                FROM topicos_oficiais topico
                JOIN registros_de_estudo registro
                  ON registro.topico_id = topico.topico_id
                 AND registro.situacao = 'ATIVO'
                 AND registro.data_hora < ?
                JOIN evidencias_de_aprendizagem evidencia
                  ON evidencia.registro_de_estudo_id = registro.identificador
                LEFT JOIN blocos_abertos bloco ON bloco.topico_id = topico.topico_id
                ORDER BY topico.materia_ordem, topico.materia_nome_normalizado,
                         topico.item_ordem, topico.topico_ordem,
                         topico.topico_nome_normalizado, topico.topico_id,
                         registro.data_hora, evidencia.identificador
                """, extrator,
                contexto.identificadorDoEdital(), contexto.identificadorDoCargo(),
                usuario, usuario, limiteExclusivo);
    }

    private Map<UUID, TopicoComEventos> extrairTopicos(ResultSet resultado)
            throws SQLException {
        Map<UUID, TopicoComEventos> topicos = new LinkedHashMap<>();
        while (resultado.next()) {
            UUID identificador = resultado.getObject("topico_id", UUID.class);
            TopicoComEventos topico = topicos.computeIfAbsent(identificador,
                    ignorado -> mapearTopico(resultado));
            topico.eventos().add(new EventoDeRevisaoEspacada(
                    resultado.getObject("evidencia_id", UUID.class),
                    resultado.getObject("data_hora", OffsetDateTime.class),
                    resultado.getObject("data_local", LocalDate.class),
                    "REVISAO".equals(resultado.getString("tipo_de_estudo")),
                    inteiro(resultado, "nivel_de_recordacao")));
        }
        return topicos;
    }

    private TopicoComEventos mapearTopico(ResultSet resultado) {
        try {
            return new TopicoComEventos(
                    resultado.getObject("topico_id", UUID.class),
                    resultado.getString("topico_nome"),
                    resultado.getString("topico_nome_normalizado"),
                    resultado.getInt("topico_ordem"),
                    resultado.getObject("materia_id", UUID.class),
                    resultado.getString("materia_nome"),
                    resultado.getString("materia_nome_normalizado"),
                    resultado.getInt("materia_ordem"),
                    resultado.getInt("item_ordem"),
                    mapearBloco(resultado), new ArrayList<>());
        } catch (SQLException excecao) {
            throw new IllegalStateException("Nao foi possivel ler o topico da revisao.", excecao);
        }
    }

    private BlocoAberto mapearBloco(ResultSet resultado) throws SQLException {
        UUID identificador = resultado.getObject("bloco_id", UUID.class);
        if (identificador == null) {
            return null;
        }
        return new BlocoAberto(identificador,
                resultado.getObject("plano_id", UUID.class),
                resultado.getObject("data_inicial", LocalDate.class),
                resultado.getObject("bloco_data", LocalDate.class),
                resultado.getString("bloco_estado"));
    }

    private RevisaoComOrdem calcularResultado(TopicoComEventos topico,
            RevisaoEspacadaCalculada calculo, LocalDate dataDeReferencia) {
        long diasEmAtraso = Math.max(0,
                ChronoUnit.DAYS.between(calculo.dataDevida(), dataDeReferencia));
        SituacaoDaRevisaoEspacada situacao = situacao(
                calculo.dataDevida(), dataDeReferencia, topico.blocoAberto());
        Revisao revisao = new Revisao(topico.identificador(), topico.nome(),
                topico.identificadorDaMateria(), topico.nomeDaMateria(),
                calculo.etapa(), calculo.intervaloEmDias(), calculo.dataDevida(),
                diasEmAtraso, calculo.ultimaRevisao(), calculo.ultimaRecordacao(),
                situacao, topico.blocoAberto());
        return new RevisaoComOrdem(revisao, topico.nomeNormalizado(),
                topico.ordem(), topico.nomeNormalizadoDaMateria(),
                topico.ordemDaMateria(), topico.ordemDoItem());
    }

    private SituacaoDaRevisaoEspacada situacao(LocalDate dataDevida,
            LocalDate dataDeReferencia, BlocoAberto blocoAberto) {
        if (blocoAberto != null) {
            return SituacaoDaRevisaoEspacada.JA_PLANEJADA;
        }
        if (dataDevida.isBefore(dataDeReferencia)) {
            return SituacaoDaRevisaoEspacada.VENCIDA;
        }
        return dataDevida.equals(dataDeReferencia)
                ? SituacaoDaRevisaoEspacada.DEVIDA_HOJE
                : SituacaoDaRevisaoEspacada.FUTURA;
    }

    private Comparator<RevisaoComOrdem> ordemDasRevisoes() {
        return Comparator.comparing(
                        (RevisaoComOrdem item) -> item.revisao().dataDevida())
                .thenComparingInt(item -> item.revisao().etapa())
                .thenComparing(item -> item.revisao().ultimaRecordacao(),
                        Comparator.nullsLast(Integer::compareTo))
                .thenComparingInt(RevisaoComOrdem::ordemDaMateria)
                .thenComparing(RevisaoComOrdem::nomeNormalizadoDaMateria)
                .thenComparingInt(RevisaoComOrdem::ordemDoItem)
                .thenComparingInt(RevisaoComOrdem::ordem)
                .thenComparing(RevisaoComOrdem::nomeNormalizado)
                .thenComparing(item -> item.revisao().identificadorDoTopico());
    }

    private Integer inteiro(ResultSet resultado, String coluna) throws SQLException {
        int valor = resultado.getInt(coluna);
        return resultado.wasNull() ? null : valor;
    }

    private record TopicoComEventos(
            UUID identificador,
            String nome,
            String nomeNormalizado,
            int ordem,
            UUID identificadorDaMateria,
            String nomeDaMateria,
            String nomeNormalizadoDaMateria,
            int ordemDaMateria,
            int ordemDoItem,
            BlocoAberto blocoAberto,
            List<EventoDeRevisaoEspacada> eventos) {
    }

    private record RevisaoComOrdem(
            Revisao revisao,
            String nomeNormalizado,
            int ordem,
            String nomeNormalizadoDaMateria,
            int ordemDaMateria,
            int ordemDoItem) {
    }
}
