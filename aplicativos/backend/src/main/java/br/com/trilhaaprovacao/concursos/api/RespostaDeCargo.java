package br.com.trilhaaprovacao.concursos.api;

import br.com.trilhaaprovacao.concursos.dominio.CargoDoConcurso;
import br.com.trilhaaprovacao.concursos.dominio.NivelDeEscolaridade;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDeCargo(
        UUID identificador,
        UUID identificadorDoConcurso,
        String nome,
        String area,
        String especialidade,
        NivelDeEscolaridade nivelDeEscolaridade,
        boolean selecionado,
        int ordem,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    static RespostaDeCargo de(CargoDoConcurso cargo) {
        return new RespostaDeCargo(cargo.identificador(), cargo.identificadorDoConcurso(),
                cargo.nome(), cargo.area(), cargo.especialidade(), cargo.nivelDeEscolaridade(),
                cargo.selecionado(), cargo.ordem(), cargo.criadoEm(),
                cargo.atualizadoEm(), cargo.versao());
    }
}
