package br.com.trilhaaprovacao.concursos.api;

import br.com.trilhaaprovacao.concursos.dominio.GrupoDeConteudo;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDeGrupo(
        UUID identificador,
        UUID identificadorDaProva,
        String nome,
        int ordem,
        Integer quantidadeDeQuestoes,
        BigDecimal pontuacaoMaxima,
        BigDecimal pontuacaoMinima,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    static RespostaDeGrupo de(GrupoDeConteudo grupo) {
        return new RespostaDeGrupo(grupo.identificador(), grupo.identificadorDaProva(),
                grupo.nome(), grupo.ordem(), grupo.quantidadeDeQuestoes(),
                grupo.pontuacaoMaxima(), grupo.pontuacaoMinima(), grupo.criadoEm(),
                grupo.atualizadoEm(), grupo.versao());
    }
}
