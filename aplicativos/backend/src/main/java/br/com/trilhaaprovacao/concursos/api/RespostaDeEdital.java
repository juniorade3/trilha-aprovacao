package br.com.trilhaaprovacao.concursos.api;

import br.com.trilhaaprovacao.concursos.dominio.Edital;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDeEdital(
        UUID identificador,
        UUID identificadorDoConcurso,
        String titulo,
        String numero,
        Integer ano,
        String descricao,
        LocalDate dataDePublicacao,
        String enderecoDoDocumento,
        boolean principal,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    static RespostaDeEdital de(Edital edital) {
        return new RespostaDeEdital(edital.identificador(), edital.identificadorDoConcurso(),
                edital.titulo(), edital.numero(), edital.ano(), edital.descricao(),
                edital.dataDePublicacao(), edital.enderecoDoDocumento(), edital.principal(),
                edital.criadoEm(), edital.atualizadoEm(), edital.versao());
    }
}
