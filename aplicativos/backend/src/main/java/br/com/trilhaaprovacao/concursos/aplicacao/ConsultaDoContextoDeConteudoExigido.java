package br.com.trilhaaprovacao.concursos.aplicacao;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaDoContextoDeConteudoExigido {
    private final JdbcClient banco;

    public ConsultaDoContextoDeConteudoExigido(JdbcClient banco) {
        this.banco = banco;
    }

    @Transactional(readOnly = true)
    public Optional<ContextoDeConteudoExigido> consultar(UUID usuario) {
        return banco.sql("""
                SELECT c.identificador AS concurso_id,
                       c.nome AS concurso_nome,
                       c.orgao AS concurso_orgao,
                       c.banca AS concurso_banca,
                       c.situacao AS concurso_situacao,
                       cargo.identificador AS cargo_id,
                       cargo.nome AS cargo_nome,
                       ed.identificador AS edital_id,
                       ed.titulo AS edital_titulo
                FROM concursos c
                LEFT JOIN cargos_do_concurso cargo
                  ON cargo.concurso_id = c.identificador
                 AND cargo.selecionado = TRUE
                LEFT JOIN editais ed
                  ON ed.concurso_id = c.identificador
                 AND ed.principal = TRUE
                WHERE c.usuario_id = :usuario
                  AND c.ativo = TRUE
                ORDER BY c.identificador
                LIMIT 1
                """)
                .param("usuario", usuario)
                .query((resultado, linha) -> new ContextoDeConteudoExigido(
                        resultado.getObject("concurso_id", UUID.class),
                        resultado.getString("concurso_nome"),
                        resultado.getString("concurso_orgao"),
                        resultado.getString("concurso_banca"),
                        resultado.getString("concurso_situacao"),
                        resultado.getObject("cargo_id", UUID.class),
                        resultado.getString("cargo_nome"),
                        resultado.getObject("edital_id", UUID.class),
                        resultado.getString("edital_titulo")))
                .optional();
    }
}
