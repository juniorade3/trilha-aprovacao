package br.com.trilhaaprovacao.dashboard.aplicacao;

import br.com.trilhaaprovacao.dashboard.aplicacao.ResultadoDoDashboard.AlertaDoDashboard;
import br.com.trilhaaprovacao.dashboard.aplicacao.ResultadoDoDashboard.AtividadeRecente;
import br.com.trilhaaprovacao.dashboard.aplicacao.ResultadoDoDashboard.ResumoDoConcursoAtivo;
import br.com.trilhaaprovacao.concursos.aplicacao.ConsultaDoContextoDeConteudoExigido;
import br.com.trilhaaprovacao.concursos.aplicacao.ContextoDeConteudoExigido;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaDoDashboard {
    private static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate banco;
    private final ConsultaDoContextoDeConteudoExigido contextos;

    public ConsultaDoDashboard(JdbcTemplate banco,
            ConsultaDoContextoDeConteudoExigido contextos) {
        this.banco = banco;
        this.contextos = contextos;
    }

    @Transactional(readOnly = true)
    public ResultadoDoDashboard consultar(UUID usuario) {
        Optional<ContextoDeConteudoExigido> contextoEncontrado = contextos.consultar(usuario);
        if (contextoEncontrado.isEmpty()) {
            return new ResultadoDoDashboard(null, null, null, 0, 0, 0, 0,
                    0, 0, List.of(), List.of());
        }

        ContextoDeConteudoExigido contexto = contextoEncontrado.get();
        int quantidadeDeProvasDoConcurso =
                contarProvasDoConcurso(contexto.identificadorDoConcurso());
        List<AlertaDoDashboard> alertas = new ArrayList<>();

        if (!contexto.possuiCargoSelecionado()) {
            alertas.add(alerta("SEM_CARGO_SELECIONADO", "Selecione um cargo",
                    "Escolha o cargo que orientará os conteúdos e as provas do painel."));
        }
        if (quantidadeDeProvasDoConcurso == 0) {
            alertas.add(alerta("CONCURSO_SEM_PROVA", "Cadastre uma prova",
                    "Inclua ao menos uma prova para acompanhar datas e conteúdos."));
        }

        ResumoDoConcursoAtivo resumo = new ResumoDoConcursoAtivo(
                contexto.identificadorDoConcurso(), contexto.nomeDoConcurso(),
                contexto.orgaoDoConcurso(), contexto.bancaDoConcurso(),
                contexto.situacaoDoConcurso(), contexto.identificadorDoCargo(),
                contexto.nomeDoCargo());

        if (!contexto.possuiCargoSelecionado()) {
            return new ResultadoDoDashboard(resumo, null, null, 0, 0, 0, 0,
                    0, 0, List.of(), List.copyOf(alertas));
        }

        if (!contexto.possuiEditalPrincipal()) {
            alertas.add(alerta("SEM_EDITAL_PRINCIPAL", "Defina o edital principal",
                    "Escolha o edital principal que orientará os conteúdos exigidos."));
        }

        UUID cargo = contexto.identificadorDoCargo();
        Optional<OffsetDateTime> proximaProva = encontrarProximaProva(cargo);
        LocalDate dataDaProximaProva = proximaProva
                .map(data -> data.atZoneSameInstant(FUSO_HORARIO).toLocalDate())
                .orElse(null);
        Long diasAteAProva = dataDaProximaProva == null
                ? null
                : java.time.temporal.ChronoUnit.DAYS.between(
                        LocalDate.now(FUSO_HORARIO), dataDaProximaProva);

        if (!contexto.possuiEditalPrincipal()) {
            return new ResultadoDoDashboard(resumo, dataDaProximaProva, diasAteAProva,
                    0, 0, 0, 0, 0, 0, List.of(), List.copyOf(alertas));
        }

        UUID edital = contexto.identificadorDoEdital();
        int quantidadeDeMaterias = contarMaterias(usuario, cargo);
        int quantidadeDeTopicosExigidos = contarTopicosExigidos(usuario, cargo, edital);
        int quantidadeDeTopicosComEstudo =
                contarTopicosComEstudo(usuario, cargo, edital);
        ContagemDeItens itens = contarItens(usuario, cargo, edital);
        int tempoNaSemana = somarTempoNaSemana(usuario, cargo, edital);
        List<AtividadeRecente> atividades =
                listarAtividadeRecente(usuario, cargo, edital);
        int gruposSemMateria = contarGruposSemMateria(cargo);
        int materiasSemTopico = contarMateriasSemTopico(usuario, cargo);

        if (gruposSemMateria > 0) {
            alertas.add(alerta("GRUPO_SEM_MATERIA", "Grupo sem matéria",
                    quantidade(gruposSemMateria, "grupo ainda não possui matéria vinculada.",
                            "grupos ainda não possuem matéria vinculada.")));
        }
        if (itens.semMapeamento() > 0) {
            alertas.add(alerta("ITEM_SEM_MAPEAMENTO", "Itens aguardando mapeamento",
                    quantidade(itens.semMapeamento(),
                            "item do edital ainda não aponta para um tópico pessoal.",
                            "itens do edital ainda não apontam para tópicos pessoais.")));
        }
        if (materiasSemTopico > 0) {
            alertas.add(alerta("MATERIA_SEM_TOPICO", "Matéria sem tópico",
                    quantidade(materiasSemTopico,
                            "matéria da prova ainda não possui tópico cadastrado.",
                            "matérias da prova ainda não possuem tópicos cadastrados.")));
        }
        if (proximaProva.isPresent() && quantidadeDeTopicosComEstudo == 0) {
            alertas.add(alerta("PROVA_PROXIMA_SEM_ESTUDOS",
                    "Próxima prova sem estudos",
                    "A próxima prova está marcada, mas nenhum tópico exigido possui estudo ativo."));
        }

        return new ResultadoDoDashboard(resumo, dataDaProximaProva, diasAteAProva,
                tempoNaSemana, quantidadeDeMaterias, quantidadeDeTopicosExigidos,
                quantidadeDeTopicosComEstudo, itens.mapeados(), itens.semMapeamento(),
                atividades, List.copyOf(alertas));
    }

    private int contarProvasDoConcurso(UUID concurso) {
        return numero("""
                SELECT COUNT(*)
                FROM provas p
                JOIN cargos_do_concurso c ON c.identificador = p.cargo_id
                WHERE c.concurso_id = ?
                """, concurso);
    }

    private Optional<OffsetDateTime> encontrarProximaProva(UUID cargo) {
        return banco.query("""
                        SELECT data_hora_prevista
                        FROM provas
                        WHERE cargo_id = ?
                          AND data_hora_prevista >= ?
                        ORDER BY data_hora_prevista
                        LIMIT 1
                        """,
                (resultado, linha) -> resultado.getObject(
                        "data_hora_prevista", OffsetDateTime.class),
                cargo, OffsetDateTime.now(FUSO_HORARIO)).stream().findFirst();
    }

    private int contarMaterias(UUID usuario, UUID cargo) {
        return numero("""
                SELECT COUNT(DISTINCT mp.materia_id)
                FROM materias_da_prova mp
                JOIN grupos_de_conteudo g ON g.identificador = mp.grupo_de_conteudo_id
                JOIN provas p ON p.identificador = g.prova_id
                JOIN materias m ON m.identificador = mp.materia_id
                WHERE p.cargo_id = ? AND m.usuario_id = ?
                  AND m.arquivada = FALSE
                """, cargo, usuario);
    }

    private int contarTopicosExigidos(UUID usuario, UUID cargo, UUID edital) {
        return numero("""
                SELECT COUNT(DISTINCT mapa.topico_da_materia_id)
                FROM mapeamentos_de_itens_do_edital mapa
                JOIN itens_do_edital i ON i.identificador = mapa.item_do_edital_id
                JOIN materias_da_prova mp ON mp.identificador = i.materia_da_prova_id
                JOIN grupos_de_conteudo g ON g.identificador = mp.grupo_de_conteudo_id
                JOIN provas p ON p.identificador = g.prova_id
                JOIN topicos_da_materia t
                  ON t.identificador = mapa.topico_da_materia_id
                 AND t.materia_id = mp.materia_id
                JOIN materias m ON m.identificador = t.materia_id
                WHERE p.cargo_id = ? AND i.edital_id = ?
                  AND mapa.confirmado = TRUE AND t.arquivado = FALSE
                  AND m.usuario_id = ? AND m.arquivada = FALSE
                """, cargo, edital, usuario);
    }

    private int contarTopicosComEstudo(UUID usuario, UUID cargo, UUID edital) {
        return numero("""
                SELECT COUNT(DISTINCT mapa.topico_da_materia_id)
                FROM mapeamentos_de_itens_do_edital mapa
                JOIN itens_do_edital i ON i.identificador = mapa.item_do_edital_id
                JOIN materias_da_prova mp ON mp.identificador = i.materia_da_prova_id
                JOIN grupos_de_conteudo g ON g.identificador = mp.grupo_de_conteudo_id
                JOIN provas p ON p.identificador = g.prova_id
                JOIN topicos_da_materia t
                  ON t.identificador = mapa.topico_da_materia_id
                 AND t.materia_id = mp.materia_id
                JOIN materias m ON m.identificador = t.materia_id
                WHERE p.cargo_id = ? AND i.edital_id = ?
                  AND mapa.confirmado = TRUE AND t.arquivado = FALSE
                  AND m.usuario_id = ? AND m.arquivada = FALSE
                  AND EXISTS (
                    SELECT 1 FROM registros_de_estudo r
                    WHERE r.topico_id = mapa.topico_da_materia_id
                      AND r.situacao = 'ATIVO'
                  )
                """, cargo, edital, usuario);
    }

    private ContagemDeItens contarItens(UUID usuario, UUID cargo, UUID edital) {
        return banco.queryForObject("""
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (
                         WHERE EXISTS (
                           SELECT 1 FROM mapeamentos_de_itens_do_edital mapa
                           JOIN topicos_da_materia t
                             ON t.identificador = mapa.topico_da_materia_id
                           JOIN materias m ON m.identificador = t.materia_id
                           WHERE mapa.item_do_edital_id = i.identificador
                             AND mapa.confirmado = TRUE
                             AND t.materia_id = mp.materia_id
                             AND t.arquivado = FALSE
                             AND m.usuario_id = ? AND m.arquivada = FALSE
                         )
                       ) AS mapeados
                FROM itens_do_edital i
                JOIN materias_da_prova mp ON mp.identificador = i.materia_da_prova_id
                JOIN grupos_de_conteudo g ON g.identificador = mp.grupo_de_conteudo_id
                JOIN provas p ON p.identificador = g.prova_id
                WHERE p.cargo_id = ? AND i.edital_id = ?
                """, (resultado, linha) -> {
                    int total = resultado.getInt("total");
                    int mapeados = resultado.getInt("mapeados");
                    return new ContagemDeItens(mapeados, total - mapeados);
                }, usuario, cargo, edital);
    }

    private int somarTempoNaSemana(UUID usuario, UUID cargo, UUID edital) {
        LocalDate inicioDaSemana = LocalDate.now(FUSO_HORARIO)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        OffsetDateTime inicio = inicioDaSemana.atStartOfDay(FUSO_HORARIO)
                .toOffsetDateTime();
        OffsetDateTime fim = inicioDaSemana.plusWeeks(1)
                .atStartOfDay(FUSO_HORARIO).toOffsetDateTime();
        return numero("""
                SELECT COALESCE(SUM(r.duracao_em_minutos), 0)
                FROM registros_de_estudo r
                JOIN topicos_da_materia topico_do_registro
                  ON topico_do_registro.identificador = r.topico_id
                JOIN materias materia_do_registro
                  ON materia_do_registro.identificador = topico_do_registro.materia_id
                 AND materia_do_registro.usuario_id = ?
                WHERE r.situacao = 'ATIVO'
                  AND r.data_hora >= ? AND r.data_hora < ?
                  AND EXISTS (
                    SELECT 1
                    FROM mapeamentos_de_itens_do_edital mapa
                    JOIN itens_do_edital i
                      ON i.identificador = mapa.item_do_edital_id
                    JOIN materias_da_prova mp
                      ON mp.identificador = i.materia_da_prova_id
                    JOIN grupos_de_conteudo g
                      ON g.identificador = mp.grupo_de_conteudo_id
                    JOIN provas p ON p.identificador = g.prova_id
                    JOIN topicos_da_materia t
                      ON t.identificador = mapa.topico_da_materia_id
                    JOIN materias m ON m.identificador = t.materia_id
                    WHERE mapa.topico_da_materia_id = r.topico_id
                      AND mapa.confirmado = TRUE
                      AND t.materia_id = mp.materia_id
                      AND t.arquivado = FALSE AND m.arquivada = FALSE
                      AND p.cargo_id = ?
                      AND i.edital_id = ?
                  )
                """, usuario, inicio, fim, cargo, edital);
    }

    private List<AtividadeRecente> listarAtividadeRecente(
            UUID usuario, UUID cargo, UUID edital) {
        return banco.query("""
                SELECT r.identificador, r.topico_id, t.nome AS nome_do_topico,
                       material.titulo AS titulo_do_material,
                       r.data_hora, r.duracao_em_minutos
                FROM registros_de_estudo r
                JOIN topicos_da_materia t ON t.identificador = r.topico_id
                JOIN materias materia_do_registro
                  ON materia_do_registro.identificador = t.materia_id
                 AND materia_do_registro.usuario_id = ?
                LEFT JOIN materiais_de_estudo material
                  ON material.identificador = r.material_id
                 AND material.usuario_id = ?
                WHERE r.situacao = 'ATIVO'
                  AND t.arquivado = FALSE
                  AND EXISTS (
                    SELECT 1
                    FROM mapeamentos_de_itens_do_edital mapa
                    JOIN itens_do_edital i
                      ON i.identificador = mapa.item_do_edital_id
                    JOIN materias_da_prova mp
                      ON mp.identificador = i.materia_da_prova_id
                    JOIN grupos_de_conteudo g
                      ON g.identificador = mp.grupo_de_conteudo_id
                    JOIN provas p ON p.identificador = g.prova_id
                    JOIN materias m ON m.identificador = t.materia_id
                    WHERE mapa.topico_da_materia_id = r.topico_id
                      AND mapa.confirmado = TRUE
                      AND t.materia_id = mp.materia_id
                      AND m.arquivada = FALSE
                      AND p.cargo_id = ?
                      AND i.edital_id = ?
                  )
                ORDER BY r.data_hora DESC, r.criado_em DESC
                LIMIT 6
                """, (resultado, linha) -> new AtividadeRecente(
                    resultado.getObject("identificador", UUID.class),
                    resultado.getObject("topico_id", UUID.class),
                    resultado.getString("nome_do_topico"),
                    resultado.getString("titulo_do_material"),
                    resultado.getObject("data_hora", OffsetDateTime.class),
                    resultado.getInt("duracao_em_minutos")),
                usuario, usuario, cargo, edital);
    }

    private int contarGruposSemMateria(UUID cargo) {
        return numero("""
                SELECT COUNT(*)
                FROM grupos_de_conteudo g
                JOIN provas p ON p.identificador = g.prova_id
                WHERE p.cargo_id = ?
                  AND NOT EXISTS (
                    SELECT 1 FROM materias_da_prova mp
                    WHERE mp.grupo_de_conteudo_id = g.identificador
                  )
                """, cargo);
    }

    private int contarMateriasSemTopico(UUID usuario, UUID cargo) {
        return numero("""
                SELECT COUNT(DISTINCT mp.materia_id)
                FROM materias_da_prova mp
                JOIN grupos_de_conteudo g ON g.identificador = mp.grupo_de_conteudo_id
                JOIN provas p ON p.identificador = g.prova_id
                JOIN materias m ON m.identificador = mp.materia_id
                WHERE p.cargo_id = ? AND m.usuario_id = ?
                  AND m.arquivada = FALSE
                  AND NOT EXISTS (
                    SELECT 1 FROM topicos_da_materia t
                    WHERE t.materia_id = mp.materia_id AND t.arquivado = FALSE
                  )
                """, cargo, usuario);
    }

    private int numero(String consulta, Object... parametros) {
        Number resultado = banco.queryForObject(consulta, Number.class, parametros);
        return resultado == null ? 0 : resultado.intValue();
    }

    private AlertaDoDashboard alerta(String codigo, String titulo, String mensagem) {
        return new AlertaDoDashboard(codigo, titulo, mensagem, "ATENCAO");
    }

    private String quantidade(int valor, String singular, String plural) {
        return valor + " " + (valor == 1 ? singular : plural);
    }

    private record ContagemDeItens(int mapeados, int semMapeamento) {
    }
}
