package br.com.trilhaaprovacao.automacao.aplicacao;

import br.com.trilhaaprovacao.automacao.dominio.EstadoDaOperacaoAssistida;
import br.com.trilhaaprovacao.automacao.dominio.CanalDeIntegracao;
import br.com.trilhaaprovacao.automacao.dominio.EstadoDoVinculoDeCanal;
import br.com.trilhaaprovacao.automacao.dominio.OperacaoAssistida;
import br.com.trilhaaprovacao.automacao.infraestrutura.OperacaoAssistidaPersistida;
import br.com.trilhaaprovacao.automacao.infraestrutura.RepositorioDeOperacoesAssistidas;
import br.com.trilhaaprovacao.automacao.infraestrutura.RepositorioDeVinculosDeCanal;
import br.com.trilhaaprovacao.automacao.infraestrutura.ServicoDeSegredosDaAutomacao;
import br.com.trilhaaprovacao.automacao.infraestrutura.MetricasDaAutomacao;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoDeAplicacaoDeOperacoesAssistidas {
    private static final Pattern CODIGO = Pattern.compile(
            "^[23456789A-HJ-NP-Z]{8}$");
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
    private final MetricasDaAutomacao metricas;

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
            ObjectMapper mapeador, MetricasDaAutomacao metricas) {
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
        this.metricas = metricas;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public OperacaoAssistida confirmarEAplicar(String codigo, String metodo,
            long bot, long telegram, long chat, String sessao, String update) {
        return confirmarComResultado(codigo, metodo, bot, telegram, chat,
                sessao, update).operacao();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ResultadoDaConfirmacao confirmarComResultado(String codigo,
            String metodo, long bot, long telegram, long chat, String sessao,
            String update) {
        String normalizado = codigo == null ? "" : codigo.trim().toUpperCase();
        var vinculo = vinculos
                .findByCanalAndIdentificadorDoBotAndIdentificadorExternoAndIdentificadorDoChatAndEstado(
                        CanalDeIntegracao.TELEGRAM, bot, telegram, chat,
                        EstadoDoVinculoDeCanal.ATIVO)
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "OPERACAO_ASSISTIDA_NAO_ENCONTRADA",
                        "Operacao assistida nao encontrada."));
        var operacao = operacoes
                .findFirstByIdentificadorDoVinculoAndCodigoDeConfirmacaoHashAndEstadoInOrderByCriadoEmDesc(
                        vinculo.identificador(), segredos.hash(normalizado),
                        List.of(EstadoDaOperacaoAssistida.AGUARDANDO_CONFIRMACAO,
                                EstadoDaOperacaoAssistida.APLICADA))
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "OPERACAO_ASSISTIDA_NAO_ENCONTRADA",
                        "Operacao assistida nao encontrada."));
        return confirmarComResultado(operacao.paraDominio().identificador(),
                normalizado, metodo, bot, telegram, chat, sessao, update);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public OperacaoAssistida confirmarEAplicar(UUID identificador,
            String codigo, String metodo, long bot, long telegram, long chat,
            String sessao, String update) {
        return confirmarComResultado(identificador, codigo, metodo, bot,
                telegram, chat, sessao, update).operacao();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ResultadoDaConfirmacao confirmarComResultado(UUID identificador,
            String codigo, String metodo, long bot, long telegram, long chat,
            String sessao, String update) {
        if (codigo == null || !CODIGO.matcher(codigo.trim().toUpperCase()).matches()
                || !List.of("BOTAO", "TEXTO", "VOZ").contains(metodo)
                || sessao == null || sessao.isBlank() || update == null
                || update.isBlank()) {
            throw new RegraDeDominio("CONFIRMACAO_INVALIDA",
                    "A confirmacao informada e invalida.");
        }
        OperacaoAssistidaPersistida encontrada = operacoes.findById(identificador)
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "OPERACAO_ASSISTIDA_NAO_ENCONTRADA",
                        "Operacao assistida nao encontrada."));
        OperacaoAssistidaPersistida persistida = operacoes
                .encontrarParaAtualizacao(identificador,
                        encontrada.paraDominio().identificadorDoUsuario())
                .orElseThrow();
        OperacaoAssistida operacao = persistida.paraDominio();
        var vinculo = vinculos.findByIdentificadorAndIdentificadorDoUsuario(
                        operacao.identificadorDoVinculo(),
                        operacao.identificadorDoUsuario())
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "VINCULO_DO_TELEGRAM_NAO_ENCONTRADO",
                        "Vinculo do Telegram nao encontrado."));
        if (vinculo.estado() != EstadoDoVinculoDeCanal.ATIVO
                || !Long.valueOf(bot).equals(vinculo.identificadorDoBot())
                || !Long.valueOf(telegram).equals(vinculo.identificadorExterno())
                || !Long.valueOf(chat).equals(vinculo.identificadorDoChat())
                || !sessao.equals(vinculo.identificadorDaSessao())) {
            throw new RecursoNaoEncontrado("OPERACAO_ASSISTIDA_NAO_ENCONTRADA",
                    "Operacao assistida nao encontrada.");
        }
        if (operacao.estado() == EstadoDaOperacaoAssistida.APLICADA) {
            return new ResultadoDaConfirmacao(operacao, null);
        }
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        String informado = codigo.trim().toUpperCase();
        if (persistida.confirmacaoExpiraEm() == null
                || !agora.isBefore(persistida.confirmacaoExpiraEm())
                || !segredos.corresponde(persistida.codigoDeConfirmacaoHash(),
                        segredos.hash(informado))) {
            throw new ConflitoDeDominio("CONFIRMACAO_EXPIRADA_OU_INVALIDA",
                    "A confirmacao expirou ou o codigo divergiu.");
        }
        Map<String, Object> proposta = mapa(operacao.propostaCanonica());
        Map<String, Object> atuais = preparacoes.versoesAtuais(operacao.tipo(),
                operacao.identificadorDoUsuario(), proposta);
        servicoDeOperacoes.validarAtualidade(operacao.identificadorDoUsuario(),
                operacao.identificador(), operacao.assinatura(), json(atuais));
        if ("REFORCADA".equals(persistida.nivelDeConfirmacao())
                && persistida.etapaDaConfirmacao() == 0) {
            String segundoCodigo = segredos.derivarCodigoDeConfirmacao(
                    "segunda-etapa:" + operacao.assinatura());
            persistida.registrarContextoDaConfirmacao(metodo, bot, telegram,
                    chat, sessao, update);
            persistida.definirConfirmacao(segredos.hash(segundoCodigo),
                    segundoCodigo.substring(0, 2),
                    segredos.hash("segunda-etapa:nonce:" + operacao.assinatura()),
                    operacao.expiraEm(), "REFORCADA", 1);
            operacoes.saveAndFlush(persistida);
            metricas.registrarPrimeiraConfirmacaoReforcada();
            return new ResultadoDaConfirmacao(operacao, segundoCodigo);
        }
        operacao.confirmar(operacao.assinatura(), agora);
        persistida.atualizarDe(operacao);
        persistida.registrarContextoDaConfirmacao(metodo, bot, telegram, chat,
                sessao, update);
        operacoes.saveAndFlush(persistida);
        Map<String, Object> resultado = aplicar(operacao, proposta);
        operacao.aplicar(json(resultado), OffsetDateTime.now(ZoneOffset.UTC));
        metricas.registrarAplicacao();
        persistida.atualizarDe(operacao);
        return new ResultadoDaConfirmacao(
                operacoes.saveAndFlush(persistida).paraDominio(), null);
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
