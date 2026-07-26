package br.com.trilhaaprovacao.automacao.aplicacao;

import br.com.trilhaaprovacao.automacao.infraestrutura.RepositorioDeOperacoesAssistidas;
import br.com.trilhaaprovacao.automacao.infraestrutura.ServicoDeSegredosDaAutomacao;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(prefix = "trilha.automacao", name = "habilitada",
        havingValue = "true")
public class ReconciliadorDeHashesDeConfirmacoesReforcadas
        implements ApplicationRunner {
    private static final int TAMANHO_DO_LOTE = 100;
    private final RepositorioDeOperacoesAssistidas operacoes;
    private final ServicoDeSegredosDaAutomacao segredos;
    private final TransactionTemplate transacao;

    public ReconciliadorDeHashesDeConfirmacoesReforcadas(
            RepositorioDeOperacoesAssistidas operacoes,
            ServicoDeSegredosDaAutomacao segredos,
            PlatformTransactionManager transacoes) {
        this.operacoes = operacoes;
        this.segredos = segredos;
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
        var pendentes = operacoes.encontrarHashesAnterioresAusentes(
                PageRequest.of(0, TAMANHO_DO_LOTE));
        pendentes.forEach(persistida -> {
            String primeiroCodigo = segredos.derivarCodigoDeConfirmacao(
                    persistida.paraDominio().assinatura());
            persistida.definirCodigoDeConfirmacaoAnteriorHash(
                    segredos.hash(primeiroCodigo));
        });
        operacoes.saveAllAndFlush(pendentes);
        return pendentes.size();
    }
}
