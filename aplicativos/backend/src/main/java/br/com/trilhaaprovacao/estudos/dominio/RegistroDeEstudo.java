package br.com.trilhaaprovacao.estudos.dominio;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record RegistroDeEstudo(
        UUID identificador,
        UUID identificadorDoTopico,
        UUID identificadorDoMaterial,
        UUID identificadorDoRegistroDeOrigem,
        OffsetDateTime dataHora,
        int duracaoEmMinutos,
        String observacao,
        SituacaoDoRegistroDeEstudo situacao,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public RegistroDeEstudo {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(identificadorDoTopico);
        Objects.requireNonNull(dataHora);
        if (duracaoEmMinutos < 1 || duracaoEmMinutos > 1440) {
            throw new IllegalArgumentException(
                    "Duracao deve estar entre 1 e 1440 minutos.");
        }
        observacao = ValidacaoDeEstudo.opcional(observacao);
        Objects.requireNonNull(situacao);
        Objects.requireNonNull(criadoEm);
        Objects.requireNonNull(atualizadoEm);
    }

    public static RegistroDeEstudo criar(UUID topico, UUID material,
            OffsetDateTime dataHora, int duracao, String observacao) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new RegistroDeEstudo(UUID.randomUUID(), topico, material, null,
                dataHora, duracao, observacao, SituacaoDoRegistroDeEstudo.ATIVO,
                agora, agora, 0);
    }

    public RegistroDeEstudo encerrarComoCorrigido() {
        exigirAtivo();
        return comSituacao(SituacaoDoRegistroDeEstudo.CORRIGIDO);
    }

    public RegistroDeEstudo cancelar() {
        exigirAtivo();
        return comSituacao(SituacaoDoRegistroDeEstudo.CANCELADO);
    }

    public RegistroDeEstudo criarCorrecao(UUID topico, UUID material,
            OffsetDateTime novaDataHora, int novaDuracao, String novaObservacao) {
        exigirAtivo();
        OffsetDateTime agora = OffsetDateTime.now();
        return new RegistroDeEstudo(UUID.randomUUID(), topico, material,
                identificador, novaDataHora, novaDuracao, novaObservacao,
                SituacaoDoRegistroDeEstudo.ATIVO, agora, agora, 0);
    }

    private RegistroDeEstudo comSituacao(SituacaoDoRegistroDeEstudo novaSituacao) {
        return new RegistroDeEstudo(identificador, identificadorDoTopico,
                identificadorDoMaterial, identificadorDoRegistroDeOrigem, dataHora,
                duracaoEmMinutos, observacao, novaSituacao, criadoEm,
                OffsetDateTime.now(), versao);
    }

    private void exigirAtivo() {
        if (situacao != SituacaoDoRegistroDeEstudo.ATIVO) {
            throw new IllegalStateException("Somente estudo ativo pode ser alterado.");
        }
    }
}
