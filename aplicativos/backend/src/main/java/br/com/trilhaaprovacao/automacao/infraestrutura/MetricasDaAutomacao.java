package br.com.trilhaaprovacao.automacao.infraestrutura;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class MetricasDaAutomacao {
    private final Counter operacoesPreparadas;
    private final Counter primeirasConfirmacoesReforcadas;
    private final Counter operacoesAplicadas;

    public MetricasDaAutomacao(MeterRegistry registro) {
        operacoesPreparadas = Counter.builder(
                "trilha.automacao.operacoes.preparadas").register(registro);
        primeirasConfirmacoesReforcadas = Counter.builder(
                "trilha.automacao.confirmacoes.reforcadas.primeira_etapa")
                .register(registro);
        operacoesAplicadas = Counter.builder(
                "trilha.automacao.operacoes.aplicadas").register(registro);
    }

    public void registrarPreparacao() { operacoesPreparadas.increment(); }
    public void registrarPrimeiraConfirmacaoReforcada() {
        primeirasConfirmacoesReforcadas.increment();
    }
    public void registrarAplicacao() { operacoesAplicadas.increment(); }
}
