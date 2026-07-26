package br.com.trilhaaprovacao.automacao.infraestrutura;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class MetricasDaAutomacao {
    private final Counter operacoesPreparadas;
    private final Counter primeirasConfirmacoesReforcadas;
    private final Counter operacoesAplicadas;
    private final Counter confirmacoesRecebidas;
    private final Counter confirmacoesRejeitadas;
    private final Counter confirmacoesIdempotentes;
    private final Counter confirmacoesExpiradas;
    private final Counter divergenciasDaConfirmacao;
    private final Counter falhasDaConfirmacao;

    public MetricasDaAutomacao(MeterRegistry registro) {
        operacoesPreparadas = Counter.builder(
                "trilha.automacao.operacoes.preparadas").register(registro);
        primeirasConfirmacoesReforcadas = Counter.builder(
                "trilha.automacao.confirmacoes.reforcadas.primeira_etapa")
                .register(registro);
        operacoesAplicadas = Counter.builder(
                "trilha.automacao.operacoes.aplicadas").register(registro);
        confirmacoesRecebidas = Counter.builder(
                "trilha.automacao.confirmacoes.recebidas").register(registro);
        confirmacoesRejeitadas = Counter.builder(
                "trilha.automacao.confirmacoes.rejeitadas").register(registro);
        confirmacoesIdempotentes = Counter.builder(
                "trilha.automacao.confirmacoes.idempotentes").register(registro);
        confirmacoesExpiradas = Counter.builder(
                "trilha.automacao.confirmacoes.expiradas").register(registro);
        divergenciasDaConfirmacao = Counter.builder(
                "trilha.automacao.confirmacoes.divergencias").register(registro);
        falhasDaConfirmacao = Counter.builder(
                "trilha.automacao.confirmacoes.falhas").register(registro);
    }

    public void registrarPreparacao() { incrementarAposCommit(operacoesPreparadas); }
    public void registrarPrimeiraConfirmacaoReforcada() {
        incrementarAposCommit(primeirasConfirmacoesReforcadas);
    }
    public void registrarAplicacao() { incrementarAposCommit(operacoesAplicadas); }
    public void registrarConfirmacaoRecebida() {
        incrementarAposCommit(confirmacoesRecebidas);
    }
    public void registrarConfirmacaoRejeitada() {
        incrementarAposCommit(confirmacoesRejeitadas);
    }
    public void registrarConfirmacaoIdempotente() {
        incrementarAposCommit(confirmacoesIdempotentes);
    }
    public void registrarConfirmacaoExpirada() {
        incrementarAposCommit(confirmacoesExpiradas);
    }
    public void registrarDivergenciaDaConfirmacao() {
        incrementarAposCommit(divergenciasDaConfirmacao);
    }
    public void registrarFalhaDaConfirmacao() {
        incrementarAposCommit(falhasDaConfirmacao);
    }

    private void incrementarAposCommit(Counter contador) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            contador.increment();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        contador.increment();
                    }
                });
    }
}
