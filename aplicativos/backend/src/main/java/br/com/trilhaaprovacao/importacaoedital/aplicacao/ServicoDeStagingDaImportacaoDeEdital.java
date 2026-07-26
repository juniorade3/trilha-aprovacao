package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.importacaoedital.dominio.EstadoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.FonteDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ProblemaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.SeveridadeDoProblemaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.ConfiguracaoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.ImportacaoDeEditalPersistida;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.RepositorioDeImportacoesDeEdital;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.RepositorioDeVersoesDaExtracaoDoEdital;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.VersaoDaExtracaoDoEditalPersistida;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoDeStagingDaImportacaoDeEdital {
    private static final String VERSAO_DO_EXTRATOR_MANUAL = "manual-1";
    private static final Set<EstadoDaImportacaoDeEdital>
            ESTADOS_QUE_ACEITAM_CORRECAO_MANUAL = Set.of(
                    EstadoDaImportacaoDeEdital.EXTRAIDA,
                    EstadoDaImportacaoDeEdital.AGUARDANDO_SELECAO,
                    EstadoDaImportacaoDeEdital.AGUARDANDO_CORRECOES,
                    EstadoDaImportacaoDeEdital.VALIDADA);
    private static final Set<EstadoDaImportacaoDeEdital>
            ESTADOS_QUE_ACEITAM_REEXTRACAO = Set.of(
                    EstadoDaImportacaoDeEdital.RECEBIDA,
                    EstadoDaImportacaoDeEdital.EXTRAIDA,
                    EstadoDaImportacaoDeEdital.AGUARDANDO_SELECAO,
                    EstadoDaImportacaoDeEdital.AGUARDANDO_CORRECOES,
                    EstadoDaImportacaoDeEdital.VALIDADA,
                    EstadoDaImportacaoDeEdital.FALHOU);

    private final RepositorioDeImportacoesDeEdital importacoes;
    private final RepositorioDeVersoesDaExtracaoDoEdital versoes;
    private final ConfiguracaoDaImportacaoDeEdital configuracao;
    private final ServicoDeExtracaoDoArquivoDoEdital extrator;
    private final ParserDeterministicoDoEdital parser;
    private final ValidadorDaExtracaoDoEdital validador;
    private final ObjectMapper mapeador;
    private final JdbcTemplate banco;

    public ServicoDeStagingDaImportacaoDeEdital(
            RepositorioDeImportacoesDeEdital importacoes,
            RepositorioDeVersoesDaExtracaoDoEdital versoes,
            ConfiguracaoDaImportacaoDeEdital configuracao,
            ServicoDeExtracaoDoArquivoDoEdital extrator,
            ObjectMapper mapeador, JdbcTemplate banco) {
        this.importacoes = importacoes;
        this.versoes = versoes;
        this.configuracao = configuracao;
        this.extrator = extrator;
        this.mapeador = mapeador;
        this.banco = banco;
        parser = new ParserDeterministicoDoEdital();
        validador = new ValidadorDaExtracaoDoEdital();
    }

    @Transactional
    public ImportacaoDeEdital receber(UUID usuario, String nomeDoArquivo,
            byte[] conteudo) {
        if (usuario == null) throw new IllegalArgumentException(
                "Usuario obrigatorio.");
        InspecaoDoArquivoDoEdital inspecao = extrator.inspecionar(
                nomeDoArquivo, conteudo);
        bloquearRecebimento(usuario, inspecao.sha256());
        var anterior = importacoes
                .findFirstByIdentificadorDoUsuarioAndSha256OrderByCriadoEmDesc(
                        usuario, inspecao.sha256());
        var reutilizavel = anterior.filter(item ->
                item.chaveDoCargoSelecionado() == null
                && item.estado() != EstadoDaImportacaoDeEdital.FALHOU
                && item.estado() != EstadoDaImportacaoDeEdital.CANCELADA
                && item.estado() != EstadoDaImportacaoDeEdital.APLICADA);
        if (reutilizavel.isPresent()) {
            ImportacaoDeEditalPersistida existente = reutilizavel.orElseThrow();
            if (existente.conteudoOriginal() == null) {
                OffsetDateTime agora = OffsetDateTime.now();
                existente.restaurarConteudoRetido(conteudo,
                        agora.plus(configuracao.retencaoDoConteudo()), agora);
            }
            return existente.paraDominio();
        }
        OffsetDateTime agora = OffsetDateTime.now();
        ImportacaoDeEdital importacao = ImportacaoDeEdital.receber(usuario,
                inspecao.tipoDaFonte(), inspecao.nomeDoArquivo(),
                inspecao.tipoMime(), inspecao.sha256(),
                inspecao.tamanhoEmBytes(), agora);
        ImportacaoDeEditalPersistida nova = new ImportacaoDeEditalPersistida(
                importacao, conteudo,
                agora.plus(configuracao.retencaoDoConteudo()));
        anterior.ifPresent(item -> nova.definirImportacaoDeOrigem(
                item.identificador()));
        importacoes.save(nova);
        return importacao;
    }

    private void bloquearRecebimento(UUID usuario, String sha256) {
        banco.query("""
                SELECT pg_advisory_xact_lock(hashtextextended(?, 0))
                """, (ResultSetExtractor<Void>) resultado -> {
                    if (resultado.next()) resultado.getObject(1);
                    return null;
                }, "importacao-edital:" + usuario + ":" + sha256);
    }

    @Transactional
    public ResultadoDoStagingDaImportacao extrair(UUID usuario,
            UUID identificador) {
        ImportacaoDeEditalPersistida persistida = importacoes
                .encontrarParaAtualizacao(identificador, usuario)
                .orElseThrow(ServicoDeStagingDaImportacaoDeEdital::naoEncontrada);
        ImportacaoDeEdital importacao = persistida.paraDominio();
        if (persistida.identificadorDaOperacaoAssistida() != null
                || !ESTADOS_QUE_ACEITAM_REEXTRACAO.contains(
                        importacao.estado())) {
            throw new ConflitoDeDominio(
                    "IMPORTACAO_NAO_ACEITA_REEXTRACAO",
                    "A importacao ja foi preparada, aplicada ou encerrada.");
        }
        OffsetDateTime agora = OffsetDateTime.now();
        if (importacao.estado() == EstadoDaImportacaoDeEdital.RECEBIDA) {
            importacao.iniciarExtracao(agora);
        } else {
            importacao.reiniciarExtracao(agora);
        }
        persistida.limparFalha();
        persistida.atualizarDe(importacao);
        try {
            ResultadoDaExtracaoDoArquivo arquivo = extrator.extrair(
                    persistida.conteudoOriginal());
            importacao.classificarFonte(arquivo.tipoDaFonte(),
                    OffsetDateTime.now());
            persistida.registrarConteudoExtraido(arquivo.tipoDaFonte(),
                    arquivo.quantidadeDePaginas(), arquivo.texto());
            FonteDoEdital fonte = new FonteDoEdital(
                    importacao.nomeDoArquivo(), importacao.sha256(),
                    arquivo.quantidadeDePaginas());
            ExtracaoEstruturadaDoEdital extracao = parser.extrair(
                    arquivo.texto(), fonte);
            List<ProblemaDaImportacao> problemas = unir(arquivo.problemas(),
                    validador.validar(extracao));
            String dados = escrever(extracao);
            String problemasJson = escrever(problemas);
            String hash = ServicoDeExtracaoDoArquivoDoEdital.sha256(
                    (dados + "\n" + problemasJson).getBytes(
                            StandardCharsets.UTF_8));
            int numero = importacao.versaoAtualDaExtracao() + 1;
            EstadoDaImportacaoDeEdital estado = proximoEstado(extracao,
                    problemas);
            versoes.save(new VersaoDaExtracaoDoEditalPersistida(
                    identificador, usuario, numero, "1",
                    ParserDeterministicoDoEdital.VERSAO, dados,
                    problemasJson, hash, OffsetDateTime.now()));
            importacao.registrarExtracao(numero, hash, estado,
                    OffsetDateTime.now());
            persistida.atualizarDe(importacao);
            return new ResultadoDoStagingDaImportacao(importacao, extracao,
                    problemas);
        } catch (FalhaNaExtracaoDoEdital falha) {
            importacao.falhar(falha.codigo(), OffsetDateTime.now());
            persistida.atualizarDe(importacao);
            persistida.registrarFalha(falha.codigo(), falha.getMessage());
            ProblemaDaImportacao problema = new ProblemaDaImportacao(
                    SeveridadeDoProblemaDaImportacao.BLOQUEANTE,
                    falha.codigo(), falha.getMessage(), "fonte");
            return new ResultadoDoStagingDaImportacao(importacao, null,
                    List.of(problema));
        }
    }

    /**
     * Registra uma correcao humana como nova versao imutavel da extracao.
     * O bloqueio da importacao serializa correcoes concorrentes e o numero
     * esperado impede que uma revisao antiga substitua trabalho mais recente.
     */
    @Transactional
    public ResultadoDoStagingDaImportacao registrarCorrecaoManual(UUID usuario,
            UUID identificador, int versaoEsperada,
            ExtracaoEstruturadaDoEdital extracaoCorrigida) {
        if (usuario == null || identificador == null) {
            throw new RegraDeDominio("CORRECAO_MANUAL_INVALIDA",
                    "Usuario e importacao sao obrigatorios.");
        }
        ImportacaoDeEditalPersistida persistida = importacoes
                .encontrarParaAtualizacao(identificador, usuario)
                .orElseThrow(ServicoDeStagingDaImportacaoDeEdital::naoEncontrada);
        ImportacaoDeEdital importacao = persistida.paraDominio();
        if (versaoEsperada < 1
                || versaoEsperada != importacao.versaoAtualDaExtracao()) {
            throw new ConflitoDeDominio(
                    "VERSAO_DA_EXTRACAO_DESATUALIZADA",
                    "A extracao mudou. Atualize os dados antes de corrigir.");
        }
        if (persistida.identificadorDaOperacaoAssistida() != null
                || !ESTADOS_QUE_ACEITAM_CORRECAO_MANUAL.contains(
                        importacao.estado())) {
            throw new ConflitoDeDominio(
                    "IMPORTACAO_NAO_ACEITA_CORRECAO_MANUAL",
                    "A importacao ja foi preparada, aplicada ou encerrada.");
        }

        validarFonteDaCorrecao(persistida, extracaoCorrigida);
        List<ProblemaDaImportacao> problemas = validarCorrecao(
                extracaoCorrigida);
        String dados = escrever(extracaoCorrigida);
        String problemasJson = escrever(problemas);
        validarLimiteDoJson(dados, problemasJson);
        String hash = ServicoDeExtracaoDoArquivoDoEdital.sha256(
                (dados + "\n" + problemasJson).getBytes(
                        StandardCharsets.UTF_8));
        if (hash.equals(importacao.hashDaExtracaoAtual())) {
            throw new ConflitoDeDominio("CORRECAO_MANUAL_SEM_ALTERACOES",
                    "A correcao nao altera a extracao atual.");
        }

        OffsetDateTime agora = OffsetDateTime.now();
        importacao.reiniciarExtracao(agora);
        int numero = versaoEsperada + 1;
        EstadoDaImportacaoDeEdital estado = proximoEstado(extracaoCorrigida,
                problemas);
        versoes.save(new VersaoDaExtracaoDoEditalPersistida(
                identificador, usuario, numero,
                extracaoCorrigida.versaoDoContrato(),
                VERSAO_DO_EXTRATOR_MANUAL, dados, problemasJson, hash, agora));
        importacao.registrarExtracao(numero, hash, estado, agora);
        persistida.limparFalha();
        persistida.atualizarDe(importacao);
        return new ResultadoDoStagingDaImportacao(importacao,
                extracaoCorrigida, problemas);
    }

    @Transactional(readOnly = true)
    public ImportacaoDeEdital obter(UUID usuario, UUID identificador) {
        return importacoes.findByIdentificadorAndIdentificadorDoUsuario(
                identificador, usuario).map(ImportacaoDeEditalPersistida::paraDominio)
                .orElseThrow(ServicoDeStagingDaImportacaoDeEdital::naoEncontrada);
    }

    @Transactional(readOnly = true)
    public ResultadoDoStagingDaImportacao obterExtracaoAtual(UUID usuario,
            UUID identificador) {
        ImportacaoDeEditalPersistida persistida = importacoes
                .findByIdentificadorAndIdentificadorDoUsuario(
                        identificador, usuario)
                .orElseThrow(ServicoDeStagingDaImportacaoDeEdital::naoEncontrada);
        ImportacaoDeEdital importacao = persistida.paraDominio();
        return versoes
                .findFirstByIdentificadorDaImportacaoAndIdentificadorDoUsuarioOrderByNumeroDaVersaoDesc(
                        identificador, usuario)
                .map(versao -> new ResultadoDoStagingDaImportacao(importacao,
                        ler(versao.dadosEstruturados(),
                                ExtracaoEstruturadaDoEdital.class),
                        unir(lerProblemas(versao.problemas()),
                                problemasDaFalha(persistida))))
                .orElseGet(() -> new ResultadoDoStagingDaImportacao(importacao,
                        null, problemasDaFalha(persistida)));
    }

    private static List<ProblemaDaImportacao> problemasDaFalha(
            ImportacaoDeEditalPersistida persistida) {
        if (persistida.estado() != EstadoDaImportacaoDeEdital.FALHOU
                || persistida.codigoDaFalha() == null) {
            return List.of();
        }
        String mensagem = persistida.descricaoDaFalha() == null
                ? "A importacao falhou antes de concluir a extracao."
                : persistida.descricaoDaFalha();
        return List.of(new ProblemaDaImportacao(
                SeveridadeDoProblemaDaImportacao.BLOQUEANTE,
                persistida.codigoDaFalha(), mensagem, "fonte"));
    }

    private EstadoDaImportacaoDeEdital proximoEstado(
            ExtracaoEstruturadaDoEdital extracao,
            List<ProblemaDaImportacao> problemas) {
        if (validador.possuiBloqueante(problemas)) {
            return EstadoDaImportacaoDeEdital.AGUARDANDO_CORRECOES;
        }
        if (extracao.cargos().size() > 1) {
            return EstadoDaImportacaoDeEdital.AGUARDANDO_SELECAO;
        }
        if (validador.exigeDecisao(problemas)) {
            return EstadoDaImportacaoDeEdital.AGUARDANDO_CORRECOES;
        }
        return EstadoDaImportacaoDeEdital.VALIDADA;
    }

    private void validarFonteDaCorrecao(
            ImportacaoDeEditalPersistida persistida,
            ExtracaoEstruturadaDoEdital extracao) {
        if (extracao == null || extracao.fonte() == null) {
            throw correcaoInvalida("Fonte da extracao obrigatoria.");
        }
        FonteDoEdital fonte = extracao.fonte();
        Integer paginasOriginais = persistida.quantidadeDePaginas();
        if (!Objects.equals(fonte.nomeDoArquivo(),
                    persistida.nomeDoArquivo())
                || !Objects.equals(fonte.sha256(), persistida.sha256())) {
            throw correcaoInvalida(
                    "Nome e SHA-256 da fonte original devem ser preservados.");
        }
        if (fonte.paginas() < 1
                || fonte.paginas() > configuracao.limiteDePaginas()
                || paginasOriginais == null
                || fonte.paginas() != paginasOriginais) {
            throw correcaoInvalida(
                    "Quantidade de paginas da fonte esta inconsistente.");
        }
    }

    private List<ProblemaDaImportacao> validarCorrecao(
            ExtracaoEstruturadaDoEdital extracao) {
        try {
            return validador.validar(extracao);
        } catch (IllegalArgumentException | NullPointerException excecao) {
            throw correcaoInvalida("Estrutura da extracao invalida.");
        }
    }

    private void validarLimiteDoJson(String dados, String problemas) {
        long tamanho = (long) dados.getBytes(StandardCharsets.UTF_8).length
                + problemas.getBytes(StandardCharsets.UTF_8).length;
        if (tamanho > configuracao.limiteDeCaracteresExtraidos()) {
            throw new RegraDeDominio("CORRECAO_MANUAL_MUITO_GRANDE",
                    "Correcao manual excede o limite permitido.");
        }
    }

    private static RegraDeDominio correcaoInvalida(String mensagem) {
        return new RegraDeDominio("CORRECAO_MANUAL_INVALIDA", mensagem);
    }

    private static List<ProblemaDaImportacao> unir(
            List<ProblemaDaImportacao> primeiros,
            List<ProblemaDaImportacao> segundos) {
        Map<String, ProblemaDaImportacao> unicos = new LinkedHashMap<>();
        List<ProblemaDaImportacao> todos = new ArrayList<>(primeiros);
        todos.addAll(segundos);
        todos.forEach(item -> unicos.putIfAbsent(
                item.codigo() + "\0" + item.caminho(), item));
        return List.copyOf(unicos.values());
    }

    private String escrever(Object valor) {
        try {
            return mapeador.writeValueAsString(valor);
        } catch (Exception excecao) {
            throw new IllegalStateException("Falha ao serializar staging.", excecao);
        }
    }

    private <T> T ler(String json, Class<T> tipo) {
        try {
            return mapeador.readValue(json, tipo);
        } catch (Exception excecao) {
            throw new IllegalStateException("Staging persistido invalido.", excecao);
        }
    }

    private List<ProblemaDaImportacao> lerProblemas(String json) {
        try {
            ProblemaDaImportacao[] valores = mapeador.readValue(json,
                    ProblemaDaImportacao[].class);
            return List.of(valores);
        } catch (Exception excecao) {
            throw new IllegalStateException("Problemas persistidos invalidos.",
                    excecao);
        }
    }

    private static RecursoNaoEncontrado naoEncontrada() {
        return new RecursoNaoEncontrado("IMPORTACAO_DE_EDITAL_NAO_ENCONTRADA",
                "Importacao de edital nao encontrada.");
    }
}
