package br.com.trilhaaprovacao.planejamento.dominio;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record PlanoSemanal(
        UUID identificador,
        UUID identificadorDoUsuario,
        LocalDate dataInicial,
        EstadoDoPlanoSemanal estado,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public PlanoSemanal {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(identificadorDoUsuario);
        Objects.requireNonNull(dataInicial);
        Objects.requireNonNull(estado);
        Objects.requireNonNull(criadoEm);
        Objects.requireNonNull(atualizadoEm);
        if (dataInicial.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("A semana deve comecar em uma segunda-feira.");
        }
    }

    public static PlanoSemanal criar(UUID usuario, LocalDate dataInicial) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new PlanoSemanal(UUID.randomUUID(), usuario, dataInicial,
                EstadoDoPlanoSemanal.RASCUNHO, agora, agora, 0);
    }

    public LocalDate dataFinal() {
        return dataInicial.plusDays(6);
    }

    public void exigirEditavel() {
        exigirRascunho();
    }

    public void exigirRascunho() {
        if (estado != EstadoDoPlanoSemanal.RASCUNHO) {
            throw new IllegalStateException(
                    "Somente plano em rascunho permite esta operacao.");
        }
    }

    public boolean estaAtivo() {
        return estado == EstadoDoPlanoSemanal.ATIVO;
    }

    public PlanoSemanal ativar() {
        if (estaAtivo()) return this;
        exigirRascunho();
        return new PlanoSemanal(identificador, identificadorDoUsuario, dataInicial,
                EstadoDoPlanoSemanal.ATIVO, criadoEm, OffsetDateTime.now(), versao);
    }

    public PlanoSemanal encerrar() {
        if (estado == EstadoDoPlanoSemanal.ENCERRADO) return this;
        if (estado != EstadoDoPlanoSemanal.ATIVO) {
            throw new IllegalStateException("Somente plano ativo pode ser encerrado.");
        }
        return comEstado(EstadoDoPlanoSemanal.ENCERRADO);
    }

    public PlanoSemanal cancelar() {
        if (estado == EstadoDoPlanoSemanal.CANCELADO) return this;
        if (estado != EstadoDoPlanoSemanal.RASCUNHO && estado != EstadoDoPlanoSemanal.ATIVO) {
            throw new IllegalStateException("O estado atual não permite cancelamento.");
        }
        return comEstado(EstadoDoPlanoSemanal.CANCELADO);
    }

    private PlanoSemanal comEstado(EstadoDoPlanoSemanal novoEstado) {
        return new PlanoSemanal(identificador, identificadorDoUsuario, dataInicial,
                novoEstado, criadoEm, OffsetDateTime.now(), versao);
    }

    public boolean contem(LocalDate data) {
        return data != null && !data.isBefore(dataInicial) && !data.isAfter(dataFinal());
    }
}
