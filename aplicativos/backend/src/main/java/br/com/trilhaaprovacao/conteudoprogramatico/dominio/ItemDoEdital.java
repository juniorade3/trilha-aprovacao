package br.com.trilhaaprovacao.conteudoprogramatico.dominio;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record ItemDoEdital(
        UUID identificador,
        UUID identificadorDoEdital,
        UUID identificadorDaMateriaDaProva,
        String descricaoOriginal,
        UUID identificadorDoItemPai,
        int ordem,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public ItemDoEdital {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(identificadorDoEdital);
        Objects.requireNonNull(identificadorDaMateriaDaProva);
        if (descricaoOriginal == null || descricaoOriginal.isBlank()) {
            throw new IllegalArgumentException("Descricao original e obrigatoria.");
        }
        if (identificador.equals(identificadorDoItemPai)) {
            throw new IllegalArgumentException("Item nao pode ser pai de si mesmo.");
        }
        if (ordem < 1) {
            throw new IllegalArgumentException("Ordem deve ser positiva.");
        }
        Objects.requireNonNull(criadoEm);
        Objects.requireNonNull(atualizadoEm);
    }

    public static ItemDoEdital criar(UUID edital, UUID materiaDaProva,
            String descricaoOriginal, UUID itemPai, int ordem) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new ItemDoEdital(UUID.randomUUID(), edital, materiaDaProva,
                descricaoOriginal, itemPai, ordem, agora, agora, 0);
    }

    public ItemDoEdital alterar(String novaDescricaoOriginal, UUID novoItemPai, int novaOrdem) {
        return new ItemDoEdital(identificador, identificadorDoEdital,
                identificadorDaMateriaDaProva, novaDescricaoOriginal, novoItemPai,
                novaOrdem, criadoEm, OffsetDateTime.now(), versao);
    }
}
