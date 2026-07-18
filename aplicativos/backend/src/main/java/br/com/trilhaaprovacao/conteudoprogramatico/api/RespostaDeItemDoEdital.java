package br.com.trilhaaprovacao.conteudoprogramatico.api;

import br.com.trilhaaprovacao.conteudoprogramatico.dominio.ItemDoEdital;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDeItemDoEdital(
        UUID identificador,
        UUID identificadorDoEdital,
        UUID identificadorDaMateriaDaProva,
        String descricaoOriginal,
        UUID identificadorDoItemPai,
        int ordem,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public static RespostaDeItemDoEdital de(ItemDoEdital item) {
        return new RespostaDeItemDoEdital(item.identificador(),
                item.identificadorDoEdital(), item.identificadorDaMateriaDaProva(),
                item.descricaoOriginal(), item.identificadorDoItemPai(), item.ordem(),
                item.criadoEm(), item.atualizadoEm(), item.versao());
    }
}
