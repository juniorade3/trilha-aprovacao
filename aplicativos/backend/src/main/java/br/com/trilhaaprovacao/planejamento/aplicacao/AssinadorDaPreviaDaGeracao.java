package br.com.trilhaaprovacao.planejamento.aplicacao;

import br.com.trilhaaprovacao.planejamento.dominio.CandidatoDeMateriaParaGeracao;
import br.com.trilhaaprovacao.planejamento.dominio.CandidatoDeRevisaoParaGeracao;
import br.com.trilhaaprovacao.planejamento.dominio.CandidatoDeTopicoParaGeracao;
import br.com.trilhaaprovacao.planejamento.dominio.ConfiguracaoDaGeracaoDeterministica;
import br.com.trilhaaprovacao.planejamento.dominio.EntradaDoDiaParaGeracao;
import br.com.trilhaaprovacao.planejamento.dominio.PlanoSemanal;
import br.com.trilhaaprovacao.planejamento.dominio.PreviaDaGeracaoDaSemana;
import br.com.trilhaaprovacao.planejamento.infraestrutura.BlocoDeEstudoPersistido;
import br.com.trilhaaprovacao.priorizacao.aplicacao.ResultadoDaPriorizacaoDeTopicos;
import br.com.trilhaaprovacao.revisoes.aplicacao.ResultadoDaAgendaDeRevisoes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class AssinadorDaPreviaDaGeracao {
    private static final String VERSAO = "geracao-deterministica-v2";

    private final ObjectMapper json;
    private final JdbcTemplate banco;

    public AssinadorDaPreviaDaGeracao(ObjectMapper json, JdbcTemplate banco) {
        this.json = json;
        this.banco = banco;
    }

    public String assinar(PlanoSemanal plano, LocalDate dataDeReferencia,
            ConfiguracaoDaGeracaoDeterministica configuracao,
            ResultadoDaPriorizacaoDeTopicos priorizacao,
            ResultadoDaAgendaDeRevisoes revisoes,
            List<CandidatoDeMateriaParaGeracao> materias,
            List<CandidatoDeTopicoParaGeracao> topicos,
            List<CandidatoDeRevisaoParaGeracao> candidatosDeRevisao,
            List<EntradaDoDiaParaGeracao> dias,
            List<BlocoDeEstudoPersistido> blocos,
            PreviaDaGeracaoDaSemana previa) {
        List<?> estadoCanonico = List.of(
                VERSAO, plano, dataDeReferencia, configuracao,
                priorizacao, revisoes, materias, topicos, candidatosDeRevisao, dias,
                blocos.stream().map(BlocoDeEstudoPersistido::paraDominio).toList(),
                consultarEstadoPersistido(plano.identificadorDoUsuario(),
                        plano.identificador(), dataDeReferencia),
                previa);
        try {
            byte[] estado = json.writeValueAsBytes(estadoCanonico);
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(estado);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(resumo);
        } catch (JacksonException | NoSuchAlgorithmException excecao) {
            throw new IllegalStateException("Nao foi possivel assinar a previa da geracao.",
                    excecao);
        }
    }

    public boolean equivalentes(String calculada, String informada) {
        if (calculada == null || informada == null) {
            return false;
        }
        return MessageDigest.isEqual(calculada.getBytes(StandardCharsets.UTF_8),
                informada.getBytes(StandardCharsets.UTF_8));
    }

    private List<EstadoPersistido> consultarEstadoPersistido(
            UUID usuario, UUID plano, LocalDate referencia) {
        return banco.query("""
                WITH entrada AS (
                    SELECT CAST(? AS UUID) AS usuario_id,
                           CAST(? AS UUID) AS plano_id,
                           CAST(? AS DATE) AS referencia
                ), contexto AS (
                    SELECT c.identificador AS concurso_id,
                           cargo.identificador AS cargo_id,
                           edital.identificador AS edital_id
                    FROM entrada e
                    JOIN concursos c ON c.usuario_id = e.usuario_id AND c.ativo = TRUE
                    JOIN cargos_do_concurso cargo
                      ON cargo.concurso_id = c.identificador AND cargo.selecionado = TRUE
                    JOIN editais edital
                      ON edital.concurso_id = c.identificador AND edital.principal = TRUE
                ), itens_oficiais AS (
                    SELECT item.*
                    FROM itens_do_edital item
                    JOIN materias_da_prova mp
                      ON mp.identificador = item.materia_da_prova_id
                    JOIN grupos_de_conteudo grupo
                      ON grupo.identificador = mp.grupo_de_conteudo_id
                    JOIN provas prova ON prova.identificador = grupo.prova_id
                    JOIN contexto c ON c.edital_id = item.edital_id
                                    AND c.cargo_id = prova.cargo_id
                ), estudos_ativos AS (
                    SELECT registro.*
                    FROM registros_de_estudo registro
                    JOIN topicos_da_materia topico
                      ON topico.identificador = registro.topico_id
                    JOIN materias materia ON materia.identificador = topico.materia_id
                    CROSS JOIN entrada e
                    WHERE materia.usuario_id = e.usuario_id
                      AND registro.situacao = 'ATIVO'
                      AND (registro.data_hora AT TIME ZONE
                           'America/Sao_Paulo')::date <= e.referencia
                )
                SELECT tipo, chave, conteudo
                FROM (
                    SELECT 'contexto' AS tipo, c.concurso_id::text AS chave,
                           to_jsonb(c)::text AS conteudo FROM contexto c
                    UNION ALL
                    SELECT 'item', item.identificador::text, to_jsonb(item)::text
                      FROM itens_oficiais item
                    UNION ALL
                    SELECT 'mapeamento', mapa.identificador::text, to_jsonb(mapa)::text
                      FROM mapeamentos_de_itens_do_edital mapa
                      JOIN itens_oficiais item
                        ON item.identificador = mapa.item_do_edital_id
                     WHERE mapa.confirmado = TRUE
                    UNION ALL
                    SELECT 'material', material.identificador::text,
                           to_jsonb(material)::text
                      FROM materiais_de_estudo material CROSS JOIN entrada e
                     WHERE material.usuario_id = e.usuario_id
                       AND material.arquivado = FALSE
                    UNION ALL
                    SELECT 'cobertura', cobertura.identificador::text,
                           to_jsonb(cobertura)::text
                      FROM coberturas_de_topicos_por_material cobertura
                      JOIN materiais_de_estudo material
                        ON material.identificador = cobertura.material_id
                      CROSS JOIN entrada e WHERE material.usuario_id = e.usuario_id
                       AND material.arquivado = FALSE
                    UNION ALL
                    SELECT 'estudo', estudo.identificador::text, to_jsonb(estudo)::text
                      FROM estudos_ativos estudo
                    UNION ALL
                    SELECT 'evidencia', evidencia.identificador::text,
                           to_jsonb(evidencia)::text
                      FROM evidencias_de_aprendizagem evidencia
                      JOIN estudos_ativos estudo
                        ON estudo.identificador = evidencia.registro_de_estudo_id
                    UNION ALL
                    SELECT 'padrao', padrao.identificador::text, to_jsonb(padrao)::text
                      FROM padroes_de_erro padrao CROSS JOIN entrada e
                     WHERE padrao.usuario_id = e.usuario_id
                    UNION ALL
                    SELECT 'ocorrencia', ocorrencia.identificador::text,
                           to_jsonb(ocorrencia)::text
                      FROM ocorrencias_de_padrao_de_erro ocorrencia
                      JOIN evidencias_de_aprendizagem evidencia
                        ON evidencia.identificador = ocorrencia.evidencia_id
                      JOIN estudos_ativos estudo
                        ON estudo.identificador = evidencia.registro_de_estudo_id
                    UNION ALL
                    SELECT 'prioridade', prioridade.identificador::text,
                           to_jsonb(prioridade)::text
                      FROM prioridades_de_materias_no_plano prioridade CROSS JOIN entrada e
                     WHERE prioridade.plano_id = e.plano_id
                    UNION ALL
                    SELECT 'disponibilidade', disponibilidade.identificador::text,
                           to_jsonb(disponibilidade)::text
                      FROM disponibilidades_do_dia disponibilidade CROSS JOIN entrada e
                     WHERE disponibilidade.plano_id = e.plano_id
                    UNION ALL
                    SELECT 'bloco', bloco.identificador::text, to_jsonb(bloco)::text
                      FROM blocos_de_estudo bloco CROSS JOIN entrada e
                     WHERE bloco.plano_id = e.plano_id
                    UNION ALL
                    SELECT 'bloco_revisao_aberto', bloco.identificador::text,
                           to_jsonb(bloco)::text
                      FROM blocos_de_estudo bloco
                      JOIN planos_semanais plano_aberto
                        ON plano_aberto.identificador = bloco.plano_id
                      CROSS JOIN entrada e
                     WHERE plano_aberto.usuario_id = e.usuario_id
                       AND plano_aberto.estado IN ('RASCUNHO', 'ATIVO')
                       AND bloco.tipo_de_atividade = 'REVISAO'
                       AND bloco.estado IN ('PLANEJADO', 'EM_ANDAMENTO')
                       AND bloco.topico_id IS NOT NULL
                ) estado
                ORDER BY tipo, chave
                """, (resultado, linha) -> new EstadoPersistido(
                        resultado.getString("tipo"), resultado.getString("chave"),
                        resultado.getString("conteudo")), usuario, plano, referencia);
    }

    private record EstadoPersistido(String tipo, String chave, String conteudo) {
    }
}
