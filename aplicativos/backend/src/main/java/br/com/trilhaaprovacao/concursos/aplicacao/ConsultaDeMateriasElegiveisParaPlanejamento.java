package br.com.trilhaaprovacao.concursos.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaDeMateriasElegiveisParaPlanejamento {
    private final JdbcClient banco;

    public ConsultaDeMateriasElegiveisParaPlanejamento(JdbcClient banco) {
        this.banco = banco;
    }

    @Transactional(readOnly = true)
    public List<MateriaElegivelParaPlanejamento> consultar(UUID usuario) {
        List<ResultadoDaConsulta> resultado = banco.sql("""
                WITH concurso_ativo AS (
                    SELECT identificador
                    FROM concursos
                    WHERE usuario_id = :usuario AND ativo = TRUE
                    ORDER BY identificador
                    LIMIT 1
                ),
                cargo_selecionado AS (
                    SELECT ca.identificador
                    FROM cargos_do_concurso ca
                    JOIN concurso_ativo c ON c.identificador = ca.concurso_id
                    WHERE ca.selecionado = TRUE
                    ORDER BY ca.ordem, ca.identificador
                    LIMIT 1
                ),
                candidatas AS (
                    SELECT m.identificador, m.nome, m.nome_normalizado,
                           p.ordem AS ordem_prova, g.ordem AS ordem_grupo,
                           mp.ordem AS ordem_materia,
                           ROW_NUMBER() OVER (
                               PARTITION BY m.identificador
                               ORDER BY p.ordem, g.ordem, mp.ordem,
                                        m.nome_normalizado, m.identificador
                           ) AS repeticao
                    FROM concurso_ativo c
                    JOIN cargos_do_concurso ca ON ca.concurso_id = c.identificador
                    JOIN cargo_selecionado cs ON cs.identificador = ca.identificador
                    JOIN provas p ON p.cargo_id = ca.identificador
                    JOIN grupos_de_conteudo g ON g.prova_id = p.identificador
                    JOIN materias_da_prova mp ON mp.grupo_de_conteudo_id = g.identificador
                    JOIN materias m ON m.identificador = mp.materia_id
                    WHERE m.usuario_id = :usuario
                      AND m.arquivada = FALSE
                ),
                elegiveis AS (
                    SELECT identificador, nome, nome_normalizado,
                           ordem_prova, ordem_grupo, ordem_materia
                    FROM candidatas
                    WHERE repeticao = 1
                ),
                elegiveis_ordenadas AS (
                    SELECT identificador, nome, nome_normalizado,
                           ROW_NUMBER() OVER (
                               ORDER BY ordem_prova, ordem_grupo, ordem_materia,
                                        nome_normalizado, identificador
                           ) AS ordem_estavel
                    FROM elegiveis
                ),
                diagnostico AS (
                    SELECT CASE
                        WHEN NOT EXISTS (SELECT 1 FROM concurso_ativo)
                            THEN 'CONCURSO_ATIVO_NAO_ENCONTRADO'
                        WHEN NOT EXISTS (SELECT 1 FROM cargo_selecionado)
                            THEN 'CARGO_SELECIONADO_NAO_ENCONTRADO'
                        WHEN NOT EXISTS (SELECT 1 FROM elegiveis_ordenadas)
                            THEN 'MATERIAS_ELEGIVEIS_NAO_ENCONTRADAS'
                        END AS codigo_do_erro
                )
                SELECT d.codigo_do_erro, e.identificador, e.nome,
                       e.nome_normalizado, e.ordem_estavel
                FROM diagnostico d
                LEFT JOIN elegiveis_ordenadas e ON d.codigo_do_erro IS NULL
                ORDER BY e.ordem_estavel
                """)
                .param("usuario", usuario)
                .query((dados, linha) -> new ResultadoDaConsulta(
                        dados.getString("codigo_do_erro"),
                        dados.getObject("identificador", UUID.class),
                        dados.getString("nome"),
                        dados.getString("nome_normalizado"),
                        dados.getInt("ordem_estavel")))
                .list();
        String codigoDoErro = resultado.getFirst().codigoDoErro();
        if (codigoDoErro != null) {
            throw erroDeElegibilidade(codigoDoErro);
        }
        return resultado.stream().map(item -> new MateriaElegivelParaPlanejamento(
                item.identificador(), item.nome(), item.nomeNormalizado(),
                item.ordemEstavel())).toList();
    }

    private RegraDeDominio erroDeElegibilidade(String codigo) {
        return switch (codigo) {
            case "CONCURSO_ATIVO_NAO_ENCONTRADO" -> new RegraDeDominio(codigo,
                    "Defina um concurso ativo antes de gerar a semana.");
            case "CARGO_SELECIONADO_NAO_ENCONTRADO" -> new RegraDeDominio(codigo,
                    "Selecione um cargo no concurso ativo antes de gerar a semana.");
            case "MATERIAS_ELEGIVEIS_NAO_ENCONTRADAS" -> new RegraDeDominio(codigo,
                    "O cargo selecionado nao possui materias pessoais ativas para gerar a semana.");
            default -> throw new IllegalStateException("Diagnostico de elegibilidade invalido.");
        };
    }

    private record ResultadoDaConsulta(
            String codigoDoErro,
            UUID identificador,
            String nome,
            String nomeNormalizado,
            int ordemEstavel) {
    }
}
