package br.com.trilhaaprovacao.conteudos.api;

import br.com.trilhaaprovacao.conteudos.dominio.Materia;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDeMateria(
        UUID identificador,
        String nome,
        String descricao,
        String cor,
        boolean arquivada,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    static RespostaDeMateria de(Materia materia) {
        return new RespostaDeMateria(materia.identificador(), materia.nome(), materia.descricao(),
                materia.cor(), materia.arquivada(), materia.criadoEm(), materia.atualizadoEm(), materia.versao());
    }
}
