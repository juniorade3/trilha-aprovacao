package br.com.trilhaaprovacao.conteudos.aplicacao;

import br.com.trilhaaprovacao.conteudos.aplicacao.ResultadoDeUsoDaMateria.ConcursoRelacionado;
import br.com.trilhaaprovacao.conteudos.aplicacao.ResultadoDeUsoDaMateria.EstudoRecente;
import br.com.trilhaaprovacao.conteudos.aplicacao.ResultadoDeUsoDaMateria.MaterialRelacionado;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaDeUsoDaMateria {
    private final JdbcTemplate banco;
    private final ServicoDeMaterias materias;

    public ConsultaDeUsoDaMateria(JdbcTemplate banco, ServicoDeMaterias materias) {
        this.banco = banco;
        this.materias = materias;
    }

    @Transactional(readOnly = true)
    public ResultadoDeUsoDaMateria consultar(UUID usuario, UUID materia) {
        materias.obter(usuario, materia);
        return new ResultadoDeUsoDaMateria(
                banco.query("""
                                SELECT DISTINCT m.identificador, m.titulo, m.tipo
                                FROM materiais_de_estudo m
                                JOIN coberturas_de_topicos_por_material c
                                  ON c.material_id = m.identificador
                                JOIN topicos_da_materia t
                                  ON t.identificador = c.topico_id
                                WHERE t.materia_id = ? AND m.usuario_id = ?
                                ORDER BY m.titulo
                                """,
                        (resultado, linha) -> new MaterialRelacionado(
                                resultado.getObject("identificador", UUID.class),
                                resultado.getString("titulo"),
                                resultado.getString("tipo")),
                        materia, usuario),
                banco.query("""
                                SELECT r.identificador, t.nome AS nome_do_topico,
                                       r.data_hora, r.duracao_em_minutos
                                FROM registros_de_estudo r
                                JOIN topicos_da_materia t
                                  ON t.identificador = r.topico_id
                                JOIN materias m ON m.identificador = t.materia_id
                                WHERE t.materia_id = ? AND m.usuario_id = ?
                                  AND r.situacao = 'ATIVO'
                                ORDER BY r.data_hora DESC
                                LIMIT 5
                                """,
                        (resultado, linha) -> new EstudoRecente(
                                resultado.getObject("identificador", UUID.class),
                                resultado.getString("nome_do_topico"),
                                resultado.getObject(
                                        "data_hora", java.time.OffsetDateTime.class),
                                resultado.getInt("duracao_em_minutos")),
                        materia, usuario),
                banco.query("""
                                SELECT DISTINCT c.identificador, c.nome, c.ativo
                                FROM concursos c
                                JOIN cargos_do_concurso cargo
                                  ON cargo.concurso_id = c.identificador
                                JOIN provas p ON p.cargo_id = cargo.identificador
                                JOIN grupos_de_conteudo g ON g.prova_id = p.identificador
                                JOIN materias_da_prova mp
                                  ON mp.grupo_de_conteudo_id = g.identificador
                                WHERE mp.materia_id = ? AND c.usuario_id = ?
                                ORDER BY c.ativo DESC, c.nome
                                """,
                        (resultado, linha) -> new ConcursoRelacionado(
                                resultado.getObject("identificador", UUID.class),
                                resultado.getString("nome"),
                                resultado.getBoolean("ativo")),
                        materia, usuario));
    }
}
