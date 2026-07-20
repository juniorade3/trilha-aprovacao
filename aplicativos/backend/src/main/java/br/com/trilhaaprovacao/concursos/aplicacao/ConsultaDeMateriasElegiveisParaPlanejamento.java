package br.com.trilhaaprovacao.concursos.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
        UUID concurso = banco.sql("""
                SELECT identificador FROM concursos
                WHERE usuario_id = :usuario AND ativo = TRUE
                """).param("usuario", usuario).query(UUID.class).optional()
                .orElseThrow(() -> new RegraDeDominio("CONCURSO_ATIVO_NAO_ENCONTRADO",
                        "Defina um concurso ativo antes de gerar a semana."));
        boolean possuiCargo = banco.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM cargos_do_concurso
                    WHERE concurso_id = :concurso AND selecionado = TRUE
                )
                """).param("concurso", concurso).query(Boolean.class).single();
        if (!possuiCargo) {
            throw new RegraDeDominio("CARGO_SELECIONADO_NAO_ENCONTRADO",
                    "Selecione um cargo no concurso ativo antes de gerar a semana.");
        }
        List<MateriaElegivelSemOrdem> encontradas = banco.sql("""
                WITH candidatas AS (
                    SELECT m.identificador, m.nome, m.nome_normalizado,
                           p.ordem AS ordem_prova, g.ordem AS ordem_grupo,
                           mp.ordem AS ordem_materia,
                           ROW_NUMBER() OVER (
                               PARTITION BY m.identificador
                               ORDER BY p.ordem, g.ordem, mp.ordem,
                                        m.nome_normalizado, m.identificador
                           ) AS repeticao
                    FROM concursos c
                    JOIN cargos_do_concurso ca
                      ON ca.concurso_id = c.identificador AND ca.selecionado = TRUE
                    JOIN provas p ON p.cargo_id = ca.identificador
                    JOIN grupos_de_conteudo g ON g.prova_id = p.identificador
                    JOIN materias_da_prova mp ON mp.grupo_de_conteudo_id = g.identificador
                    JOIN materias m ON m.identificador = mp.materia_id
                    WHERE c.identificador = :concurso
                      AND c.usuario_id = :usuario
                      AND m.usuario_id = :usuario
                      AND m.arquivada = FALSE
                )
                SELECT identificador, nome, nome_normalizado
                FROM candidatas
                WHERE repeticao = 1
                ORDER BY ordem_prova, ordem_grupo, ordem_materia,
                         nome_normalizado, identificador
                """)
                .param("concurso", concurso)
                .param("usuario", usuario)
                .query((resultado, linha) -> new MateriaElegivelSemOrdem(
                        resultado.getObject("identificador", UUID.class),
                        resultado.getString("nome"),
                        resultado.getString("nome_normalizado")))
                .list();
        if (encontradas.isEmpty()) {
            throw new RegraDeDominio("MATERIAS_ELEGIVEIS_NAO_ENCONTRADAS",
                    "O cargo selecionado nao possui materias pessoais ativas para gerar a semana.");
        }
        AtomicInteger ordem = new AtomicInteger(1);
        return encontradas.stream().map(item -> new MateriaElegivelParaPlanejamento(
                item.identificador(), item.nome(), item.nomeNormalizado(),
                ordem.getAndIncrement())).toList();
    }

    private record MateriaElegivelSemOrdem(
            UUID identificador, String nome, String nomeNormalizado) {
    }
}
