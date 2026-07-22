package br.com.trilhaaprovacao.automacao.aplicacao;

import br.com.trilhaaprovacao.automacao.dominio.CanalDeIntegracao;
import br.com.trilhaaprovacao.automacao.dominio.CredencialDeIntegracao;
import br.com.trilhaaprovacao.automacao.dominio.EstadoDoVinculoDeCanal;
import br.com.trilhaaprovacao.automacao.dominio.EventoDeAuditoriaDaAutomacao;
import br.com.trilhaaprovacao.automacao.dominio.VinculoDeCanal;
import br.com.trilhaaprovacao.automacao.infraestrutura.CredencialDeIntegracaoPersistida;
import br.com.trilhaaprovacao.automacao.infraestrutura.EventoDeAuditoriaDaAutomacaoPersistido;
import br.com.trilhaaprovacao.automacao.infraestrutura.RepositorioDeCredenciaisDeIntegracao;
import br.com.trilhaaprovacao.automacao.infraestrutura.RepositorioDeEventosDeAuditoriaDaAutomacao;
import br.com.trilhaaprovacao.automacao.infraestrutura.RepositorioDeVinculosDeCanal;
import br.com.trilhaaprovacao.automacao.infraestrutura.ServicoDeSegredosDaAutomacao;
import br.com.trilhaaprovacao.automacao.infraestrutura.VinculoDeCanalPersistido;
import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoDeVinculosDoTelegram {
    private static final String ESCOPOS_INICIAIS =
            "agenda:ler revisoes:ler prioridades:ler progresso:ler historico:ler "
                    + "estrutura:ler operacoes:ler";

    private final RepositorioDeVinculosDeCanal vinculos;
    private final RepositorioDeCredenciaisDeIntegracao credenciais;
    private final RepositorioDeEventosDeAuditoriaDaAutomacao auditoria;
    private final ServicoDeSegredosDaAutomacao segredos;
    private final long identificadorDoBot;
    private final Duration validadeDoCodigo;
    private final Duration validadeDaCredencial;

    public ServicoDeVinculosDoTelegram(
            RepositorioDeVinculosDeCanal vinculos,
            RepositorioDeCredenciaisDeIntegracao credenciais,
            RepositorioDeEventosDeAuditoriaDaAutomacao auditoria,
            ServicoDeSegredosDaAutomacao segredos,
            @Value("${trilha.automacao.identificador-do-bot:0}") long identificadorDoBot,
            @Value("${trilha.automacao.validade-do-codigo:PT10M}") Duration validadeDoCodigo,
            @Value("${trilha.automacao.validade-da-credencial:P90D}") Duration validadeDaCredencial) {
        this.vinculos = vinculos;
        this.credenciais = credenciais;
        this.auditoria = auditoria;
        this.segredos = segredos;
        this.identificadorDoBot = identificadorDoBot;
        this.validadeDoCodigo = validadeDoCodigo;
        this.validadeDaCredencial = validadeDaCredencial;
    }

    @Transactional
    public CodigoDeVinculoGerado gerarCodigo(UUID usuario) {
        validarConfiguracao();
        vinculos.bloquearGeracaoParaUsuario(usuario);
        OffsetDateTime agora = agora();
        vinculoAtualParaAtualizacao(usuario, List.of(
                EstadoDoVinculoDeCanal.PENDENTE,
                EstadoDoVinculoDeCanal.ATIVO)).ifPresent(existente -> {
            if (existente.paraDominio().estado() == EstadoDoVinculoDeCanal.ATIVO) {
                throw new ConflitoDeDominio("TELEGRAM_JA_VINCULADO",
                        "A conta ja possui um Telegram vinculado.");
            }
            VinculoDeCanal anterior = existente.paraDominio();
            anterior.revogar(agora);
            existente.atualizarDe(anterior);
            vinculos.flush();
        });
        return criarVinculoPendente(usuario, agora, "CODIGO_DE_VINCULO_GERADO");
    }

    @Transactional(readOnly = true)
    public VinculoDeCanal obter(UUID usuario) {
        return vinculoAtual(usuario).map(VinculoDeCanalPersistido::paraDominio)
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "VINCULO_DO_TELEGRAM_NAO_ENCONTRADO",
                        "Vinculo do Telegram nao encontrado."));
    }

    @Transactional
    public void revogar(UUID usuario) {
        OffsetDateTime agora = agora();
        VinculoDeCanalPersistido persistido = vinculoAtualParaAtualizacao(
                usuario, List.of(EstadoDoVinculoDeCanal.PENDENTE,
                        EstadoDoVinculoDeCanal.ATIVO))
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "VINCULO_DO_TELEGRAM_NAO_ENCONTRADO",
                        "Vinculo do Telegram nao encontrado."));
        VinculoDeCanal vinculo = persistido.paraDominio();
        vinculo.revogar(agora);
        persistido.atualizarDe(vinculo);
        revogarCredenciais(vinculo.identificador(), agora);
        auditar(usuario, vinculo.identificador(), "VINCULO_REVOGADO",
                null, "USUARIO_WEB", "APLICACAO_WEB", "SUCESSO", agora);
    }

    @Transactional
    public CodigoDeVinculoGerado rotacionar(UUID usuario) {
        validarConfiguracao();
        vinculos.bloquearGeracaoParaUsuario(usuario);
        OffsetDateTime agora = agora();
        VinculoDeCanalPersistido persistido = vinculoAtualParaAtualizacao(
                usuario, List.of(EstadoDoVinculoDeCanal.ATIVO))
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "VINCULO_ATIVO_DO_TELEGRAM_NAO_ENCONTRADO",
                        "Vinculo ativo do Telegram nao encontrado."));
        VinculoDeCanal anterior = persistido.paraDominio();
        anterior.revogar(agora);
        persistido.atualizarDe(anterior);
        revogarCredenciais(anterior.identificador(), agora);
        auditar(usuario, anterior.identificador(),
                "CREDENCIAL_ANTERIOR_REVOGADA_PARA_ROTACAO", null,
                "USUARIO_WEB", "APLICACAO_WEB", "SUCESSO", agora);
        vinculos.flush();
        return criarVinculoPendente(usuario, agora,
                "ROTACAO_DE_CREDENCIAL_PREPARADA");
    }

    @Transactional(noRollbackFor = CodigoDeVinculoExpirado.class)
    public ResultadoDaTrocaDoCodigo trocarCodigo(String codigo, long bot,
            long identificadorExterno, long identificadorDoChat) {
        validarConfiguracao();
        if (bot != identificadorDoBot) {
            throw new RecursoNaoEncontrado("CODIGO_DE_VINCULO_NAO_ENCONTRADO",
                    "Codigo de vinculo nao encontrado.");
        }
        OffsetDateTime agora = agora();
        String hash = segredos.hash(normalizarCodigo(codigo));
        VinculoDeCanalPersistido persistido = vinculos
                .findByCodigoDeVinculoHashAndEstado(
                        hash, EstadoDoVinculoDeCanal.PENDENTE)
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "CODIGO_DE_VINCULO_NAO_ENCONTRADO",
                        "Codigo de vinculo nao encontrado."));
        VinculoDeCanal vinculo = persistido.paraDominio();
        if (!vinculo.codigoValidoEm(agora)) {
            vinculo.expirar(agora);
            persistido.atualizarDe(vinculo);
            throw new CodigoDeVinculoExpirado();
        }
        if (vinculos.existsByCanalAndIdentificadorDoBotAndIdentificadorExternoAndEstado(
                CanalDeIntegracao.TELEGRAM, bot, identificadorExterno,
                EstadoDoVinculoDeCanal.ATIVO)) {
            throw new ConflitoDeDominio("TELEGRAM_JA_VINCULADO",
                    "Este Telegram ja esta vinculado a uma conta.");
        }
        try {
            vinculo.ativar(identificadorExterno, identificadorDoChat, agora);
        } catch (IllegalArgumentException | IllegalStateException excecao) {
            throw new RegraDeDominio("VINCULO_DO_TELEGRAM_INVALIDO",
                    excecao.getMessage());
        }
        persistido.atualizarDe(vinculo);
        vinculos.flush();
        CredencialEmitida emitida = emitirCredencial(vinculo.identificador(), agora);
        auditar(vinculo.identificadorDoUsuario(), vinculo.identificador(),
                "VINCULO_ATIVADO", hash, "GATEWAY_TELEGRAM", "TELEGRAM",
                "SUCESSO", agora);
        return new ResultadoDaTrocaDoCodigo(persistido.paraDominio(), emitida);
    }

    private CodigoDeVinculoGerado criarVinculoPendente(UUID usuario,
            OffsetDateTime agora, String acaoDaAuditoria) {
        String codigo = segredos.gerarCodigoDeVinculo();
        String hash = segredos.hash(normalizarCodigo(codigo));
        VinculoDeCanal vinculo = VinculoDeCanal.preparar(usuario,
                identificadorDoBot, hash, agora.plus(validadeDoCodigo), agora);
        VinculoDeCanal salvo = vinculos.saveAndFlush(
                new VinculoDeCanalPersistido(vinculo)).paraDominio();
        auditar(usuario, salvo.identificador(), acaoDaAuditoria,
                hash, "USUARIO_WEB", "APLICACAO_WEB", "SUCESSO", agora);
        return new CodigoDeVinculoGerado(codigo, salvo.codigoExpiraEm(), salvo);
    }

    private CredencialEmitida emitirCredencial(UUID vinculo, OffsetDateTime agora) {
        String token = segredos.gerarToken();
        String prefixo = token.substring(0, Math.min(16, token.length()));
        CredencialDeIntegracao credencial = CredencialDeIntegracao.criar(vinculo,
                segredos.hash(token), prefixo, ESCOPOS_INICIAIS,
                agora.plus(validadeDaCredencial), agora);
        CredencialDeIntegracao salva = credenciais.saveAndFlush(
                new CredencialDeIntegracaoPersistida(credencial)).paraDominio();
        return new CredencialEmitida(token, salva);
    }

    private void revogarCredenciais(UUID vinculo, OffsetDateTime agora) {
        credenciais.findByIdentificadorDoVinculoAndRevogadoEmIsNull(vinculo)
                .forEach(persistida -> {
                    CredencialDeIntegracao credencial = persistida.paraDominio();
                    credencial.revogar(agora);
                    persistida.atualizarDe(credencial);
                });
        credenciais.flush();
    }

    private java.util.Optional<VinculoDeCanalPersistido> vinculoAtual(UUID usuario) {
        return vinculos
                .findFirstByIdentificadorDoUsuarioAndCanalAndEstadoInOrderByCriadoEmDesc(
                        usuario, CanalDeIntegracao.TELEGRAM,
                        List.of(EstadoDoVinculoDeCanal.PENDENTE,
                                EstadoDoVinculoDeCanal.ATIVO));
    }

    private java.util.Optional<VinculoDeCanalPersistido>
            vinculoAtualParaAtualizacao(UUID usuario,
                    List<EstadoDoVinculoDeCanal> estados) {
        var candidato = vinculos
                .findFirstByIdentificadorDoUsuarioAndCanalAndEstadoInOrderByCriadoEmDesc(
                        usuario, CanalDeIntegracao.TELEGRAM, estados);
        if (candidato.isEmpty()) {
            return java.util.Optional.empty();
        }
        return vinculos.encontrarParaAtualizacao(
                        candidato.get().identificador(), usuario)
                .filter(vinculo -> estados.contains(vinculo.estado()));
    }

    private void auditar(UUID usuario, UUID vinculo, String acao, String hash,
            String ator, String fonte, String resultado, OffsetDateTime agora) {
        EventoDeAuditoriaDaAutomacao evento = EventoDeAuditoriaDaAutomacao.criar(
                usuario, vinculo, null, ator, null, acao, hash, null,
                fonte, resultado, UUID.randomUUID(), "{}", agora);
        auditoria.save(new EventoDeAuditoriaDaAutomacaoPersistido(evento));
    }

    private void validarConfiguracao() {
        if (identificadorDoBot <= 0 || !segredos.configurado()
                || validadeDoCodigo.isZero()
                || validadeDoCodigo.isNegative() || validadeDaCredencial.isZero()
                || validadeDaCredencial.isNegative()) {
            throw new RegraDeDominio("AUTOMACAO_NAO_CONFIGURADA",
                    "A integracao com o Telegram ainda nao foi configurada.");
        }
    }

    private String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new RegraDeDominio("CODIGO_DE_VINCULO_INVALIDO",
                    "Informe o codigo de vinculo.");
        }
        return codigo.replace("-", "").replace(" ", "").toUpperCase();
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
