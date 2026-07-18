package br.com.trilhaaprovacao.concursos.api;

import br.com.trilhaaprovacao.concursos.dominio.Concurso;
import br.com.trilhaaprovacao.concursos.dominio.SituacaoDoConcurso;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDeConcurso(
        UUID identificador,
        String nome,
        String descricao,
        String orgao,
        String banca,
        SituacaoDoConcurso situacao,
        LocalDate dataPrevistaPrincipal,
        boolean ativo,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    static RespostaDeConcurso de(Concurso concurso) {
        return new RespostaDeConcurso(concurso.identificador(), concurso.nome(),
                concurso.descricao(), concurso.orgao(), concurso.banca(), concurso.situacao(),
                concurso.dataPrevistaPrincipal(), concurso.ativo(), concurso.criadoEm(),
                concurso.atualizadoEm(), concurso.versao());
    }
}
