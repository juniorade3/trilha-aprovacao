package br.com.trilhaaprovacao.automacao.aplicacao;

import br.com.trilhaaprovacao.automacao.dominio.EstadoDaOperacaoAssistida;
import br.com.trilhaaprovacao.automacao.dominio.CanalDeIntegracao;
import br.com.trilhaaprovacao.automacao.dominio.EstadoDoVinculoDeCanal;
import br.com.trilhaaprovacao.automacao.dominio.OperacaoAssistida;
import br.com.trilhaaprovacao.automacao.infraestrutura.OperacaoAssistidaPersistida;
import br.com.trilhaaprovacao.automacao.infraestrutura.RepositorioDeOperacoesAssistidas;
import br.com.trilhaaprovacao.automacao.infraestrutura.RepositorioDeVinculosDeCanal;
import br.com.trilhaaprovacao.automacao.infraestrutura.ServicoDeSegredosDaAutomacao;
import br.com.trilhaaprovacao.automacao.infraestrutura.VinculoDeCanalPersistido;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeObservabilidadeDeConfirmacoesAssistidas.Contexto;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeObservabilidadeDeConfirmacoesAssistidas.Desfecho;
import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.estudos.aplicacao.ServicoDeMateriaisEEstudos;
import br.com.trilhaaprovacao.estudos.dominio.TipoDeEstudo;
import br.com.trilhaaprovacao.concursos.aplicacao.ServicoDaEstruturaDeConcursos;
import br.com.trilhaaprovacao.evidencias.aplicacao.DadosDaEvidencia;
import br.com.trilhaaprovacao.evidencias.dominio.DadosDoPadraoDeErro;
import br.com.trilhaaprovacao.planejamento.aplicacao.DisponibilidadeInformada;
import br.com.trilhaaprovacao.planejamento.aplicacao.PrioridadeDeMateriaInformada;
import br.com.trilhaaprovacao.planejamento.aplicacao.ServicoDeGeracaoDeterministica;
import br.com.trilhaaprovacao.planejamento.aplicacao.ServicoDePlanejamento;
import br.com.trilhaaprovacao.planejamento.aplicacao.ServicoDeReplanejamento;
import br.com.trilhaaprovacao.planejamento.dominio.ConfiguracaoDaGeracaoDeterministica;
import br.com.trilhaaprovacao.planejamento.dominio.PrioridadeDaMateriaNoPlano;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoDeAplicacaoDeOperacoesAssistidas {
    private static final Pattern CODIGO = Pattern.compile(
            "^[23456789A-HJ-NP-Z]{8}$");
    private static final List<EstadoDaOperacaoAssistida> ESTADOS_CONFIRMAVEIS =
            List.of(EstadoDaOperacaoAssistida.AGUARDANDO_CONFIRMACAO,
                    EstadoDaOperacaoAssistida.APLICADA);
    private static final TypeReference<Map<String, Object>> MAPA =
            new TypeReference<>() { };
    private final RepositorioDeOperacoesAssistidas operacoes;
    private final RepositorioDeVinculosDeCanal vinculos;
    private final ServicoDeOperacoesAssistidas servicoDeOperacoes;
    private final ServicoDePreparacoesMcp preparacoes;
    private final ServicoDeSegredosDaAutomacao segredos;
    private final ServicoDeMateriaisEEstudos estudos;
    private final ServicoDePlanejamento planejamento;
    private final ServicoDeGeracaoDeterministica geracao;
    private final ServicoDeReplanejamento replanejamento;
    private final ServicoDeCadastroAssistidoDeConcursos cadastroDeConcursos;
    private final ServicoDaEstruturaDeConcursos estruturaDeConcursos;
    private final ObjectMapper mapeador;
    private final ServicoDeObservabilidadeDeConfirmacoesAssistidas observabilidade;

    public record ResultadoDaConfirmacao(
            OperacaoAssistida operacao, String proximoCodigo) {
        public boolean exigeNovaConfirmacao() { return proximoCodigo != null; }
    }

    public ServicoDeAplicacaoDeOperacoesAssistidas(
            RepositorioDeOperacoesAssistidas operacoes,
            RepositorioDeVinculosDeCanal vinculos,
            ServicoDeOperacoesAssistidas servicoDeOperacoes,
            ServicoDePreparacoesMcp preparacoes,
            ServicoDeSegredosDaAutomacao segredos,
            ServicoDeMateriaisEEstudos estudos,
            ServicoDePlanejamento planejamento,
            ServicoDeGeracaoDeterministica geracao,
            ServicoDeReplanejamento replanejamento,
            ServicoDeCadastroAssistidoDeConcursos cadastroDeConcursos,
            ServicoDaEstruturaDeConcursos estruturaDeConcursos,
            ObjectMapper mapeador,
            ServicoDeObservabilidadeDeConfirmacoesAssistidas observabilidade) {
        this.operacoes = operacoes;
        this.vinculos = vinculos;
        this.servicoDeOperacoes = servicoDeOperacoes;
        this.preparacoes = preparacoes;
        this.segredos = segredos;
        this.estudos = estudos;
        this.planejamento = planejamento;
        this.geracao = geracao;
        this.replanejamento = replanejamento;
        this.cadastroDeConcursos = cadastroDeConcursos;
        this.estruturaDeConcursos = estruturaDeConcursos;
        this.mapeador = mapeador;
        this.observabilidade = observabilidade;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE,
            noRollbackFor = ConfirmacaoExpirada.class)
    public OperacaoAssistida confirmarEAplicar(String codigo, String metodo,
            long bot, long telegram, long chat, String sessao, String update) {
        return confirmarComResultado(codigo, metodo, bot, telegram, chat,
                sessao, update).operacao();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE,
            noRollbackFor = ConfirmacaoExpirada.class)
    public ResultadoDaConfirmacao confirmarComResultado(String codigo,
            String metodo, long bot, long telegram, long chat, String sessao,
            String update) {
        return confirmarComResultado(codigo, metodo, bot, telegram, chat,
                sessao, update, UUID.randomUUID());
    }

    @Transactional(isolation = Isolation.SERIALIZABLE,
            noRollbackFor = ConfirmacaoExpirada.class)
    public ResultadoDaConfirmacao confirmarComResultado(String codigo,
            String metodo, long bot, long telegram, long chat, String sessao,
            String update, UUID correlacao) {
        UUID correlacaoEfetiva = correlacao == null
                ? UUID.randomUUID() : correlacao;
        validarEntrada(codigo, metodo, sessao, update, correlacaoEfetiva);
        String normalizado = codigo.trim().toUpperCase();
        var vinculoExato = vinculos
                .findByCanalAndIdentificadorDoBotAndIdentificadorExternoAndIdentificadorDoChatAndEstado(
                        CanalDeIntegracao.TELEGRAM, bot, telegram, chat,
                        EstadoDoVinculoDeCanal.ATIVO);
        if (vinculoExato.isPresent()) {
            var vinculo = vinculoExato.get();
            List<OperacaoAssistidaPersistida> correspondentes =
                    resolverPorCodigo(Set.of(vinculo.identificador()),
                            normalizado);
            if (correspondentes.size() == 1) {
                return confirmarIdentificada(
                        correspondentes.getFirst().paraDominio()
                                .identificador(),
                        normalizado, metodo, bot, telegram, chat, sessao,
                        update, correlacaoEfetiva);
            }
            String motivo = correspondentes.isEmpty()
                    ? "CODIGO_DE_CONFIRMACAO_DIVERGENTE"
                    : "CODIGO_DE_CONFIRMACAO_AMBIGUO";
            observabilidade.registrarDepoisDaConclusao(
                    new Contexto(vinculo.identificadorDoUsuario(),
                            vinculo.identificador(), null, null,
                            correlacaoEfetiva),
                    Desfecho.DIVERGENCIA, motivo);
            throw operacaoNaoEncontrada();
        }

        Map<UUID, VinculoDeCanalPersistido> candidatos =
                candidatosDoContexto(bot, telegram, chat);
        List<OperacaoAssistidaPersistida> correspondentes =
                resolverPorCodigo(candidatos.keySet(), normalizado);
        if (correspondentes.size() == 1) {
            return confirmarIdentificada(
                    correspondentes.getFirst().paraDominio().identificador(),
                    normalizado, metodo, bot, telegram, chat, sessao, update,
                    correlacaoEfetiva);
        }
        String motivo = correspondentes.isEmpty()
                ? "VINCULO_AUSENTE" : "CODIGO_DE_CONFIRMACAO_AMBIGUO";
        observabilidade.registrarDepoisDaConclusao(
                new Contexto(null, null, null, null, correlacaoEfetiva),
                correspondentes.isEmpty()
                        ? Desfecho.REJEITADA : Desfecho.DIVERGENCIA,
                motivo);
        throw operacaoNaoEncontrada();
    }

    private Map<UUID, VinculoDeCanalPersistido> candidatosDoContexto(
            long bot, long telegram, long chat) {
        Map<UUID, VinculoDeCanalPersistido> candidatos =
                new LinkedHashMap<>();
        adicionar(candidatos, vinculos
                .findByCanalAndIdentificadorDoBotAndIdentificadorExternoAndIdentificadorDoChatOrderByCriadoEmDesc(
                        CanalDeIntegracao.TELEGRAM, bot, telegram, chat));
        vinculos
                .findByCanalAndIdentificadorDoBotAndIdentificadorExternoAndEstado(
                        CanalDeIntegracao.TELEGRAM, bot, telegram,
                        EstadoDoVinculoDeCanal.ATIVO)
                .ifPresent(vinculo ->
                        candidatos.put(vinculo.identificador(), vinculo));
        adicionar(candidatos, vinculos
                .findByCanalAndIdentificadorDoBotAndIdentificadorDoChatAndEstadoOrderByCriadoEmDesc(
                        CanalDeIntegracao.TELEGRAM, bot, chat,
                        EstadoDoVinculoDeCanal.ATIVO));
        adicionar(candidatos, vinculos
                .findByCanalAndIdentificadorExternoAndIdentificadorDoChatAndEstadoOrderByCriadoEmDesc(
                        CanalDeIntegracao.TELEGRAM, telegram, chat,
                        EstadoDoVinculoDeCanal.ATIVO));
        return candidatos;
    }

    private void adicionar(Map<UUID, VinculoDeCanalPersistido> destino,
            List<VinculoDeCanalPersistido> encontrados) {
        encontrados.forEach(vinculo ->
                destino.putIfAbsent(vinculo.identificador(), vinculo));
    }

    private List<OperacaoAssistidaPersistida> resolverPorCodigo(
            Set<UUID> identificadoresDosVinculos, String codigo) {
        if (identificadoresDosVinculos.isEmpty()) return List.of();
        return operacoes
                .encontrarPorVinculosECodigoDeConfirmacao(
                        identificadoresDosVinculos, segredos.hash(codigo),
                        ESTADOS_CONFIRMAVEIS);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE,
            noRollbackFor = ConfirmacaoExpirada.class)
    public OperacaoAssistida confirmarEAplicar(UUID identificador,
            String codigo, String metodo, long bot, long telegram, long chat,
            String sessao, String update) {
        return confirmarComResultado(identificador, codigo, metodo, bot,
                telegram, chat, sessao, update).operacao();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE,
            noRollbackFor = ConfirmacaoExpirada.class)
    public ResultadoDaConfirmacao confirmarComResultado(UUID identificador,
            String codigo, String metodo, long bot, long telegram, long chat,
            String sessao, String update) {
        return confirmarComResultado(identificador, codigo, metodo, bot,
                telegram, chat, sessao, update, UUID.randomUUID());
    }

    @Transactional(isolation = Isolation.SERIALIZABLE,
            noRollbackFor = ConfirmacaoExpirada.class)
    public ResultadoDaConfirmacao confirmarComResultado(UUID identificador,
            String codigo, String metodo, long bot, long telegram, long chat,
            String sessao, String update, UUID correlacao) {
        UUID correlacaoEfetiva = correlacao == null
                ? UUID.randomUUID() : correlacao;
        validarEntrada(codigo, metodo, sessao, update, correlacaoEfetiva);
        OperacaoAssistidaPersistida encontrada = operacoes.findById(identificador)
                .orElse(null);
        if (encontrada == null) {
            observabilidade.registrarDepoisDaConclusao(
                    new Contexto(null, null, identificador, null,
                            correlacaoEfetiva),
                    Desfecho.REJEITADA,
                    "OPERACAO_ASSISTIDA_NAO_ENCONTRADA");
            throw operacaoNaoEncontrada();
        }
        return confirmarIdentificada(identificador, codigo, metodo, bot,
                telegram, chat, sessao, update, correlacaoEfetiva);
    }

    private ResultadoDaConfirmacao confirmarIdentificada(UUID identificador,
            String codigo, String metodo, long bot, long telegram, long chat,
            String sessao, String update, UUID correlacao) {
        OperacaoAssistidaPersistida encontrada = operacoes.findById(identificador)
                .orElseThrow(this::operacaoNaoEncontrada);
        Contexto contexto = Contexto.de(encontrada.paraDominio(), correlacao);
        EstadoDaAplicacao estadoDaAplicacao = new EstadoDaAplicacao();
        try {
            return processarConfirmacao(identificador, codigo, metodo, bot,
                    telegram, chat, sessao, update, contexto,
                    estadoDaAplicacao);
        } catch (ConfirmacaoExpirada excecao) {
            throw excecao;
        } catch (RuntimeException excecao) {
            observabilidade.registrarDepoisDaConclusao(contexto,
                    classificar(excecao, estadoDaAplicacao.ativa),
                    codigoDaExcecao(excecao));
            throw excecao;
        }
    }

    private ResultadoDaConfirmacao processarConfirmacao(UUID identificador,
            String codigo, String metodo, long bot, long telegram, long chat,
            String sessao, String update, Contexto contexto,
            EstadoDaAplicacao estadoDaAplicacao) {
        OperacaoAssistidaPersistida persistida = operacoes
                .encontrarParaAtualizacao(identificador,
                        contexto.identificadorDoUsuario())
                .orElseThrow(this::operacaoNaoEncontrada);
        OperacaoAssistida operacao = persistida.paraDominio();
        if (operacao.identificadorDoVinculo() == null) {
            throw contextoDaConfirmacaoInvalido("VINCULO_AUSENTE");
        }
        var vinculo = vinculos.findByIdentificadorAndIdentificadorDoUsuario(
                        operacao.identificadorDoVinculo(),
                        operacao.identificadorDoUsuario())
                .orElseThrow(() ->
                        contextoDaConfirmacaoInvalido("VINCULO_AUSENTE"));
        if (vinculo.estado() != EstadoDoVinculoDeCanal.ATIVO) {
            throw contextoDaConfirmacaoInvalido("VINCULO_INATIVO");
        }
        if (!Long.valueOf(bot).equals(vinculo.identificadorDoBot())) {
            throw contextoDaConfirmacaoInvalido("BOT_DIVERGENTE");
        }
        if (!Long.valueOf(telegram).equals(vinculo.identificadorExterno())) {
            throw contextoDaConfirmacaoInvalido("TELEGRAM_DIVERGENTE");
        }
        if (!Long.valueOf(chat).equals(vinculo.identificadorDoChat())) {
            throw contextoDaConfirmacaoInvalido("CHAT_DIVERGENTE");
        }
        if (!sessao.equals(vinculo.identificadorDaSessao())) {
            throw contextoDaConfirmacaoInvalido("SESSAO_DIVERGENTE");
        }
        String informado = codigo.trim().toUpperCase();
        String hashInformado = segredos.hash(informado);
        boolean codigoAtual = persistida.codigoDeConfirmacaoHash() != null
                && segredos.corresponde(
                        persistida.codigoDeConfirmacaoHash(),
                        hashInformado);
        boolean primeiraEtapaRepetida =
                "REFORCADA".equals(persistida.nivelDeConfirmacao())
                && persistida.etapaDaConfirmacao() == 1
                && segredos.corresponde(
                        persistida.codigoDeConfirmacaoAnteriorHash(),
                        hashInformado);
        if (operacao.estado() == EstadoDaOperacaoAssistida.APLICADA) {
            if (!codigoAtual && !primeiraEtapaRepetida) {
                throw codigoDivergente();
            }
            observabilidade.registrarNoFluxo(contexto,
                    Desfecho.IDEMPOTENTE, "OPERACAO_JA_APLICADA");
            return new ResultadoDaConfirmacao(operacao, null);
        }
        if (operacao.estado()
                != EstadoDaOperacaoAssistida.AGUARDANDO_CONFIRMACAO) {
            throw new ConflitoDeDominio("OPERACAO_ASSISTIDA_INDISPONIVEL",
                    "A operacao nao esta disponivel para confirmacao.");
        }
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        if (persistida.confirmacaoExpiraEm() == null
                || !agora.isBefore(persistida.confirmacaoExpiraEm())) {
            operacao.expirar(agora);
            persistida.atualizarDe(operacao);
            operacoes.saveAndFlush(persistida);
            observabilidade.registrarNoFluxo(contexto,
                    Desfecho.EXPIRADA, "CONFIRMACAO_EXPIRADA");
            throw new ConfirmacaoExpirada();
        }
        if (!codigoAtual && !primeiraEtapaRepetida) {
            throw codigoDivergente();
        }
        Map<String, Object> proposta = mapa(operacao.propostaCanonica());
        Map<String, Object> atuais = preparacoes.versoesAtuais(operacao.tipo(),
                operacao.identificadorDoUsuario(), proposta);
        servicoDeOperacoes.validarAtualidade(operacao.identificadorDoUsuario(),
                operacao.identificador(), operacao.assinatura(), json(atuais));
        if (primeiraEtapaRepetida) {
            String segundoCodigo = segredos.derivarCodigoDeConfirmacao(
                    "segunda-etapa:" + operacao.assinatura());
            observabilidade.registrarNoFluxo(contexto,
                    Desfecho.REFORCADA_REPETIDA,
                    "PRIMEIRA_ETAPA_JA_CONFIRMADA");
            return new ResultadoDaConfirmacao(operacao, segundoCodigo);
        }
        if ("REFORCADA".equals(persistida.nivelDeConfirmacao())
                && persistida.etapaDaConfirmacao() == 0) {
            String segundoCodigo = segredos.derivarCodigoDeConfirmacao(
                    "segunda-etapa:" + operacao.assinatura());
            persistida.registrarContextoDaConfirmacao(metodo, bot, telegram,
                    chat, sessao, update);
            persistida.definirSegundaEtapaDaConfirmacao(
                    segredos.hash(segundoCodigo),
                    segundoCodigo.substring(0, 2),
                    segredos.hash("segunda-etapa:nonce:" + operacao.assinatura()),
                    operacao.expiraEm());
            operacoes.saveAndFlush(persistida);
            observabilidade.registrarNoFluxo(contexto,
                    Desfecho.REFORCADA, "SEGUNDA_ETAPA_SOLICITADA");
            return new ResultadoDaConfirmacao(operacao, segundoCodigo);
        }
        operacao.confirmar(operacao.assinatura(), agora);
        persistida.atualizarDe(operacao);
        persistida.registrarContextoDaConfirmacao(metodo, bot, telegram, chat,
                sessao, update);
        operacoes.saveAndFlush(persistida);
        estadoDaAplicacao.ativa = true;
        Map<String, Object> resultado = aplicar(operacao, proposta);
        operacao.aplicar(json(resultado), OffsetDateTime.now(ZoneOffset.UTC));
        persistida.atualizarDe(operacao);
        OperacaoAssistida aplicada = operacoes.saveAndFlush(
                persistida).paraDominio();
        observabilidade.registrarNoFluxo(Contexto.de(aplicada,
                        contexto.identificadorDeCorrelacao()),
                Desfecho.APLICADA, "OPERACAO_APLICADA");
        return new ResultadoDaConfirmacao(aplicada, null);
    }

    private static final class EstadoDaAplicacao {
        private boolean ativa;
    }

    private void validarEntrada(String codigo, String metodo, String sessao,
            String update, UUID correlacao) {
        if (codigo == null || !CODIGO.matcher(
                        codigo.trim().toUpperCase()).matches()
                || !List.of("BOTAO", "TEXTO", "VOZ").contains(metodo)
                || sessao == null || sessao.isBlank() || update == null
                || update.isBlank()) {
            observabilidade.registrarDepoisDaConclusao(
                    new Contexto(null, null, null, null, correlacao),
                    Desfecho.REJEITADA, "CONFIRMACAO_INVALIDA");
            throw new RegraDeDominio("CONFIRMACAO_INVALIDA",
                    "A confirmacao informada e invalida.");
        }
    }

    private RecursoNaoEncontrado operacaoNaoEncontrada() {
        return new RecursoNaoEncontrado(
                "OPERACAO_ASSISTIDA_NAO_ENCONTRADA",
                "Operacao assistida nao encontrada.");
    }

    private RecursoNaoEncontrado contextoDaConfirmacaoInvalido(String motivo) {
        return new ContextoDaConfirmacaoInvalido(motivo);
    }

    private ConflitoDeDominio codigoDivergente() {
        return new ConflitoDeDominio(
                "CODIGO_DE_CONFIRMACAO_DIVERGENTE",
                "O codigo de confirmacao divergiu.");
    }

    private Desfecho classificar(RuntimeException excecao,
            boolean aplicando) {
        if (aplicando) return Desfecho.FALHA;
        String codigo = codigoDaExcecao(excecao);
        if (codigo.contains("DIVERGENTE")
                || codigo.contains("DESATUALIZADA")
                || codigo.contains("CONCORRENTEMENTE")) {
            return Desfecho.DIVERGENCIA;
        }
        return Desfecho.REJEITADA;
    }

    private String codigoDaExcecao(RuntimeException excecao) {
        if (excecao instanceof ContextoDaConfirmacaoInvalido contexto) {
            return contexto.motivo();
        }
        if (excecao instanceof ConflitoDeDominio conflito) {
            return conflito.codigo();
        }
        if (excecao instanceof RegraDeDominio regra) {
            return regra.codigo();
        }
        if (excecao instanceof RecursoNaoEncontrado naoEncontrado) {
            return naoEncontrado.codigo();
        }
        if (excecao instanceof CannotSerializeTransactionException
                || excecao instanceof PessimisticLockingFailureException
                || excecao instanceof ObjectOptimisticLockingFailureException) {
            return "DADO_ALTERADO_CONCORRENTEMENTE";
        }
        return "FALHA_INTERNA";
    }

    private static final class ContextoDaConfirmacaoInvalido
            extends RecursoNaoEncontrado {
        private final String motivo;

        private ContextoDaConfirmacaoInvalido(String motivo) {
            super("OPERACAO_ASSISTIDA_NAO_ENCONTRADA",
                    "Operacao assistida nao encontrada.");
            this.motivo = motivo;
        }

        private String motivo() {
            return motivo;
        }
    }

    private Map<String, Object> aplicar(OperacaoAssistida operacao,
            Map<String, Object> proposta) {
        UUID usuario = operacao.identificadorDoUsuario();
        Object resultado = switch (operacao.tipo()) {
            case "REGISTRO_DE_ESTUDO" -> estudos.registrarEstudo(usuario,
                    uuid(proposta, "identificadorDoTopico"),
                    uuidOpcional(proposta, "identificadorDoMaterial"),
                    TipoDeEstudo.valueOf(texto(proposta, "tipoDeEstudo")),
                    OffsetDateTime.parse(texto(proposta, "dataHora")),
                    inteiro(proposta, "duracaoEmMinutos"),
                    textoOpcional(proposta, "observacao"),
                    evidencia(proposta), true);
            case "CONCLUSAO_DO_BLOCO" -> planejamento.concluirBloco(usuario,
                    uuid(proposta, "identificadorDoBloco"),
                    inteiro(proposta, "duracaoExecutadaEmMinutos"),
                    textoOpcional(proposta, "observacao"),
                    uuidOpcional(proposta, "identificadorDoTopico"),
                    evidencia(proposta));
            case "INTERRUPCAO_DO_BLOCO" -> planejamento.interromperBloco(usuario,
                    uuid(proposta, "identificadorDoBloco"),
                    inteiro(proposta, "duracaoExecutadaEmMinutos"),
                    textoOpcional(proposta, "observacao"),
                    uuidOpcional(proposta, "identificadorDoTopico"),
                    evidencia(proposta));
            case "CORRECAO_DO_ESTUDO" -> estudos.corrigirEstudo(usuario,
                    uuid(proposta, "identificadorDoEstudo"),
                    uuid(proposta, "identificadorDoTopico"),
                    uuidOpcional(proposta, "identificadorDoMaterial"),
                    TipoDeEstudo.valueOf(texto(proposta, "tipoDeEstudo")),
                    OffsetDateTime.parse(texto(proposta, "dataHora")),
                    inteiro(proposta, "duracaoEmMinutos"),
                    textoOpcional(proposta, "observacao"),
                    evidencia(proposta), true);
            case "GERACAO_DO_PLANO" -> geracao.aplicar(usuario,
                    uuid(proposta, "identificadorDoPlano"),
                    LocalDate.parse(texto(proposta, "dataDeReferencia")),
                    new ConfiguracaoDaGeracaoDeterministica(inteiro(proposta,
                            "duracaoDoBlocoPrincipalEmMinutos")),
                    booleano(proposta, "substituirBlocosGerados"),
                    texto(proposta, "assinaturaDaPrevia"));
            case "REPLANEJAMENTO" -> replanejamento.aplicar(usuario,
                    uuid(proposta, "identificadorDoPlano"),
                    LocalDate.parse(texto(proposta, "dataDeReferencia")),
                    uuids(proposta, "identificadoresDasPendenciasIgnoradas"),
                    uuids(proposta, "identificadoresDasConfirmacoesDoLimite"),
                    texto(proposta, "assinaturaDaPrevia"));
            case "ALTERACAO_DE_DISPONIBILIDADE" ->
                    planejamento.alterarDisponibilidades(usuario,
                            uuid(proposta, "identificadorDoPlano"),
                            lista(proposta, "disponibilidades").stream()
                                    .map(item -> new DisponibilidadeInformada(
                                            LocalDate.parse(texto(item, "data")),
                                            inteiro(item, "minutosDisponiveis")))
                                    .toList());
            case "ALTERACAO_DE_PRIORIDADES" -> geracao.substituirPrioridades(
                    usuario, uuid(proposta, "identificadorDoPlano"),
                    lista(proposta, "prioridades").stream()
                            .map(item -> new PrioridadeDeMateriaInformada(
                                    uuid(item, "identificadorDaMateria"),
                                    PrioridadeDaMateriaNoPlano.valueOf(
                                            texto(item, "prioridade"))))
                            .toList());
            case "CADASTRO_DO_CONCURSO", "CATALOGO_DE_CONTEUDOS",
                    "CONTEUDO_PROGRAMATICO", "MAPEAMENTOS_DO_EDITAL" ->
                    cadastroDeConcursos.aplicar(usuario, proposta);
            case "ATIVACAO_DO_CONCURSO" ->
                    estruturaDeConcursos.ativarConcurso(usuario,
                            uuid(proposta, "identificadorDoConcurso"));
            case "ARQUIVAMENTO_DO_CONCURSO" ->
                    estruturaDeConcursos.definirArquivamentoDoConcurso(usuario,
                            uuid(proposta, "identificadorDoConcurso"), true);
            case "CANCELAMENTO_DO_CONCURSO" ->
                    estruturaDeConcursos.cancelarConcurso(usuario,
                            uuid(proposta, "identificadorDoConcurso"));
            default -> throw new IllegalArgumentException("Operacao desconhecida.");
        };
        return Map.of("tipo", operacao.tipo(), "dados",
                mapeador.convertValue(resultado, Object.class));
    }

    private DadosDaEvidencia evidencia(Map<String, Object> proposta) {
        Object valor = proposta.get("evidencia");
        if (!(valor instanceof Map<?, ?> bruto)) return null;
        Map<String, Object> mapa = bruto.entrySet().stream().collect(
                Collectors.toMap(item -> item.getKey().toString(), Map.Entry::getValue));
        return new DadosDaEvidencia(inteiroOpcional(mapa,
                "quantidadeDeQuestoes"), inteiroOpcional(mapa,
                "quantidadeDeAcertos"), inteiroOpcional(mapa,
                "nivelDeRecordacao"), inteiroOpcional(mapa,
                "dificuldadePercebida"), listaOpcional(mapa, "padroesDeErro")
                .stream().map(item -> new DadosDoPadraoDeErro(
                        texto(item, "descricao"),
                        inteiro(item, "quantidadeDeOcorrencias"))).toList());
    }

    private Map<String, Object> mapa(String json) {
        try { return mapeador.readValue(json, MAPA); }
        catch (Exception excecao) { throw new IllegalStateException(excecao); }
    }
    private String json(Object valor) {
        try { return mapeador.writeValueAsString(valor); }
        catch (Exception excecao) { throw new IllegalStateException(excecao); }
    }
    private String texto(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave);
        if (valor == null || valor.toString().isBlank())
            throw new IllegalArgumentException(chave + " e obrigatorio.");
        return valor.toString();
    }
    private String textoOpcional(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave); return valor == null ? null : valor.toString();
    }
    private UUID uuid(Map<String, Object> mapa, String chave) {
        return UUID.fromString(texto(mapa, chave));
    }
    private UUID uuidOpcional(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave); return valor == null ? null
                : UUID.fromString(valor.toString());
    }
    private int inteiro(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave); return valor instanceof Number numero
                ? numero.intValue() : Integer.parseInt(texto(mapa, chave));
    }
    private Integer inteiroOpcional(Map<String, Object> mapa, String chave) {
        return mapa.get(chave) == null ? null : inteiro(mapa, chave);
    }
    private boolean booleano(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave); return valor instanceof Boolean b
                ? b : Boolean.parseBoolean(texto(mapa, chave));
    }
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lista(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave);
        if (!(valor instanceof List<?>)) throw new IllegalArgumentException(chave);
        return (List<Map<String, Object>>) valor;
    }
    private List<Map<String, Object>> listaOpcional(Map<String, Object> mapa,
            String chave) {
        return mapa.get(chave) == null ? List.of() : lista(mapa, chave);
    }
    private Set<UUID> uuids(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave);
        if (!(valor instanceof List<?> lista)) return Set.of();
        return lista.stream().map(Object::toString).map(UUID::fromString)
                .collect(Collectors.toSet());
    }
}
