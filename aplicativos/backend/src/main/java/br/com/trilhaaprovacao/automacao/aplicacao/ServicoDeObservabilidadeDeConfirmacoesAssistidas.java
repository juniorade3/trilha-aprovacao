package br.com.trilhaaprovacao.automacao.aplicacao;

import br.com.trilhaaprovacao.automacao.dominio.EventoDeAuditoriaDaAutomacao;
import br.com.trilhaaprovacao.automacao.dominio.OperacaoAssistida;
import br.com.trilhaaprovacao.automacao.infraestrutura.EventoDeAuditoriaDaAutomacaoPersistido;
import br.com.trilhaaprovacao.automacao.infraestrutura.MetricasDaAutomacao;
import br.com.trilhaaprovacao.automacao.infraestrutura.RepositorioDeEventosDeAuditoriaDaAutomacao;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoDeObservabilidadeDeConfirmacoesAssistidas {
    private static final Logger LOG = LoggerFactory.getLogger(
            ServicoDeObservabilidadeDeConfirmacoesAssistidas.class);
    private final RepositorioDeEventosDeAuditoriaDaAutomacao auditoria;
    private final MetricasDaAutomacao metricas;
    private final ObjectMapper mapeador;
    private final TransactionTemplate novaTransacao;

    public enum Desfecho {
        REJEITADA,
        DIVERGENCIA,
        EXPIRADA,
        REFORCADA,
        REFORCADA_REPETIDA,
        APLICADA,
        IDEMPOTENTE,
        FALHA
    }

    public record Contexto(
            UUID identificadorDoUsuario,
            UUID identificadorDoVinculo,
            UUID identificadorDaOperacao,
            String tipo,
            UUID identificadorDeCorrelacao) {

        public static Contexto de(OperacaoAssistida operacao,
                UUID correlacao) {
            return new Contexto(operacao.identificadorDoUsuario(),
                    operacao.identificadorDoVinculo(),
                    operacao.identificador(), operacao.tipo(),
                    correlacao == null ? UUID.randomUUID() : correlacao);
        }
    }

    public ServicoDeObservabilidadeDeConfirmacoesAssistidas(
            RepositorioDeEventosDeAuditoriaDaAutomacao auditoria,
            MetricasDaAutomacao metricas, ObjectMapper mapeador,
            PlatformTransactionManager transacoes) {
        this.auditoria = auditoria;
        this.metricas = metricas;
        this.mapeador = mapeador;
        novaTransacao = new TransactionTemplate(transacoes);
        novaTransacao.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void registrarNoFluxo(Contexto contexto, Desfecho desfecho,
            String codigoDoResultado) {
        registrarNoFluxo(contexto, desfecho, codigoDoResultado,
                "GATEWAY_TELEGRAM", "GATEWAY", "CONFIRMACAO_RECEBIDA");
    }

    public void registrarPelaWebNoFluxo(Contexto contexto, Desfecho desfecho,
            String codigoDoResultado) {
        registrarNoFluxo(contexto, desfecho, codigoDoResultado,
                "USUARIO_WEB", "WEB", "CONFIRMACAO_WEB_RECEBIDA");
    }

    private void registrarNoFluxo(Contexto contexto, Desfecho desfecho,
            String codigoDoResultado, String ator, String fonte,
            String acaoDaRecepcao) {
        if (contexto.identificadorDoUsuario() != null) {
            salvar(contexto, ator, fonte, acaoDaRecepcao, "RECEBIDA", null);
            salvar(contexto, ator, fonte, acao(desfecho), resultado(desfecho),
                    codigoDoResultado);
        }
        registrarMetricas(desfecho);
        registrarLogAposCommit(contexto, desfecho, codigoDoResultado);
    }

    public void registrarDepoisDaConclusao(Contexto contexto,
            Desfecho desfecho, String codigoDoResultado) {
        registrarDepoisDaConclusao(contexto, desfecho, codigoDoResultado,
                false);
    }

    public void registrarPelaWebDepoisDaConclusao(Contexto contexto,
            Desfecho desfecho, String codigoDoResultado) {
        registrarDepoisDaConclusao(contexto, desfecho, codigoDoResultado,
                true);
    }

    private void registrarDepoisDaConclusao(Contexto contexto,
            Desfecho desfecho, String codigoDoResultado, boolean pelaWeb) {
        Runnable registro = () -> {
            try {
                novaTransacao.executeWithoutResult(estado -> {
                    if (pelaWeb) {
                        registrarPelaWebNoFluxo(contexto, desfecho,
                                codigoDoResultado);
                    } else {
                        registrarNoFluxo(contexto, desfecho,
                                codigoDoResultado);
                    }
                });
            } catch (RuntimeException falhaDaObservabilidade) {
                LOG.error("Falha observabilidade confirmacao. operacao={} "
                                + "vinculo={} tipo={} status={} correlacao={}",
                        contexto.identificadorDaOperacao(),
                        contexto.identificadorDoVinculo(), contexto.tipo(),
                        desfecho, contexto.identificadorDeCorrelacao());
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            registro.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        registro.run();
                    }
                });
    }

    public void registrarExpiracaoReconciliada(OperacaoAssistida operacao,
            UUID correlacao) {
        Contexto contexto = Contexto.de(operacao, correlacao);
        EventoDeAuditoriaDaAutomacao evento =
                EventoDeAuditoriaDaAutomacao.criar(
                        contexto.identificadorDoUsuario(),
                        contexto.identificadorDoVinculo(),
                        contexto.identificadorDaOperacao(),
                        "SISTEMA", null, "CONFIRMACAO_EXPIRADA",
                        null, null, "RECONCILIACAO", "EXPIRADA",
                        contexto.identificadorDeCorrelacao(),
                        metadados(contexto, "OPERACAO_VENCIDA_RECONCILIADA"),
                        OffsetDateTime.now(ZoneOffset.UTC));
        auditoria.save(new EventoDeAuditoriaDaAutomacaoPersistido(evento));
        metricas.registrarConfirmacaoExpirada();
        registrarLogAposCommit(contexto, Desfecho.EXPIRADA,
                "OPERACAO_VENCIDA_RECONCILIADA");
    }

    private void salvar(Contexto contexto, String ator, String fonte,
            String acao, String resultado, String codigoDoResultado) {
        EventoDeAuditoriaDaAutomacao evento =
                EventoDeAuditoriaDaAutomacao.criar(
                        contexto.identificadorDoUsuario(),
                        contexto.identificadorDoVinculo(),
                        contexto.identificadorDaOperacao(),
                        ator, null, acao, null, null, fonte, resultado,
                        contexto.identificadorDeCorrelacao(),
                        metadados(contexto, codigoDoResultado),
                        OffsetDateTime.now(ZoneOffset.UTC));
        auditoria.save(new EventoDeAuditoriaDaAutomacaoPersistido(evento));
    }

    private String metadados(Contexto contexto, String codigoDoResultado) {
        Map<String, Object> dados = new LinkedHashMap<>();
        if (contexto.tipo() != null) dados.put("tipo", contexto.tipo());
        if (codigoDoResultado != null) {
            dados.put("codigoDoResultado", codigoDoResultado);
        }
        try {
            return mapeador.writeValueAsString(dados);
        } catch (Exception excecao) {
            throw new IllegalStateException(excecao);
        }
    }

    private void registrarMetricas(Desfecho desfecho) {
        metricas.registrarConfirmacaoRecebida();
        switch (desfecho) {
            case REJEITADA -> metricas.registrarConfirmacaoRejeitada();
            case DIVERGENCIA -> {
                metricas.registrarConfirmacaoRejeitada();
                metricas.registrarDivergenciaDaConfirmacao();
            }
            case EXPIRADA -> metricas.registrarConfirmacaoExpirada();
            case REFORCADA -> metricas.registrarPrimeiraConfirmacaoReforcada();
            case REFORCADA_REPETIDA, IDEMPOTENTE ->
                    metricas.registrarConfirmacaoIdempotente();
            case APLICADA -> metricas.registrarAplicacao();
            case FALHA -> metricas.registrarFalhaDaConfirmacao();
        }
    }

    private void registrarLogAposCommit(Contexto contexto,
            Desfecho desfecho, String codigoDoResultado) {
        Runnable registro = () -> {
            String formato = "Confirmacao processada. operacao={} vinculo={} "
                    + "tipo={} status={} correlacao={} codigoResultado={}";
            if (desfecho == Desfecho.FALHA) {
                LOG.error(formato, contexto.identificadorDaOperacao(),
                        contexto.identificadorDoVinculo(), contexto.tipo(),
                        desfecho, contexto.identificadorDeCorrelacao(),
                        codigoDoResultado);
            } else if (desfecho == Desfecho.REJEITADA
                    || desfecho == Desfecho.DIVERGENCIA
                    || desfecho == Desfecho.EXPIRADA) {
                LOG.warn(formato, contexto.identificadorDaOperacao(),
                        contexto.identificadorDoVinculo(), contexto.tipo(),
                        desfecho, contexto.identificadorDeCorrelacao(),
                        codigoDoResultado);
            } else {
                LOG.info(formato, contexto.identificadorDaOperacao(),
                        contexto.identificadorDoVinculo(), contexto.tipo(),
                        desfecho, contexto.identificadorDeCorrelacao(),
                        codigoDoResultado);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            registro.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        registro.run();
                    }
                });
    }

    private String acao(Desfecho desfecho) {
        return switch (desfecho) {
            case REJEITADA, DIVERGENCIA -> "CONFIRMACAO_REJEITADA";
            case EXPIRADA -> "CONFIRMACAO_EXPIRADA";
            case REFORCADA -> "CONFIRMACAO_REFORCADA_SOLICITADA";
            case REFORCADA_REPETIDA -> "CONFIRMACAO_REFORCADA_REPETIDA";
            case APLICADA -> "OPERACAO_ASSISTIDA_APLICADA";
            case IDEMPOTENTE -> "CONFIRMACAO_IDEMPOTENTE";
            case FALHA -> "APLICACAO_DA_OPERACAO_FALHOU";
        };
    }

    private String resultado(Desfecho desfecho) {
        return switch (desfecho) {
            case REJEITADA, DIVERGENCIA -> "REJEITADA";
            case EXPIRADA -> "EXPIRADA";
            case REFORCADA -> "REFORCADA";
            case REFORCADA_REPETIDA, IDEMPOTENTE -> "IDEMPOTENTE";
            case APLICADA -> "APLICADA";
            case FALHA -> "FALHA";
        };
    }
}
