package br.com.trilhaaprovacao.concursos.api;

import br.com.trilhaaprovacao.concursos.dominio.MateriaDaProva;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDeMateriaDaProva(
        UUID identificador,
        UUID identificadorDoGrupoDeConteudo,
        UUID identificadorDaMateria,
        String nomeDaMateria,
        int ordem,
        BigDecimal peso,
        Integer quantidadeDeQuestoes,
        BigDecimal pontuacaoMaxima,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    static RespostaDeMateriaDaProva de(MateriaDaProva materia, String nomeDaMateria) {
        return new RespostaDeMateriaDaProva(materia.identificador(),
                materia.identificadorDoGrupoDeConteudo(), materia.identificadorDaMateria(),
                nomeDaMateria, materia.ordem(), materia.peso(), materia.quantidadeDeQuestoes(),
                materia.pontuacaoMaxima(), materia.criadoEm(), materia.atualizadoEm(),
                materia.versao());
    }
}
