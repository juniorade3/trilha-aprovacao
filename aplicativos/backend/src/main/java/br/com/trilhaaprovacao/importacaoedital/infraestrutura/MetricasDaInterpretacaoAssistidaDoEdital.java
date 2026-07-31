package br.com.trilhaaprovacao.importacaoedital.infraestrutura;

import br.com.trilhaaprovacao.importacaoedital.aplicacao.ResultadoDaInterpretacaoAssistidaDoEdital.UsoDaInterpretacaoAssistida;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class MetricasDaInterpretacaoAssistidaDoEdital {
    private final Counter sucessos;
    private final Counter falhas;
    private final Timer duracao;
    private final DistributionSummary tokensDeEntrada;
    private final DistributionSummary tokensDeSaida;
    private final DistributionSummary totalDeTokens;

    public MetricasDaInterpretacaoAssistidaDoEdital(MeterRegistry registro) {
        sucessos = Counter.builder(
                "trilha.importacao_edital.interpretacao_assistida.sucessos")
                .register(registro);
        falhas = Counter.builder(
                "trilha.importacao_edital.interpretacao_assistida.falhas")
                .register(registro);
        duracao = Timer.builder(
                "trilha.importacao_edital.interpretacao_assistida.duracao")
                .register(registro);
        tokensDeEntrada = resumoDeTokens(registro, "entrada");
        tokensDeSaida = resumoDeTokens(registro, "saida");
        totalDeTokens = resumoDeTokens(registro, "total");
    }

    public void registrarSucesso(Duration tempo,
            UsoDaInterpretacaoAssistida uso) {
        sucessos.increment();
        registrarDuracao(tempo);
        registrarTokens(uso);
    }

    public void registrarFalha(Duration tempo,
            UsoDaInterpretacaoAssistida uso) {
        falhas.increment();
        registrarDuracao(tempo);
        registrarTokens(uso);
    }

    private DistributionSummary resumoDeTokens(MeterRegistry registro,
            String tipo) {
        return DistributionSummary.builder(
                        "trilha.importacao_edital.interpretacao_assistida.tokens")
                .tag("tipo", tipo)
                .baseUnit("tokens")
                .register(registro);
    }

    private void registrarDuracao(Duration tempo) {
        duracao.record(tempo);
    }

    private void registrarTokens(UsoDaInterpretacaoAssistida uso) {
        if (uso == null) return;
        tokensDeEntrada.record(uso.tokensDeEntrada());
        tokensDeSaida.record(uso.tokensDeSaida());
        totalDeTokens.record(uso.totalDeTokens());
    }
}
