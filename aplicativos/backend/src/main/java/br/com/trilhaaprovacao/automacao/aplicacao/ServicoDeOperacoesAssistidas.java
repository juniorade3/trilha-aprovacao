package br.com.trilhaaprovacao.automacao.aplicacao;

import br.com.trilhaaprovacao.automacao.dominio.EventoDeAuditoriaDaAutomacao;
import br.com.trilhaaprovacao.automacao.dominio.EstadoDaOperacaoAssistida;
import br.com.trilhaaprovacao.automacao.dominio.EstadoDoVinculoDeCanal;
import br.com.trilhaaprovacao.automacao.dominio.OperacaoAssistida;
import br.com.trilhaaprovacao.automacao.infraestrutura.EventoDeAuditoriaDaAutomacaoPersistido;
import br.com.trilhaaprovacao.automacao.infraestrutura.OperacaoAssistidaPersistida;
import br.com.trilhaaprovacao.automacao.infraestrutura.RepositorioDeEventosDeAuditoriaDaAutomacao;
import br.com.trilhaaprovacao.automacao.infraestrutura.RepositorioDeOperacoesAssistidas;
import br.com.trilhaaprovacao.automacao.infraestrutura.RepositorioDeVinculosDeCanal;
import br.com.trilhaaprovacao.automacao.infraestrutura.ServicoDeSegredosDaAutomacao;
import br.com.trilhaaprovacao.automacao.infraestrutura.MetricasDaAutomacao;
import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoDeOperacoesAssistidas {
    private static final String VERSAO_DO_CONTRATO = "1";
    private static final String NIVEL_DE_CONFIRMACAO_COMUM = "COMUM";
    private static final String NIVEL_DE_CONFIRMACAO_REFORCADA = "REFORCADA";
    private static final Duration VALIDADE_MAXIMA = Duration.ofMinutes(30);
    private final RepositorioDeOperacoesAssistidas operacoes;
    private final RepositorioDeVinculosDeCanal vinculos;
    private final RepositorioDeEventosDeAuditoriaDaAutomacao auditoria;
    private final ServicoDeSegredosDaAutomacao segredos;
    private final ObjectMapper mapeador;
    private final MetricasDaAutomacao metricas;

    public record OperacaoPreparada(
            OperacaoAssistida operacao, String codigoDeConfirmacao) { }

    public ServicoDeOperacoesAssistidas(
            RepositorioDeOperacoesAssistidas operacoes,
            RepositorioDeVinculosDeCanal vinculos,
            RepositorioDeEventosDeAuditoriaDaAutomacao auditoria,
            ServicoDeSegredosDaAutomacao segredos,
            ObjectMapper mapeador, MetricasDaAutomacao metricas) {
        this.operacoes = operacoes;
        this.vinculos = vinculos;
        this.auditoria = auditoria;
        this.segredos = segredos;
        this.mapeador = mapeador;
        this.metricas = metricas;
    }

    @Transactional
    public OperacaoAssistida preparar(UUID usuario, UUID vinculo, String tipo,
            String resumo, String propostaCanonica, String versoesConsultadas,
            String chaveDeIdempotencia) {
        return preparar(usuario, vinculo, tipo, resumo, propostaCanonica,
                versoesConsultadas, chaveDeIdempotencia,
                NIVEL_DE_CONFIRMACAO_COMUM);
    }

    private OperacaoAssistida preparar(UUID usuario, UUID vinculo, String tipo,
            String resumo, String propostaCanonica, String versoesConsultadas,
            String chaveDeIdempotencia, String nivelDeConfirmacao) {
        String chave = validarChaveDeIdempotencia(chaveDeIdempotencia);
        String tipoNormalizado = tipo == null
                ? null : tipo.trim().toUpperCase(Locale.ROOT);
        String resumoNormalizado = resumo == null ? null : resumo.trim();
        String proposta = validarObjetoJson(propostaCanonica, "PROPOSTA_INVALIDA");
        String versoes = validarObjetoJson(
                versoesConsultadas, "VERSOES_CONSULTADAS_INVALIDAS");
        OffsetDateTime agora = agora();
        OffsetDateTime expiracao = agora.plus(VALIDADE_MAXIMA);
        String hashDaRequisicao = hashDaRequisicao(usuario, vinculo, tipoNormalizado,
                resumoNormalizado, proposta, versoes, nivelDeConfirmacao);
        operacoes.bloquearChaveDeIdempotencia(usuario, chave);
        var existente = operacoes
                .findByIdentificadorDoUsuarioAndChaveDeIdempotencia(
                        usuario, chave);
        if (existente.isPresent()) {
            OperacaoAssistida anterior = existente.get().paraDominio();
            if (anterior.mesmaRequisicao(hashDaRequisicao)) {
                return anterior;
            }
            throw new ConflitoDeDominio("CHAVE_DE_IDEMPOTENCIA_REUTILIZADA",
                    "A chave de idempotencia ja foi usada com outros dados.");
        }
        if (vinculo != null) {
            var vinculoEncontrado = vinculos
                    .findByIdentificadorAndIdentificadorDoUsuario(vinculo, usuario)
                    .orElseThrow(() -> new RecursoNaoEncontrado(
                            "VINCULO_DO_TELEGRAM_NAO_ENCONTRADO",
                            "Vinculo do Telegram nao encontrado."));
            if (vinculoEncontrado.estado() != EstadoDoVinculoDeCanal.ATIVO) {
                throw new RegraDeDominio("VINCULO_DO_TELEGRAM_INATIVO",
                        "Ative o vinculo do Telegram antes de preparar a operacao.");
            }
        }

        String assinatura = assinatura(
                usuario, vinculo, tipoNormalizado, proposta, versoes, expiracao,
                nivelDeConfirmacao);
        OperacaoAssistida operacao;
        try {
            operacao = OperacaoAssistida.preparar(usuario, vinculo,
                    tipoNormalizado, resumoNormalizado,
                    proposta, assinatura, versoes, chave,
                    hashDaRequisicao, expiracao, agora);
        } catch (IllegalArgumentException excecao) {
            throw new RegraDeDominio("OPERACAO_ASSISTIDA_INVALIDA",
                    excecao.getMessage());
        }
        OperacaoAssistida salva = operacoes.saveAndFlush(
                new OperacaoAssistidaPersistida(operacao)).paraDominio();
        metricas.registrarPreparacao();
        auditar(salva, "OPERACAO_PREPARADA", hashDaRequisicao, "SUCESSO", agora);
        return salva;
    }

    @Transactional
    public OperacaoPreparada prepararParaConfirmacao(UUID usuario, UUID vinculo,
            String tipo, String resumo, String propostaCanonica,
            String versoesConsultadas, String chaveDeIdempotencia) {
        return prepararParaConfirmacao(usuario, vinculo, tipo, resumo,
                propostaCanonica, versoesConsultadas, chaveDeIdempotencia,
                NIVEL_DE_CONFIRMACAO_COMUM);
    }

    @Transactional
    public OperacaoPreparada prepararParaConfirmacaoReforcada(UUID usuario,
            UUID vinculo, String tipo, String resumo, String propostaCanonica,
            String versoesConsultadas, String chaveDeIdempotencia) {
        return prepararParaConfirmacao(usuario, vinculo, tipo, resumo,
                propostaCanonica, versoesConsultadas, chaveDeIdempotencia,
                NIVEL_DE_CONFIRMACAO_REFORCADA);
    }

    private OperacaoPreparada prepararParaConfirmacao(UUID usuario,
            UUID vinculo, String tipo, String resumo, String propostaCanonica,
            String versoesConsultadas, String chaveDeIdempotencia,
            String nivel) {
        OperacaoAssistida operacao = preparar(usuario, vinculo, tipo, resumo,
                propostaCanonica, versoesConsultadas, chaveDeIdempotencia,
                nivel);
        String codigo = null;
        if (operacao.estado() == EstadoDaOperacaoAssistida.PREPARADA) {
            OffsetDateTime agora = agora();
            codigo = segredos.derivarCodigoDeConfirmacao(
                    operacao.assinatura());
            operacao.aguardarConfirmacao(agora);
            OperacaoAssistidaPersistida persistida = operacoes
                    .encontrarParaAtualizacao(operacao.identificador(), usuario)
                    .orElseThrow();
            persistida.atualizarDe(operacao);
            persistida.definirConfirmacao(segredos.hash(codigo),
                    codigo.substring(0, 2),
                    segredos.hash("nonce:" + operacao.assinatura()),
                    operacao.expiraEm(), nivel, 0);
            operacoes.saveAndFlush(persistida);
            auditar(operacao, "CONFIRMACAO_SOLICITADA",
                    segredos.hash(operacao.identificador().toString()),
                    "SUCESSO", agora);
        } else if (operacao.estado()
                == EstadoDaOperacaoAssistida.AGUARDANDO_CONFIRMACAO) {
            OffsetDateTime agora = agora();
            if (!agora.isBefore(operacao.expiraEm())) {
                OperacaoAssistidaPersistida persistida = operacoes
                        .encontrarParaAtualizacao(
                                operacao.identificador(), usuario)
                        .orElseThrow();
                operacao.expirar(agora);
                persistida.atualizarDe(operacao);
                operacoes.saveAndFlush(persistida);
                metricas.registrarConfirmacaoExpirada();
                auditar(operacao, "CONFIRMACAO_EXPIRADA",
                        segredos.hash(operacao.identificador().toString()),
                        "EXPIRADA", agora);
            } else {
                codigo = segredos.derivarCodigoDeConfirmacao(
                        operacao.assinatura());
            }
        }
        return new OperacaoPreparada(operacao, codigo);
    }

    @Transactional(readOnly = true)
    public Page<OperacaoAssistida> listar(UUID usuario, int pagina, int tamanho) {
        var paginacao = PageRequest.of(pagina, tamanho,
                Sort.by("criadoEm").descending().and(
                        Sort.by("identificador").descending()));
        return operacoes.findByIdentificadorDoUsuario(usuario, paginacao)
                .map(OperacaoAssistidaPersistida::paraDominio);
    }

    @Transactional(readOnly = true)
    public OperacaoAssistida obter(UUID usuario, UUID identificador) {
        return operacoes.findByIdentificadorAndIdentificadorDoUsuario(
                        identificador, usuario)
                .map(OperacaoAssistidaPersistida::paraDominio)
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "OPERACAO_ASSISTIDA_NAO_ENCONTRADA",
                        "Operacao assistida nao encontrada."));
    }

    @Transactional
    public OperacaoAssistida validarAtualidade(UUID usuario, UUID identificador,
            String assinaturaInformada, String versoesAtuais) {
        return validarAtualidade(usuario, identificador, assinaturaInformada,
                versoesAtuais, NIVEL_DE_CONFIRMACAO_COMUM);
    }

    @Transactional
    public OperacaoAssistida validarAtualidade(UUID usuario, UUID identificador,
            String assinaturaInformada, String versoesAtuais,
            String nivelDeConfirmacao) {
        OperacaoAssistida operacao = operacoes.encontrarParaAtualizacao(
                        identificador, usuario)
                .map(OperacaoAssistidaPersistida::paraDominio)
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "OPERACAO_ASSISTIDA_NAO_ENCONTRADA",
                        "Operacao assistida nao encontrada."));
        String versoes = validarObjetoJson(
                versoesAtuais, "VERSOES_CONSULTADAS_INVALIDAS");
        String proposta = validarObjetoJson(
                operacao.propostaCanonica(), "PROPOSTA_PERSISTIDA_INVALIDA");
        OffsetDateTime agora = agora();
        if (!agora.isBefore(operacao.expiraEm())
                || operacao.estado() == EstadoDaOperacaoAssistida.APLICADA
                || operacao.estado() == EstadoDaOperacaoAssistida.CANCELADA
                || operacao.estado() == EstadoDaOperacaoAssistida.EXPIRADA
                || operacao.estado() == EstadoDaOperacaoAssistida.FALHOU) {
            throw new ConflitoDeDominio("OPERACAO_ASSISTIDA_INDISPONIVEL",
                    "A operacao expirou ou ja foi finalizada.");
        }
        String assinaturaAtual = assinatura(operacao.identificadorDoUsuario(),
                operacao.identificadorDoVinculo(), operacao.tipo(),
                proposta, versoes, operacao.expiraEm(), nivelDeConfirmacao);
        if (!segredos.corresponde(operacao.assinatura(), assinaturaInformada)
                || !segredos.corresponde(operacao.assinatura(), assinaturaAtual)) {
            throw new ConflitoDeDominio("PREVIA_DE_AUTOMACAO_DESATUALIZADA",
                    "Os dados mudaram. Recalcule e confirme a nova proposta.");
        }
        return operacao;
    }

    private String assinatura(UUID usuario, UUID vinculo, String tipo,
            String proposta, String versoes, OffsetDateTime expiraEm,
            String nivelDeConfirmacao) {
        return segredos.hash("assinatura\n" + VERSAO_DO_CONTRATO + "\n" + usuario
                + "\n" + String.valueOf(vinculo) + "\n" + tipo + "\n"
                + proposta + "\n" + versoes + "\n" + expiraEm + "\n"
                + nivelDeConfirmacao);
    }

    private String hashDaRequisicao(UUID usuario, UUID vinculo, String tipo,
            String resumo, String proposta, String versoes,
            String nivelDeConfirmacao) {
        return segredos.hash("requisicao\n" + VERSAO_DO_CONTRATO + "\n" + usuario
                + "\n" + String.valueOf(vinculo) + "\n" + tipo + "\n" + resumo
                + "\n" + proposta + "\n" + versoes + "\n"
                + nivelDeConfirmacao);
    }

    private String validarObjetoJson(String json, String codigo) {
        try {
            Object valor = mapeador.readValue(json, Object.class);
            if (!(valor instanceof Map<?, ?>)) {
                throw new IllegalArgumentException();
            }
            return mapeador.writeValueAsString(ordenar(valor));
        } catch (Exception excecao) {
            throw new RegraDeDominio(codigo, "O valor deve ser um objeto JSON valido.");
        }
    }

    private Object ordenar(Object valor) {
        if (valor instanceof Map<?, ?> mapa) {
            Map<String, Object> ordenado = new TreeMap<>();
            mapa.forEach((chave, item) -> ordenado.put(
                    String.valueOf(chave), ordenar(item)));
            return ordenado;
        }
        if (valor instanceof List<?> lista) {
            List<Object> ordenada = new ArrayList<>(lista.size());
            lista.forEach(item -> ordenada.add(ordenar(item)));
            return ordenada;
        }
        return valor;
    }

    private String validarChaveDeIdempotencia(String chave) {
        if (chave == null || chave.isBlank() || chave.trim().length() > 160) {
            throw new RegraDeDominio("CHAVE_DE_IDEMPOTENCIA_INVALIDA",
                    "A chave de idempotencia deve conter entre 1 e 160 caracteres.");
        }
        return chave.trim();
    }

    private void auditar(OperacaoAssistida operacao, String acao, String hash,
            String resultado, OffsetDateTime agora) {
        EventoDeAuditoriaDaAutomacao evento = EventoDeAuditoriaDaAutomacao.criar(
                operacao.identificadorDoUsuario(), operacao.identificadorDoVinculo(),
                operacao.identificador(), "SISTEMA", null, acao, hash, null,
                "APLICACAO", resultado, UUID.randomUUID(), "{}", agora);
        auditoria.save(new EventoDeAuditoriaDaAutomacaoPersistido(evento));
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
