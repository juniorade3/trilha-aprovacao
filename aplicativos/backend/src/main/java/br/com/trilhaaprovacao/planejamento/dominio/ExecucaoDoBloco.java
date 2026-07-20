package br.com.trilhaaprovacao.planejamento.dominio;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record ExecucaoDoBloco(
        UUID identificador,
        UUID identificadorDoUsuario,
        UUID identificadorDoBloco,
        OffsetDateTime iniciadaEm,
        OffsetDateTime encerradaEm,
        Integer duracaoExecutadaEmMinutos,
        ResultadoDaExecucao resultado,
        String observacao,
        UUID identificadorDoRegistroDeEstudo,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public ExecucaoDoBloco {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(identificadorDoUsuario);
        Objects.requireNonNull(identificadorDoBloco);
        Objects.requireNonNull(iniciadaEm);
        Objects.requireNonNull(criadoEm);
        Objects.requireNonNull(atualizadoEm);
        observacao = textoOpcional(observacao, 2000);
        if (encerradaEm == null) {
            if (duracaoExecutadaEmMinutos != null || resultado != null
                    || observacao != null || identificadorDoRegistroDeEstudo != null) {
                throw new IllegalArgumentException(
                        "Execucao em andamento nao possui dados de encerramento.");
            }
        } else {
            if (encerradaEm.isBefore(iniciadaEm)) {
                throw new IllegalArgumentException(
                        "Encerramento nao pode ocorrer antes do inicio.");
            }
            if (duracaoExecutadaEmMinutos == null
                    || duracaoExecutadaEmMinutos < 1
                    || duracaoExecutadaEmMinutos > 1440) {
                throw new IllegalArgumentException(
                        "Duracao executada deve estar entre 1 e 1440 minutos.");
            }
            Objects.requireNonNull(resultado);
        }
    }

    public static ExecucaoDoBloco iniciar(UUID usuario, UUID bloco,
            OffsetDateTime momento) {
        Objects.requireNonNull(momento);
        return new ExecucaoDoBloco(UUID.randomUUID(), usuario, bloco, momento,
                null, null, null, null, null, momento, momento, 0);
    }

    public ExecucaoDoBloco encerrar(ResultadoDaExecucao novoResultado,
            int duracao, String novaObservacao, OffsetDateTime momento) {
        if (!estaEmAndamento()) {
            throw new IllegalStateException("A execucao ja foi encerrada.");
        }
        Objects.requireNonNull(momento);
        return new ExecucaoDoBloco(identificador, identificadorDoUsuario,
                identificadorDoBloco, iniciadaEm, momento, duracao,
                novoResultado, novaObservacao, identificadorDoRegistroDeEstudo,
                criadoEm, momento, versao);
    }

    public ExecucaoDoBloco vincularRegistroDeEstudo(UUID registro,
            OffsetDateTime momento) {
        if (estaEmAndamento()) {
            throw new IllegalStateException(
                    "Somente execucao encerrada pode ser vinculada ao historico.");
        }
        Objects.requireNonNull(registro);
        Objects.requireNonNull(momento);
        if (identificadorDoRegistroDeEstudo != null) {
            if (identificadorDoRegistroDeEstudo.equals(registro)) return this;
            throw new IllegalStateException(
                    "A execucao ja possui outro registro de estudo vinculado.");
        }
        return new ExecucaoDoBloco(identificador, identificadorDoUsuario,
                identificadorDoBloco, iniciadaEm, encerradaEm,
                duracaoExecutadaEmMinutos, resultado, observacao, registro,
                criadoEm, momento, versao);
    }

    public ExecucaoDoBloco corrigir(ResultadoDaExecucao novoResultado, int novaDuracao,
            String novaObservacao, UUID novoRegistro, OffsetDateTime momento) {
        if (estaEmAndamento()) {
            throw new IllegalStateException("Execução em andamento não pode ser corrigida.");
        }
        Objects.requireNonNull(novoResultado);
        Objects.requireNonNull(momento);
        return new ExecucaoDoBloco(identificador, identificadorDoUsuario,
                identificadorDoBloco, iniciadaEm, encerradaEm, novaDuracao,
                novoResultado, novaObservacao, novoRegistro, criadoEm, momento, versao);
    }

    public boolean estaEmAndamento() {
        return encerradaEm == null;
    }

    public boolean equivaleAoEncerramento(ResultadoDaExecucao outroResultado,
            int outraDuracao, String outraObservacao) {
        return !estaEmAndamento()
                && resultado == outroResultado
                && Objects.equals(duracaoExecutadaEmMinutos, outraDuracao)
                && Objects.equals(observacao, textoOpcional(outraObservacao, 2000));
    }

    private static String textoOpcional(String valor, int limite) {
        if (valor == null || valor.isBlank()) return null;
        String normalizado = valor.trim();
        if (normalizado.length() > limite) {
            throw new IllegalArgumentException(
                    "Observacao excede " + limite + " caracteres.");
        }
        return normalizado;
    }
}
