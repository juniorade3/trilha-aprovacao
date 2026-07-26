package br.com.trilhaaprovacao.automacao.aplicacao;

import br.com.trilhaaprovacao.automacao.dominio.EstadoDaOperacaoAssistida;
import br.com.trilhaaprovacao.automacao.infraestrutura.RepositorioDeOperacoesAssistidas;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(prefix = "trilha.automacao", name = "habilitada",
        havingValue = "true")
public class ReconciliadorDeOperacoesAssistidasExpiradas
        implements ApplicationRunner {
    private static final int TAMANHO_DO_LOTE = 100;
    private static final List<EstadoDaOperacaoAssistida> ESTADOS_PENDENTES =
            List.of(EstadoDaOperacaoAssistida.PREPARADA,
                    EstadoDaOperacaoAssistida.AGUARDANDO_CONFIRMACAO,
                    EstadoDaOperacaoAssistida.CONFIRMADA);
    private final RepositorioDeOperacoesAssistidas operacoes;
    private final ServicoDeObservabilidadeDeConfirmacoesAssistidas observabilidade;
    private final TransactionTemplate transacao;

    public ReconciliadorDeOperacoesAssistidasExpiradas(
            RepositorioDeOperacoesAssistidas operacoes,
            ServicoDeObservabilidadeDeConfirmacoesAssistidas observabilidade,
            PlatformTransactionManager transacoes) {
        this.operacoes = operacoes;
        this.observabilidade = observabilidade;
        transacao = new TransactionTemplate(transacoes);
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        int quantidade;
        do {
            quantidade = transacao.execute(estado -> reconciliarLote());
        } while (quantidade == TAMANHO_DO_LOTE);
    }

    private int reconciliarLote() {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        UUID correlacao = UUID.randomUUID();
        var vencidas = operacoes
                .findTop100ByEstadoInAndExpiraEmLessThanEqualOrderByCriadoEmAsc(
                        ESTADOS_PENDENTES, agora);
        vencidas.forEach(persistida -> {
            var operacao = persistida.paraDominio();
            operacao.expirar(agora);
            persistida.atualizarDe(operacao);
            observabilidade.registrarExpiracaoReconciliada(
                    operacao, correlacao);
        });
        operacoes.saveAllAndFlush(vencidas);
        return vencidas.size();
    }
}
