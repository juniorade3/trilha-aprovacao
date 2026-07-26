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
        String numeroOficial,
        String descricaoNormalizada,
        UUID identificadorDaImportacaoDeEdital,
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
        numeroOficial = textoOpcional(numeroOficial, 80,
                "Numero oficial");
        descricaoNormalizada = textoOpcional(descricaoNormalizada, 20_000,
                "Descricao normalizada");
    }

    public ItemDoEdital(UUID identificador, UUID identificadorDoEdital,
            UUID identificadorDaMateriaDaProva, String descricaoOriginal,
            UUID identificadorDoItemPai, int ordem, OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm, long versao) {
        this(identificador, identificadorDoEdital,
                identificadorDaMateriaDaProva, descricaoOriginal,
                identificadorDoItemPai, ordem, null, null, null, criadoEm,
                atualizadoEm, versao);
    }

    public static ItemDoEdital criar(UUID edital, UUID materiaDaProva,
            String descricaoOriginal, UUID itemPai, int ordem) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new ItemDoEdital(UUID.randomUUID(), edital, materiaDaProva,
                descricaoOriginal, itemPai, ordem, null, null, null, agora,
                agora, 0);
    }

    public static ItemDoEdital criarDaImportacao(UUID edital,
            UUID materiaDaProva, String descricaoOriginal, UUID itemPai,
            int ordem, String numeroOficial, String descricaoNormalizada,
            UUID identificadorDaImportacao) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new ItemDoEdital(UUID.randomUUID(), edital, materiaDaProva,
                descricaoOriginal, itemPai, ordem, numeroOficial,
                descricaoNormalizada, Objects.requireNonNull(
                        identificadorDaImportacao), agora, agora, 0);
    }

    public ItemDoEdital alterar(String novaDescricaoOriginal, UUID novoItemPai, int novaOrdem) {
        return new ItemDoEdital(identificador, identificadorDoEdital,
                identificadorDaMateriaDaProva, novaDescricaoOriginal, novoItemPai,
                novaOrdem, numeroOficial, descricaoNormalizada,
                identificadorDaImportacaoDeEdital, criadoEm,
                OffsetDateTime.now(), versao);
    }

    private static String textoOpcional(String valor, int limite,
            String campo) {
        if (valor == null) return null;
        String texto = valor.strip();
        if (texto.isEmpty() || texto.length() > limite) {
            throw new IllegalArgumentException(campo + " invalido.");
        }
        return texto;
    }
}
