package br.com.trilhaaprovacao.conteudoprogramatico.api;

import br.com.trilhaaprovacao.conteudoprogramatico.dominio.MapeamentoDeItemDoEdital;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDeMapeamento(
        UUID identificador,
        UUID identificadorDoItemDoEdital,
        UUID identificadorDoTopicoDaMateria,
        String nomeDoTopico,
        boolean confirmado,
        OffsetDateTime criadoEm) {

    public static RespostaDeMapeamento de(
            MapeamentoDeItemDoEdital mapeamento, String nomeDoTopico) {
        return new RespostaDeMapeamento(mapeamento.identificador(),
                mapeamento.identificadorDoItemDoEdital(),
                mapeamento.identificadorDoTopicoDaMateria(), nomeDoTopico,
                mapeamento.confirmado(), mapeamento.criadoEm());
    }
}
