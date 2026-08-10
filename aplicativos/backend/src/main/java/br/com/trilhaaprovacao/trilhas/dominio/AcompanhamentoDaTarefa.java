package br.com.trilhaaprovacao.trilhas.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AcompanhamentoDaTarefa(
        UUID identificador,
        UUID identificadorDaAdesao,
        UUID identificadorDaTarefa,
        SituacaoDoAcompanhamentoDaTarefa situacao,
        String observacao,
        OffsetDateTime concluidaEm,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public AcompanhamentoDaTarefa {
        if (identificador == null || identificadorDaAdesao == null
                || identificadorDaTarefa == null || situacao == null
                || criadoEm == null || atualizadoEm == null) {
            throw new IllegalArgumentException("Acompanhamento da tarefa incompleto.");
        }
        observacao = normalizarObservacao(observacao);
        if (situacao == SituacaoDoAcompanhamentoDaTarefa.CONCLUIDA && concluidaEm == null) {
            throw new IllegalArgumentException("Tarefa concluida exige a data de conclusao.");
        }
        if (situacao != SituacaoDoAcompanhamentoDaTarefa.CONCLUIDA && concluidaEm != null) {
            throw new IllegalArgumentException("Somente tarefa concluida possui data de conclusao.");
        }
    }

    public static AcompanhamentoDaTarefa criar(UUID adesao, UUID tarefa) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new AcompanhamentoDaTarefa(UUID.randomUUID(), adesao, tarefa,
                SituacaoDoAcompanhamentoDaTarefa.PENDENTE, null, null, agora, agora, 0);
    }

    public AcompanhamentoDaTarefa alterar(SituacaoDoAcompanhamentoDaTarefa novaSituacao,
            String novaObservacao) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new AcompanhamentoDaTarefa(identificador, identificadorDaAdesao,
                identificadorDaTarefa, novaSituacao, novaObservacao,
                novaSituacao == SituacaoDoAcompanhamentoDaTarefa.CONCLUIDA ? agora : null,
                criadoEm, agora, versao);
    }

    private static String normalizarObservacao(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String normalizado = valor.trim();
        if (normalizado.length() > 2000) {
            throw new IllegalArgumentException("Observacao excede 2000 caracteres.");
        }
        return normalizado;
    }
}
