package br.com.trilhaaprovacao.estudos.dominio;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record MaterialDeEstudo(
        UUID identificador,
        UUID identificadorDoUsuario,
        String titulo,
        TipoDeMaterial tipo,
        String descricao,
        String fonte,
        String endereco,
        Integer duracaoEstimadaEmMinutos,
        boolean arquivado,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public MaterialDeEstudo {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(identificadorDoUsuario);
        titulo = ValidacaoDeEstudo.obrigatorio(titulo, "Titulo");
        Objects.requireNonNull(tipo);
        descricao = ValidacaoDeEstudo.opcional(descricao);
        fonte = ValidacaoDeEstudo.opcional(fonte);
        endereco = ValidacaoDeEstudo.endereco(endereco);
        if (duracaoEstimadaEmMinutos != null && duracaoEstimadaEmMinutos < 1) {
            throw new IllegalArgumentException("Duracao estimada deve ser positiva.");
        }
        Objects.requireNonNull(criadoEm);
        Objects.requireNonNull(atualizadoEm);
    }

    public static MaterialDeEstudo criar(UUID usuario, String titulo, TipoDeMaterial tipo,
            String descricao, String fonte, String endereco, Integer duracao) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new MaterialDeEstudo(UUID.randomUUID(), usuario, titulo, tipo,
                descricao, fonte, endereco, duracao, false, agora, agora, 0);
    }

    public MaterialDeEstudo alterar(String novoTitulo, TipoDeMaterial novoTipo,
            String novaDescricao, String novaFonte, String novoEndereco, Integer novaDuracao) {
        if (arquivado) {
            throw new IllegalStateException("Material arquivado nao pode ser alterado.");
        }
        return new MaterialDeEstudo(identificador, identificadorDoUsuario, novoTitulo,
                novoTipo, novaDescricao, novaFonte, novoEndereco, novaDuracao,
                false, criadoEm, OffsetDateTime.now(), versao);
    }

    public MaterialDeEstudo definirArquivamento(boolean deveArquivar) {
        return new MaterialDeEstudo(identificador, identificadorDoUsuario, titulo,
                tipo, descricao, fonte, endereco, duracaoEstimadaEmMinutos,
                deveArquivar, criadoEm, OffsetDateTime.now(), versao);
    }
}
