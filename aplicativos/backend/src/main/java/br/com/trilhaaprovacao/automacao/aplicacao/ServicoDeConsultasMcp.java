package br.com.trilhaaprovacao.automacao.aplicacao;

import br.com.trilhaaprovacao.automacao.aplicacao.ResultadoDaConsultaMcp.Aviso;
import br.com.trilhaaprovacao.automacao.dominio.OperacaoAssistida;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.dashboard.aplicacao.ConsultaDoDashboard;
import br.com.trilhaaprovacao.dashboard.aplicacao.ResultadoDoDashboard;
import br.com.trilhaaprovacao.planejamento.aplicacao.ConsultaDoPlanejamentoDeHoje;
import br.com.trilhaaprovacao.planejamento.aplicacao.ResultadoDoPlanejamentoDeHoje;
import br.com.trilhaaprovacao.priorizacao.aplicacao.ConsultaDePriorizacaoDeTopicos;
import br.com.trilhaaprovacao.priorizacao.aplicacao.ResultadoDaPriorizacaoDeTopicos;
import br.com.trilhaaprovacao.revisoes.aplicacao.ConsultaDeRevisoesEspacadas;
import br.com.trilhaaprovacao.revisoes.aplicacao.ResultadoDaAgendaDeRevisoes;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoDeConsultasMcp {
    private static final ZoneId FUSO_HORARIO =
            ZoneId.of("America/Sao_Paulo");
    private static final String VERSAO_DO_CONTRATO = "1";

    private final ConsultaDoPlanejamentoDeHoje planejamento;
    private final ConsultaDeRevisoesEspacadas revisoes;
    private final ConsultaDePriorizacaoDeTopicos prioridades;
    private final ConsultaDoDashboard dashboard;
    private final ServicoDeOperacoesAssistidas operacoes;
    private final JdbcTemplate banco;

    public ServicoDeConsultasMcp(
            ConsultaDoPlanejamentoDeHoje planejamento,
            ConsultaDeRevisoesEspacadas revisoes,
            ConsultaDePriorizacaoDeTopicos prioridades,
            ConsultaDoDashboard dashboard,
            ServicoDeOperacoesAssistidas operacoes,
            JdbcTemplate banco) {
        this.planejamento = planejamento;
        this.revisoes = revisoes;
        this.prioridades = prioridades;
        this.dashboard = dashboard;
        this.operacoes = operacoes;
        this.banco = banco;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResultadoDaConsultaMcp obterAgendaDeHoje(UUID usuario,
            UUID correlacao) {
        LocalDate hoje = hoje();
        ResultadoDoPlanejamentoDeHoje plano = planejamento.consultar(usuario, hoje);
        List<Aviso> avisos = new ArrayList<>();
        ResultadoDaAgendaDeRevisoes agendaDeRevisoes = null;
        try {
            agendaDeRevisoes = revisoes.consultar(usuario, hoje, hoje);
        } catch (RegraDeDominio excecao) {
            avisos.add(new Aviso(excecao.codigo(), excecao.getMessage()));
        }
        Map<String, Object> dados = mapa();
        dados.put("fusoHorario", FUSO_HORARIO.getId());
        dados.put("data", hoje);
        dados.put("planejamento", plano);
        dados.put("execucaoEmAndamento", execucaoEmAndamento(usuario));
        dados.put("revisoes", agendaDeRevisoes == null
                ? List.of() : agendaDeRevisoes.revisoes());
        return resposta(correlacao, dados, avisos);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResultadoDaConsultaMcp obterRevisoesDevidas(UUID usuario,
            LocalDate ate, UUID correlacao) {
        LocalDate hoje = hoje();
        LocalDate limite = ate == null ? hoje.plusDays(30) : ate;
        if (limite.isBefore(hoje) || limite.isAfter(hoje.plusDays(90))) {
            throw new RegraDeDominio("PERIODO_DE_REVISOES_INVALIDO",
                    "A data final deve ficar entre hoje e os proximos 90 dias.");
        }
        ResultadoDaAgendaDeRevisoes resultado = revisoes.consultar(
                usuario, hoje, limite);
        Map<String, Object> dados = mapa();
        dados.put("fusoHorario", FUSO_HORARIO.getId());
        dados.put("agenda", resultado);
        return resposta(correlacao, dados, List.of());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResultadoDaConsultaMcp obterPrioridades(UUID usuario,
            UUID materia, UUID correlacao) {
        ResultadoDaPriorizacaoDeTopicos resultado = prioridades.consultar(
                usuario, hoje(), materia);
        Map<String, Object> dados = mapa();
        dados.put("priorizacao", resultado);
        return resposta(correlacao, dados, List.of());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResultadoDaConsultaMcp obterProgresso(UUID usuario,
            UUID correlacao) {
        ResultadoDoDashboard resultado = dashboard.consultar(usuario);
        List<Aviso> avisos = new ArrayList<>();
        Object resumoDaPriorizacao = null;
        try {
            resumoDaPriorizacao = prioridades.consultar(
                    usuario, hoje(), null).resumo();
        } catch (RegraDeDominio excecao) {
            avisos.add(new Aviso(excecao.codigo(), excecao.getMessage()));
        }
        Map<String, Object> dados = mapa();
        dados.put("fusoHorario", FUSO_HORARIO.getId());
        dados.put("dataDeReferencia", hoje());
        dados.put("progresso", resultado);
        dados.put("resumoDaPriorizacao", resumoDaPriorizacao);
        return resposta(correlacao, dados, avisos);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResultadoDaConsultaMcp obterHistorico(UUID usuario,
            int quantidadeDeDias, int limite, UUID correlacao) {
        if (quantidadeDeDias < 1 || quantidadeDeDias > 90) {
            throw new RegraDeDominio("PERIODO_DO_HISTORICO_INVALIDO",
                    "A quantidade de dias deve ficar entre 1 e 90.");
        }
        if (limite < 1 || limite > 100) {
            throw new RegraDeDominio("LIMITE_DO_HISTORICO_INVALIDO",
                    "O limite deve ficar entre 1 e 100.");
        }
        LocalDate fim = hoje();
        LocalDate inicio = fim.minusDays(quantidadeDeDias - 1L);
        OffsetDateTime inicioDoPeriodo = inicio.atStartOfDay(FUSO_HORARIO)
                .toOffsetDateTime();
        OffsetDateTime fimExclusivo = fim.plusDays(1)
                .atStartOfDay(FUSO_HORARIO).toOffsetDateTime();
        Map<String, Object> dados = mapa();
        dados.put("fusoHorario", FUSO_HORARIO.getId());
        dados.put("inicio", inicio);
        dados.put("fim", fim);
        dados.put("totais", totaisDoHistorico(
                usuario, inicioDoPeriodo, fimExclusivo));
        dados.put("estudos", estudosRecentes(usuario,
                inicioDoPeriodo, fimExclusivo, limite));
        dados.put("execucoesEmAndamento", execucoesEmAndamento(usuario));
        return resposta(correlacao, dados, List.of());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResultadoDaConsultaMcp obterEstruturaDoConcurso(UUID usuario,
            UUID concurso, UUID correlacao) {
        UUID identificador = concurso == null
                ? concursoAtivo(usuario) : concursoDoUsuario(usuario, concurso);
        Map<String, Object> dados = mapa();
        dados.put("concurso", estruturaDoConcurso(usuario, identificador));
        return resposta(correlacao, dados, List.of());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResultadoDaConsultaMcp explicarBloco(UUID usuario, UUID bloco,
            UUID correlacao) {
        Map<String, Object> detalhe = detalheDoBloco(usuario, bloco);
        UUID materia = (UUID) detalhe.get("identificadorDaMateria");
        UUID topico = (UUID) detalhe.get("identificadorDoTopico");
        List<Aviso> avisos = new ArrayList<>();
        Object priorizacaoAtual = null;
        Object revisaoAtual = null;
        if (topico != null && materia != null) {
            try {
                priorizacaoAtual = localizarPrioridade(
                        prioridades.consultar(usuario, hoje(), materia), topico);
            } catch (RegraDeDominio excecao) {
                avisos.add(new Aviso(excecao.codigo(), excecao.getMessage()));
            }
            try {
                revisaoAtual = revisoes.consultar(
                                usuario, hoje(), hoje().plusDays(90)).revisoes()
                        .stream().filter(item -> item.identificadorDoTopico()
                                .equals(topico)).findFirst().orElse(null);
            } catch (RegraDeDominio excecao) {
                avisos.add(new Aviso(excecao.codigo(), excecao.getMessage()));
            }
        } else {
            avisos.add(new Aviso("PRIORIZACAO_NAO_APLICAVEL",
                    "O bloco nao possui topico oficial associado."));
        }
        detalhe.put("priorizacaoAtual", priorizacaoAtual);
        detalhe.put("revisaoAtual", revisaoAtual);
        return resposta(correlacao, detalhe, avisos);
    }

    @Transactional(readOnly = true)
    public ResultadoDaConsultaMcp consultarOperacao(UUID usuario,
            UUID operacao, UUID correlacao) {
        OperacaoAssistida encontrada = operacoes.obter(usuario, operacao);
        Map<String, Object> dados = mapa();
        dados.put("identificador", encontrada.identificador());
        dados.put("tipo", encontrada.tipo());
        dados.put("estado", encontrada.estado());
        dados.put("resumo", encontrada.resumo());
        dados.put("expiraEm", encontrada.expiraEm());
        dados.put("resultado", encontrada.resultado());
        dados.put("criadoEm", encontrada.criadoEm());
        dados.put("atualizadoEm", encontrada.atualizadoEm());
        return resposta(correlacao, dados, List.of());
    }

    private Map<String, Object> execucaoEmAndamento(UUID usuario) {
        return banco.query("""
                SELECT e.identificador, e.bloco_id, e.iniciada_em,
                       b.titulo, b.tipo_de_atividade, b.data,
                       m.nome AS materia, t.nome AS topico
                FROM execucoes_de_bloco e
                JOIN blocos_de_estudo b ON b.identificador = e.bloco_id
                JOIN planos_semanais p ON p.identificador = b.plano_id
                LEFT JOIN materias m ON m.identificador = b.materia_id
                LEFT JOIN topicos_da_materia t ON t.identificador = b.topico_id
                WHERE e.usuario_id = ? AND p.usuario_id = ?
                  AND e.encerrada_em IS NULL
                ORDER BY e.iniciada_em DESC, e.identificador
                LIMIT 1
                """, resultado -> resultado.next()
                        ? linhaDaExecucao(resultado) : null, usuario, usuario);
    }

    private List<Map<String, Object>> execucoesEmAndamento(UUID usuario) {
        return banco.query("""
                SELECT e.identificador, e.bloco_id, e.iniciada_em,
                       b.titulo, b.tipo_de_atividade, b.data,
                       m.nome AS materia, t.nome AS topico
                FROM execucoes_de_bloco e
                JOIN blocos_de_estudo b ON b.identificador = e.bloco_id
                JOIN planos_semanais p ON p.identificador = b.plano_id
                LEFT JOIN materias m ON m.identificador = b.materia_id
                LEFT JOIN topicos_da_materia t ON t.identificador = b.topico_id
                WHERE e.usuario_id = ? AND p.usuario_id = ?
                  AND e.encerrada_em IS NULL
                ORDER BY e.iniciada_em DESC, e.identificador
                """, (resultado, linha) -> linhaDaExecucao(resultado),
                usuario, usuario);
    }

    private Map<String, Object> linhaDaExecucao(ResultSet resultado)
            throws SQLException {
        Map<String, Object> item = mapa();
        item.put("identificador", uuid(resultado, "identificador"));
        item.put("identificadorDoBloco", uuid(resultado, "bloco_id"));
        item.put("iniciadaEm", resultado.getObject(
                "iniciada_em", OffsetDateTime.class));
        item.put("titulo", resultado.getString("titulo"));
        item.put("tipoDeAtividade", resultado.getString("tipo_de_atividade"));
        item.put("data", resultado.getObject("data", LocalDate.class));
        item.put("materia", resultado.getString("materia"));
        item.put("topico", resultado.getString("topico"));
        return item;
    }

    private Map<String, Object> totaisDoHistorico(UUID usuario,
            OffsetDateTime inicio, OffsetDateTime fim) {
        return banco.queryForObject("""
                SELECT COUNT(*) AS quantidade_de_estudos,
                       COALESCE(SUM(r.duracao_em_minutos), 0) AS minutos,
                       COALESCE(SUM(e.quantidade_de_questoes), 0) AS questoes,
                       COALESCE(SUM(e.quantidade_de_acertos), 0) AS acertos
                FROM registros_de_estudo r
                JOIN topicos_da_materia t ON t.identificador = r.topico_id
                JOIN materias m ON m.identificador = t.materia_id
                LEFT JOIN evidencias_de_aprendizagem e
                  ON e.registro_de_estudo_id = r.identificador
                WHERE m.usuario_id = ? AND r.situacao = 'ATIVO'
                  AND r.data_hora >= ? AND r.data_hora < ?
                """, (resultado, linha) -> {
                    Map<String, Object> totais = mapa();
                    long questoes = resultado.getLong("questoes");
                    long acertos = resultado.getLong("acertos");
                    totais.put("quantidadeDeEstudos",
                            resultado.getLong("quantidade_de_estudos"));
                    totais.put("minutos", resultado.getLong("minutos"));
                    totais.put("questoes", questoes);
                    totais.put("acertos", acertos);
                    totais.put("erros", Math.max(0, questoes - acertos));
                    return totais;
                }, usuario, inicio, fim);
    }

    private List<Map<String, Object>> estudosRecentes(UUID usuario,
            OffsetDateTime inicio, OffsetDateTime fim, int limite) {
        return banco.query("""
                SELECT r.identificador, r.data_hora, r.duracao_em_minutos,
                       r.tipo_de_estudo, r.observacao,
                       m.identificador AS materia_id, m.nome AS materia,
                       t.identificador AS topico_id, t.nome AS topico,
                       material.titulo AS material,
                       e.quantidade_de_questoes, e.quantidade_de_acertos,
                       e.nivel_de_recordacao, e.dificuldade_percebida
                FROM registros_de_estudo r
                JOIN topicos_da_materia t ON t.identificador = r.topico_id
                JOIN materias m ON m.identificador = t.materia_id
                LEFT JOIN materiais_de_estudo material
                  ON material.identificador = r.material_id
                LEFT JOIN evidencias_de_aprendizagem e
                  ON e.registro_de_estudo_id = r.identificador
                WHERE m.usuario_id = ? AND r.situacao = 'ATIVO'
                  AND r.data_hora >= ? AND r.data_hora < ?
                ORDER BY r.data_hora DESC, r.criado_em DESC, r.identificador
                LIMIT ?
                """, (resultado, linha) -> {
                    Map<String, Object> item = mapa();
                    item.put("identificador", uuid(resultado, "identificador"));
                    item.put("dataHora", resultado.getObject(
                            "data_hora", OffsetDateTime.class));
                    item.put("duracaoEmMinutos",
                            resultado.getInt("duracao_em_minutos"));
                    item.put("tipoDeEstudo", resultado.getString("tipo_de_estudo"));
                    item.put("observacao", resultado.getString("observacao"));
                    item.put("identificadorDaMateria", uuid(resultado, "materia_id"));
                    item.put("materia", resultado.getString("materia"));
                    item.put("identificadorDoTopico", uuid(resultado, "topico_id"));
                    item.put("topico", resultado.getString("topico"));
                    item.put("material", resultado.getString("material"));
                    Integer questoes = inteiro(resultado, "quantidade_de_questoes");
                    Integer acertos = inteiro(resultado, "quantidade_de_acertos");
                    item.put("quantidadeDeQuestoes", questoes);
                    item.put("quantidadeDeAcertos", acertos);
                    item.put("quantidadeDeErros", questoes == null || acertos == null
                            ? null : Math.max(0, questoes - acertos));
                    item.put("nivelDeRecordacao",
                            inteiro(resultado, "nivel_de_recordacao"));
                    item.put("dificuldadePercebida",
                            inteiro(resultado, "dificuldade_percebida"));
                    return item;
                }, usuario, inicio, fim, limite);
    }

    private UUID concursoAtivo(UUID usuario) {
        return banco.query("""
                SELECT identificador FROM concursos
                WHERE usuario_id = ? AND ativo = TRUE
                """, resultado -> resultado.next()
                        ? uuid(resultado, "identificador") : null, usuario);
    }

    private UUID concursoDoUsuario(UUID usuario, UUID concurso) {
        UUID encontrado = banco.query("""
                SELECT identificador FROM concursos
                WHERE identificador = ? AND usuario_id = ?
                """, resultado -> resultado.next()
                        ? uuid(resultado, "identificador") : null,
                concurso, usuario);
        if (encontrado == null) {
            throw new RecursoNaoEncontrado("CONCURSO_NAO_ENCONTRADO",
                    "Concurso nao encontrado.");
        }
        return encontrado;
    }

    private Map<String, Object> estruturaDoConcurso(UUID usuario, UUID concurso) {
        if (concurso == null) {
            Map<String, Object> vazio = mapa();
            vazio.put("estado", "SEM_CONCURSO_ATIVO");
            return vazio;
        }
        List<Map<String, Object>> linhas = banco.query("""
                SELECT c.identificador AS concurso_id, c.nome AS concurso_nome,
                       c.orgao, c.banca, c.situacao, c.ativo,
                       e.identificador AS edital_id, e.titulo AS edital_titulo,
                       e.principal AS edital_principal,
                       cargo.identificador AS cargo_id, cargo.nome AS cargo_nome,
                       cargo.selecionado AS cargo_selecionado,
                       p.identificador AS prova_id, p.nome AS prova_nome,
                       p.tipo AS prova_tipo, p.data_hora_prevista,
                       g.identificador AS grupo_id, g.nome AS grupo_nome,
                       mp.identificador AS materia_da_prova_id,
                       m.identificador AS materia_id, m.nome AS materia_nome,
                       (SELECT COUNT(*) FROM itens_do_edital i
                         WHERE i.materia_da_prova_id = mp.identificador
                           AND i.edital_id = e.identificador) AS itens,
                       (SELECT COUNT(DISTINCT mapa.item_do_edital_id)
                          FROM itens_do_edital i
                          JOIN mapeamentos_de_itens_do_edital mapa
                            ON mapa.item_do_edital_id = i.identificador
                           AND mapa.confirmado = TRUE
                         WHERE i.materia_da_prova_id = mp.identificador
                           AND i.edital_id = e.identificador) AS mapeados
                FROM concursos c
                LEFT JOIN editais e ON e.concurso_id = c.identificador
                                  AND e.principal = TRUE
                LEFT JOIN cargos_do_concurso cargo
                  ON cargo.concurso_id = c.identificador
                LEFT JOIN provas p ON p.cargo_id = cargo.identificador
                LEFT JOIN grupos_de_conteudo g ON g.prova_id = p.identificador
                LEFT JOIN materias_da_prova mp
                  ON mp.grupo_de_conteudo_id = g.identificador
                LEFT JOIN materias m ON m.identificador = mp.materia_id
                WHERE c.identificador = ? AND c.usuario_id = ?
                ORDER BY cargo.ordem, cargo.identificador, p.ordem,
                         p.identificador, g.ordem, g.identificador,
                         mp.ordem, mp.identificador
                """, (resultado, linha) -> linhaDaEstrutura(resultado),
                concurso, usuario);
        if (linhas.isEmpty()) {
            throw new RecursoNaoEncontrado("CONCURSO_NAO_ENCONTRADO",
                    "Concurso nao encontrado.");
        }
        Map<String, Object> primeiro = linhas.getFirst();
        Map<String, Object> estrutura = mapa();
        estrutura.put("identificador", primeiro.get("identificadorDoConcurso"));
        estrutura.put("nome", primeiro.get("nomeDoConcurso"));
        estrutura.put("orgao", primeiro.get("orgao"));
        estrutura.put("banca", primeiro.get("banca"));
        estrutura.put("situacao", primeiro.get("situacao"));
        estrutura.put("ativo", primeiro.get("ativo"));
        estrutura.put("editalPrincipal", mapaDoEdital(primeiro));
        estrutura.put("cargos", agruparEstrutura(linhas));
        return estrutura;
    }

    private Map<String, Object> linhaDaEstrutura(ResultSet resultado)
            throws SQLException {
        Map<String, Object> linha = mapa();
        linha.put("identificadorDoConcurso", uuid(resultado, "concurso_id"));
        linha.put("nomeDoConcurso", resultado.getString("concurso_nome"));
        linha.put("orgao", resultado.getString("orgao"));
        linha.put("banca", resultado.getString("banca"));
        linha.put("situacao", resultado.getString("situacao"));
        linha.put("ativo", resultado.getBoolean("ativo"));
        linha.put("identificadorDoEdital", uuid(resultado, "edital_id"));
        linha.put("tituloDoEdital", resultado.getString("edital_titulo"));
        linha.put("editalPrincipal", resultado.getObject("edital_principal"));
        linha.put("identificadorDoCargo", uuid(resultado, "cargo_id"));
        linha.put("nomeDoCargo", resultado.getString("cargo_nome"));
        linha.put("cargoSelecionado", resultado.getObject("cargo_selecionado"));
        linha.put("identificadorDaProva", uuid(resultado, "prova_id"));
        linha.put("nomeDaProva", resultado.getString("prova_nome"));
        linha.put("tipoDaProva", resultado.getString("prova_tipo"));
        linha.put("dataHoraDaProva", resultado.getObject(
                "data_hora_prevista", OffsetDateTime.class));
        linha.put("identificadorDoGrupo", uuid(resultado, "grupo_id"));
        linha.put("nomeDoGrupo", resultado.getString("grupo_nome"));
        linha.put("identificadorDaMateriaDaProva",
                uuid(resultado, "materia_da_prova_id"));
        linha.put("identificadorDaMateria", uuid(resultado, "materia_id"));
        linha.put("nomeDaMateria", resultado.getString("materia_nome"));
        linha.put("itens", resultado.getLong("itens"));
        linha.put("itensMapeados", resultado.getLong("mapeados"));
        return linha;
    }

    private Map<String, Object> mapaDoEdital(Map<String, Object> linha) {
        if (linha.get("identificadorDoEdital") == null) {
            return null;
        }
        Map<String, Object> edital = mapa();
        edital.put("identificador", linha.get("identificadorDoEdital"));
        edital.put("titulo", linha.get("tituloDoEdital"));
        edital.put("principal", linha.get("editalPrincipal"));
        return edital;
    }

    private List<Map<String, Object>> agruparEstrutura(
            List<Map<String, Object>> linhas) {
        Map<UUID, Map<String, Object>> cargos = new LinkedHashMap<>();
        Map<UUID, Map<String, Object>> provas = new LinkedHashMap<>();
        Map<UUID, Map<String, Object>> grupos = new LinkedHashMap<>();
        for (Map<String, Object> linha : linhas) {
            UUID cargoId = (UUID) linha.get("identificadorDoCargo");
            if (cargoId == null) continue;
            Map<String, Object> cargo = cargos.computeIfAbsent(cargoId, id -> {
                Map<String, Object> item = mapa();
                item.put("identificador", id);
                item.put("nome", linha.get("nomeDoCargo"));
                item.put("selecionado", linha.get("cargoSelecionado"));
                item.put("provas", new ArrayList<Map<String, Object>>());
                return item;
            });
            UUID provaId = (UUID) linha.get("identificadorDaProva");
            if (provaId == null) continue;
            Map<String, Object> prova = provas.computeIfAbsent(provaId, id -> {
                Map<String, Object> item = mapa();
                item.put("identificador", id);
                item.put("nome", linha.get("nomeDaProva"));
                item.put("tipo", linha.get("tipoDaProva"));
                item.put("dataHoraPrevista", linha.get("dataHoraDaProva"));
                item.put("grupos", new ArrayList<Map<String, Object>>());
                lista(cargo, "provas").add(item);
                return item;
            });
            UUID grupoId = (UUID) linha.get("identificadorDoGrupo");
            if (grupoId == null) continue;
            Map<String, Object> grupo = grupos.computeIfAbsent(grupoId, id -> {
                Map<String, Object> item = mapa();
                item.put("identificador", id);
                item.put("nome", linha.get("nomeDoGrupo"));
                item.put("materias", new ArrayList<Map<String, Object>>());
                lista(prova, "grupos").add(item);
                return item;
            });
            if (linha.get("identificadorDaMateriaDaProva") != null) {
                Map<String, Object> materia = mapa();
                materia.put("identificadorDaVinculacao",
                        linha.get("identificadorDaMateriaDaProva"));
                materia.put("identificador", linha.get("identificadorDaMateria"));
                materia.put("nome", linha.get("nomeDaMateria"));
                materia.put("itens", linha.get("itens"));
                materia.put("itensMapeados", linha.get("itensMapeados"));
                lista(grupo, "materias").add(materia);
            }
        }
        return List.copyOf(cargos.values());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lista(Map<String, Object> mapa,
            String chave) {
        return (List<Map<String, Object>>) mapa.get(chave);
    }

    private Map<String, Object> detalheDoBloco(UUID usuario, UUID bloco) {
        Map<String, Object> detalhe = banco.query("""
                SELECT b.identificador, b.titulo, b.tipo_de_atividade,
                       b.data, b.duracao_prevista_em_minutos, b.ordem,
                       b.horario_previsto, b.observacao, b.origem,
                       b.justificativa_da_geracao,
                       b.justificativa_do_replanejamento, b.estado,
                       b.quantidade_de_reagendamentos,
                       b.materia_id, m.nome AS materia,
                       b.topico_id, t.nome AS topico,
                       d.minutos_disponiveis,
                       COALESCE(SUM(outro.duracao_prevista_em_minutos)
                         FILTER (WHERE outro.estado <> 'CANCELADO'), 0)
                         AS minutos_ocupados
                FROM blocos_de_estudo b
                JOIN planos_semanais p ON p.identificador = b.plano_id
                LEFT JOIN materias m ON m.identificador = b.materia_id
                LEFT JOIN topicos_da_materia t ON t.identificador = b.topico_id
                LEFT JOIN disponibilidades_do_dia d
                  ON d.plano_id = b.plano_id AND d.data = b.data
                LEFT JOIN blocos_de_estudo outro
                  ON outro.plano_id = b.plano_id AND outro.data = b.data
                WHERE b.identificador = ? AND p.usuario_id = ?
                GROUP BY b.identificador, m.nome, t.nome,
                         d.minutos_disponiveis
                """, resultado -> resultado.next()
                        ? linhaDoBloco(resultado) : null, bloco, usuario);
        if (detalhe == null) {
            throw new RecursoNaoEncontrado("BLOCO_DE_ESTUDO_NAO_ENCONTRADO",
                    "Bloco de estudo nao encontrado.");
        }
        return detalhe;
    }

    private Map<String, Object> linhaDoBloco(ResultSet resultado)
            throws SQLException {
        Map<String, Object> item = mapa();
        item.put("identificador", uuid(resultado, "identificador"));
        item.put("titulo", resultado.getString("titulo"));
        item.put("tipoDeAtividade", resultado.getString("tipo_de_atividade"));
        item.put("data", resultado.getObject("data", LocalDate.class));
        item.put("duracaoPrevistaEmMinutos",
                resultado.getInt("duracao_prevista_em_minutos"));
        item.put("ordem", resultado.getInt("ordem"));
        item.put("horarioPrevisto", resultado.getObject("horario_previsto"));
        item.put("observacao", resultado.getString("observacao"));
        item.put("origem", resultado.getString("origem"));
        item.put("justificativaDaGeracao",
                resultado.getString("justificativa_da_geracao"));
        item.put("justificativaDoReplanejamento",
                resultado.getString("justificativa_do_replanejamento"));
        item.put("estado", resultado.getString("estado"));
        item.put("quantidadeDeReagendamentos",
                resultado.getInt("quantidade_de_reagendamentos"));
        item.put("identificadorDaMateria", uuid(resultado, "materia_id"));
        item.put("materia", resultado.getString("materia"));
        item.put("identificadorDoTopico", uuid(resultado, "topico_id"));
        item.put("topico", resultado.getString("topico"));
        Integer capacidade = inteiro(resultado, "minutos_disponiveis");
        int ocupados = resultado.getInt("minutos_ocupados");
        item.put("minutosDisponiveis", capacidade);
        item.put("minutosOcupados", ocupados);
        item.put("minutosRestantes", capacidade == null
                ? null : Math.max(0, capacidade - ocupados));
        return item;
    }

    private Object localizarPrioridade(ResultadoDaPriorizacaoDeTopicos resultado,
            UUID topico) {
        return resultado.materias().stream().flatMap(
                        materia -> materia.topicos().stream())
                .filter(item -> item.identificador().equals(topico))
                .findFirst().orElse(null);
    }

    private ResultadoDaConsultaMcp resposta(UUID correlacao,
            Map<String, Object> dados, List<Aviso> avisos) {
        return new ResultadoDaConsultaMcp(VERSAO_DO_CONTRATO,
                correlacao == null ? UUID.randomUUID() : correlacao,
                OffsetDateTime.now(FUSO_HORARIO), dados, avisos);
    }

    private LocalDate hoje() {
        return LocalDate.now(FUSO_HORARIO);
    }

    private UUID uuid(ResultSet resultado, String coluna) throws SQLException {
        return resultado.getObject(coluna, UUID.class);
    }

    private Integer inteiro(ResultSet resultado, String coluna)
            throws SQLException {
        int valor = resultado.getInt(coluna);
        return resultado.wasNull() ? null : valor;
    }

    private Map<String, Object> mapa() {
        return new LinkedHashMap<>();
    }
}
