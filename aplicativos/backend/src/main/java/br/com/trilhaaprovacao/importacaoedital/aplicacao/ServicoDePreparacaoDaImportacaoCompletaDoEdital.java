package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.PreparadorDaImportacaoCompletaDoEdital.ContagensDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.PreparadorDaImportacaoCompletaDoEdital.ItemDaPreviaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.PreparadorDaImportacaoCompletaDoEdital.PreviaDaImportacaoCompleta;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.PreparadorDaImportacaoCompletaDoEdital.SolicitacaoDePreparacaoDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.EstadoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.CargoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.GrupoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ItemExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.MateriaExtraida;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ProvaExtraida;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.TopicoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ModoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.PoliticaDeReutilizacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.ProblemaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.SeveridadeDoProblemaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.ValorExtraido;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.ImportacaoDeEditalPersistida;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.ProvenienciaDaImportacaoDoEditalPersistida;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.RelatorioDaImportacaoDoEditalPersistido;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.RepositorioDeImportacoesDeEdital;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.RepositorioDeProvenienciasDaImportacaoDoEdital;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.RepositorioDeRelatoriosDaImportacaoDoEdital;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoDePreparacaoDaImportacaoCompletaDoEdital
        implements PreparadorDaImportacaoCompletaDoEdital {
    private static final int LIMITE_DOS_ITENS_DA_PREVIA = 100;
    private static final int LIMITE_DOS_PROBLEMAS_DA_PREVIA = 100;
    private static final TypeReference<Map<String, Object>> MAPA =
            new TypeReference<>() { };

    private final ServicoDeStagingDaImportacaoDeEdital staging;
    private final ServicoDeAplicacaoDaEstruturaDoEdital aplicacao;
    private final RepositorioDeImportacoesDeEdital importacoes;
    private final RepositorioDeRelatoriosDaImportacaoDoEdital relatorios;
    private final RepositorioDeProvenienciasDaImportacaoDoEdital proveniencias;
    private final JdbcTemplate banco;
    private final ObjectMapper mapeador;
    private final ValidadorDaExtracaoDoEdital validador =
            new ValidadorDaExtracaoDoEdital();
    private final NormalizadorDoTextoDoEdital normalizador =
            new NormalizadorDoTextoDoEdital();

    public ServicoDePreparacaoDaImportacaoCompletaDoEdital(
            ServicoDeStagingDaImportacaoDeEdital staging,
            ServicoDeAplicacaoDaEstruturaDoEdital aplicacao,
            RepositorioDeImportacoesDeEdital importacoes,
            RepositorioDeRelatoriosDaImportacaoDoEdital relatorios,
            RepositorioDeProvenienciasDaImportacaoDoEdital proveniencias,
            JdbcTemplate banco, ObjectMapper mapeador) {
        this.staging = staging;
        this.aplicacao = aplicacao;
        this.importacoes = importacoes;
        this.relatorios = relatorios;
        this.proveniencias = proveniencias;
        this.banco = banco;
        this.mapeador = mapeador;
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public PreviaDaImportacaoCompleta preparar(
            SolicitacaoDePreparacaoDaImportacao solicitacao) {
        PreviaDaImportacaoCompleta previa = calcularPrevia(solicitacao);
        exigirPreviaAplicavel(previa.conflitos());
        return previa;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public PreviaDaImportacaoCompleta previsualizar(
            SolicitacaoDePreparacaoDaImportacao solicitacao) {
        return calcularPrevia(solicitacao);
    }

    @Transactional(readOnly = true)
    public ConsultaDaImportacaoDeEdital consultar(UUID usuario,
            UUID importacao) {
        ImportacaoDeEditalPersistida persistida = importacoes
                .findByIdentificadorAndIdentificadorDoUsuario(
                        importacao, usuario).orElseThrow(
                                ServicoDePreparacaoDaImportacaoCompletaDoEdital
                                        ::naoEncontrada);
        return consulta(persistida, staging.obterExtracaoAtual(
                usuario, importacao));
    }

    @Transactional(readOnly = true)
    public void validarDestino(UUID usuario, ModoDaImportacaoDeEdital modo,
            UUID concursoExistente) {
        validarDestino(usuario, modo, concursoExistente, true);
    }

    @Transactional
    public ConsultaDaImportacaoDeEdital registrarDestinoInicial(UUID usuario,
            UUID importacao, ModoDaImportacaoDeEdital modo,
            UUID concursoExistente) {
        validarDestino(usuario, modo, concursoExistente, true);
        ImportacaoDeEditalPersistida persistida = bloquear(importacao, usuario);
        if (persistida.chaveDoCargoSelecionado() != null
                || persistida.identificadorDaOperacaoAssistida() != null
                || Set.of(EstadoDaImportacaoDeEdital.APLICADA,
                        EstadoDaImportacaoDeEdital.CANCELADA)
                        .contains(persistida.estado())) {
            throw new ConflitoDeDominio("DESTINO_DA_IMPORTACAO_IMUTAVEL",
                    "O destino nao pode mudar depois da selecao do cargo.");
        }
        persistida.definirDestinoInicial(modo, concursoExistente,
                PoliticaDeReutilizacao.EXIGIR_DECISAO,
                OffsetDateTime.now(ZoneOffset.UTC));
        return consulta(persistida, staging.obterExtracaoAtual(
                usuario, importacao));
    }

    private PreviaDaImportacaoCompleta calcularPrevia(
            SolicitacaoDePreparacaoDaImportacao solicitacao) {
        ImportacaoDeEditalPersistida persistida = bloquear(
                solicitacao.identificadorDaImportacao(),
                solicitacao.identificadorDoUsuario());
        PreviaDaImportacaoCompleta aplicada = previaDaOperacaoAplicada(
                persistida, solicitacao);
        if (aplicada != null) return aplicada;
        ResultadoDoStagingDaImportacao stagingAtual = staging
                .obterExtracaoAtual(solicitacao.identificadorDoUsuario(),
                        solicitacao.identificadorDaImportacao());
        validarModo(solicitacao);
        ExtracaoEstruturadaDoEdital extracao = filtrarCargo(
                exigirExtracao(stagingAtual),
                solicitacao.chaveDoCargoSelecionado());
        exigirAusenciaDeOutroLote(persistida,
                solicitacao.chaveDoCargoSelecionado());
        AnaliseDaPrevia analise = analisar(solicitacao, extracao,
                problemasDaFonte(stagingAtual.problemas()));

        Map<String, Object> proposta = proposta(solicitacao, persistida,
                stagingAtual.importacao());
        Map<String, Object> versoesConsultadas = versoesAtuais(
                solicitacao.identificadorDoUsuario(), proposta);
        String resumo = "Importar edital %s para o cargo %s; nada foi alterado."
                .formatted(stagingAtual.importacao().nomeDoArquivo(),
                        nome(extracao.cargos().getFirst().nome()));
        return new PreviaDaImportacaoCompleta(resumo, proposta,
                versoesConsultadas, analise.contagens,
                limitar(analise.itensACriar, LIMITE_DOS_ITENS_DA_PREVIA),
                limitar(analise.itensAReutilizar,
                        LIMITE_DOS_ITENS_DA_PREVIA),
                limitar(analise.problemas, LIMITE_DOS_PROBLEMAS_DA_PREVIA),
                extracao.incertezas(), camposAusentes(analise.problemas));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ResultadoDoStagingDaImportacao registrarDecisoes(UUID usuario,
            UUID importacao, String cargo, ModoDaImportacaoDeEdital modo,
            UUID concursoExistente, PoliticaDeReutilizacao politica,
            int versaoDaExtracao) {
        ImportacaoDeEditalPersistida persistida = bloquear(importacao, usuario);
        if (persistida.versaoAtualDaExtracao() != versaoDaExtracao) {
            throw new ConflitoDeDominio(
                    "EXTRACAO_DA_IMPORTACAO_DESATUALIZADA",
                    "A extracao mudou; revise novamente as decisoes.");
        }
        SolicitacaoDePreparacaoDaImportacao solicitacao =
                new SolicitacaoDePreparacaoDaImportacao(usuario, importacao,
                        cargo, modo, concursoExistente, politica,
                        DecisoesDaImportacaoDoEdital.vazias());
        validarModo(solicitacao);
        ResultadoDoStagingDaImportacao atual = staging.obterExtracaoAtual(
                usuario, importacao);
        ExtracaoEstruturadaDoEdital filtrada = filtrarCargo(
                exigirExtracao(atual), cargo);
        List<ProblemaDaImportacao> problemas = new ArrayList<>(
                problemasDaFonte(atual.problemas()));
        problemas.addAll(validador.validar(filtrada));
        ImportacaoDeEditalPersistida loteAnterior = loteIdempotente(
                persistida, cargo);
        if (loteAnterior != null) {
            if (loteAnterior.modo() != modo
                    || !Objects.equals(
                            loteAnterior.identificadorDoConcursoExistente(),
                            concursoExistente)
                    || loteAnterior.politicaDeReutilizacao() != politica) {
                throw new ConflitoDeDominio(
                        "LOTE_DA_IMPORTACAO_JA_POSSUI_OUTRO_DESTINO",
                        "O mesmo arquivo, cargo e versao ja foram usados em "
                                + "outro destino.");
            }
            ImportacaoDeEdital atualCancelada = persistida.paraDominio();
            atualCancelada.cancelar(OffsetDateTime.now(ZoneOffset.UTC));
            persistida.atualizarDe(atualCancelada);
            return staging.obterExtracaoAtual(usuario,
                    loteAnterior.identificador());
        }
        boolean bloqueante = problemas.stream().anyMatch(item ->
                item.severidade()
                        == SeveridadeDoProblemaDaImportacao.BLOQUEANTE);
        boolean decisao = problemas.stream().anyMatch(item ->
                item.severidade()
                        == SeveridadeDoProblemaDaImportacao.EXIGE_DECISAO);
        ImportacaoDeEdital dominio = persistida.paraDominio();
        dominio.selecionarCargo(cargo, bloqueante, decisao,
                OffsetDateTime.now(ZoneOffset.UTC));
        persistida.atualizarDe(dominio);
        persistida.definirDecisoes(cargo, modo, concursoExistente, politica);
        return new ResultadoDoStagingDaImportacao(dominio, atual.extracao(),
                unir(problemas));
    }

    @Transactional
    public ResultadoDoStagingDaImportacao iniciarNovaTentativa(UUID usuario,
            UUID importacao) {
        ImportacaoDeEditalPersistida persistida = bloquear(importacao, usuario);
        if (persistida.estado()
                != EstadoDaImportacaoDeEdital.AGUARDANDO_CONFIRMACAO
                || persistida.identificadorDaOperacaoAssistida() == null) {
            throw new ConflitoDeDominio("IMPORTACAO_SEM_OPERACAO_FINALIZADA",
                    "A importacao nao possui confirmacao finalizada para retomar.");
        }
        String estado = estadoDaOperacao(persistida);
        if (!Set.of("EXPIRADA", "CANCELADA", "FALHOU").contains(estado)) {
            throw new ConflitoDeDominio("OPERACAO_ASSISTIDA_AINDA_VIGENTE",
                    "A operacao atual ainda esta vigente ou ja foi aplicada.");
        }
        ImportacaoDeEdital dominio = persistida.paraDominio();
        dominio.retomarPreparacao(OffsetDateTime.now(ZoneOffset.UTC));
        persistida.iniciarNovaTentativaDaPreparacao(dominio);
        ResultadoDoStagingDaImportacao atual = staging.obterExtracaoAtual(
                usuario, importacao);
        return new ResultadoDoStagingDaImportacao(dominio, atual.extracao(),
                atual.problemas());
    }

    @Override
    @Transactional
    public void vincularOperacao(UUID usuario, UUID importacao, UUID operacao,
            Map<String, Object> propostaCanonica,
            Map<String, Object> versoesConsultadas) {
        ImportacaoDeEditalPersistida persistida = bloquear(importacao, usuario);
        if (operacao.equals(persistida.identificadorDaOperacaoAssistida())) {
            return;
        }
        Map<String, Object> atuais = versoesAtuais(usuario, propostaCanonica);
        if (!jsonCanonico(atuais).equals(jsonCanonico(versoesConsultadas))) {
            throw new ConflitoDeDominio(
                    "PREVIA_DA_IMPORTACAO_DESATUALIZADA",
                    "A importacao mudou antes de vincular a confirmacao.");
        }
        if (persistida.identificadorDaOperacaoAssistida() != null) {
            throw new ConflitoDeDominio("IMPORTACAO_JA_POSSUI_OPERACAO",
                    "A importacao ja possui uma operacao assistida vigente.");
        }
        ImportacaoDeEdital dominio = persistida.paraDominio();
        String cargo = texto(propostaCanonica, "chaveDoCargoSelecionado");
        exigirAusenciaDeOutroLote(persistida, cargo);
        if (dominio.estado() != EstadoDaImportacaoDeEdital.VALIDADA) {
            dominio.selecionarCargo(cargo, false, false,
                    OffsetDateTime.now(ZoneOffset.UTC));
        }
        dominio.aguardarConfirmacao(OffsetDateTime.now(ZoneOffset.UTC));
        persistida.atualizarDe(dominio);
        persistida.definirDecisoes(cargo,
                enumeracao(propostaCanonica, "modo",
                        ModoDaImportacaoDeEdital.class),
                uuidOpcional(propostaCanonica,
                        "identificadorDoConcursoExistente"),
                enumeracao(propostaCanonica, "politicaDeReutilizacao",
                        PoliticaDeReutilizacao.class));
        persistida.vincularOperacao(operacao);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> versoesAtuais(UUID usuario,
            Map<String, Object> propostaCanonica) {
        UUID importacao = uuid(propostaCanonica,
                "identificadorDaImportacao");
        ImportacaoDeEditalPersistida persistida = importacoes
                .findByIdentificadorAndIdentificadorDoUsuario(
                        importacao, usuario).orElseThrow(
                                ServicoDePreparacaoDaImportacaoCompletaDoEdital
                                        ::naoEncontrada);
        int versaoEsperada = inteiro(propostaCanonica,
                "versaoDaExtracao");
        String hashEsperado = texto(propostaCanonica, "hashDaExtracao");
        if (persistida.versaoAtualDaExtracao() != versaoEsperada
                || !hashEsperado.equals(persistida.hashDaExtracaoAtual())) {
            throw new ConflitoDeDominio(
                    "EXTRACAO_DA_IMPORTACAO_DESATUALIZADA",
                    "A extracao mudou depois da previa.");
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("importacao", Map.of(
                "identificador", importacao,
                "versaoDaExtracao", versaoEsperada,
                "hashDaExtracao", hashEsperado,
                "chaveDoCargoSelecionado", texto(propostaCanonica,
                        "chaveDoCargoSelecionado"),
                "tentativaDaPreparacao", inteiro(propostaCanonica,
                        "tentativaDaPreparacao")));
        resultado.put("catalogo", versoesDoCatalogo(usuario));
        UUID concurso = uuidOpcional(propostaCanonica,
                "identificadorDoConcursoExistente");
        if (concurso != null) {
            resultado.put("concursoExistente", versoesDoConcurso(
                    usuario, concurso));
        }
        Map<String, UUID> decisoes = reutilizacoes(propostaCanonica);
        Map<String, Object> versoesDosRecursos = new TreeMap<>();
        decisoes.forEach((chave, recurso) -> versoesDosRecursos.put(chave,
                versaoDoRecurso(usuario, recurso)));
        resultado.put("recursosReutilizados", versoesDosRecursos);
        return resultado;
    }

    @Override
    @Transactional
    public Map<String, Object> aplicar(UUID usuario, UUID operacao,
            Map<String, Object> propostaCanonica) {
        UUID importacao = uuid(propostaCanonica,
                "identificadorDaImportacao");
        ImportacaoDeEditalPersistida persistida = bloquear(importacao, usuario);
        var anterior = relatorios
                .findByIdentificadorDaImportacaoAndIdentificadorDoUsuario(
                        importacao, usuario);
        if (persistida.estado() == EstadoDaImportacaoDeEdital.APLICADA
                && anterior.isPresent()) {
            return reciboDoRelatorio(anterior.orElseThrow());
        }
        if (!operacao.equals(persistida.identificadorDaOperacaoAssistida())) {
            throw new ConflitoDeDominio("OPERACAO_DA_IMPORTACAO_DIVERGENTE",
                    "A operacao nao pertence a versao confirmada da importacao.");
        }
        versoesAtuais(usuario, propostaCanonica);
        ResultadoDoStagingDaImportacao atual = staging.obterExtracaoAtual(
                usuario, importacao);
        ExtracaoEstruturadaDoEdital filtrada = filtrarCargo(
                exigirExtracao(atual), texto(propostaCanonica,
                        "chaveDoCargoSelecionado"));
        List<ProblemaDaImportacao> problemasDaAplicacao = new ArrayList<>(
                problemasDaFonte(atual.problemas()));
        problemasDaAplicacao.addAll(validador.validar(filtrada));
        ModoDaImportacaoDeEdital modo = enumeracao(propostaCanonica, "modo",
                ModoDaImportacaoDeEdital.class);
        PoliticaDeReutilizacao politica = enumeracao(propostaCanonica,
                "politicaDeReutilizacao", PoliticaDeReutilizacao.class);
        DecisoesDaImportacaoDoEdital decisoesConfirmadas = decisoes(
                propostaCanonica);
        ImportacaoDeEdital dominio = persistida.paraDominio();
        dominio.iniciarAplicacao(OffsetDateTime.now(ZoneOffset.UTC));
        persistida.atualizarDe(dominio);
        ResultadoDaAplicacaoDaImportacao resultado = aplicacao.aplicar(
                new SolicitacaoDeAplicacaoDaImportacao(importacao, usuario,
                        filtrada, texto(propostaCanonica,
                                "chaveDoCargoSelecionado"),
                        modo,
                        uuidOpcional(propostaCanonica,
                                "identificadorDoConcursoExistente"),
                        politica, decisoesConfirmadas));

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        UUID identificadorDoRelatorio = UUID.randomUUID();
        List<ProvenienciaDaImportacaoDoEditalPersistida> linhas =
                criarProveniencias(usuario, importacao, filtrada,
                        resultado.identificadoresPorChave(), modo,
                        decisoesConfirmadas.recursosParaReutilizar().keySet(),
                        agora);
        proveniencias.saveAll(linhas);
        Map<String, Object> recibo = resultado.reciboCompacto(
                identificadorDoRelatorio);
        Map<String, Object> relatorio = relatorioCompleto(resultado, recibo,
                filtrada, unir(problemasDaAplicacao), propostaCanonica, modo,
                decisoesConfirmadas.recursosParaReutilizar(), agora);
        relatorios.save(new RelatorioDaImportacaoDoEditalPersistido(
                identificadorDoRelatorio, importacao, usuario, operacao,
                resultado.identificadorDoConcurso(), jsonCanonico(relatorio),
                agora));
        dominio.concluirAplicacao(agora);
        persistida.atualizarDe(dominio);
        return recibo;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obterRelatorio(UUID usuario, UUID importacao) {
        return relatorios
                .findByIdentificadorDaImportacaoAndIdentificadorDoUsuario(
                        importacao, usuario).map(this::mapa)
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "RELATORIO_DA_IMPORTACAO_NAO_ENCONTRADO",
                        "Relatorio da importacao nao encontrado."));
    }

    private String estadoDaOperacao(
            ImportacaoDeEditalPersistida persistida) {
        return banco.queryForObject("""
                SELECT estado FROM operacoes_assistidas
                WHERE identificador = ? AND usuario_id = ?
                """, String.class, persistida.identificadorDaOperacaoAssistida(),
                persistida.identificadorDoUsuario());
    }

    private PreviaDaImportacaoCompleta previaDaOperacaoAplicada(
            ImportacaoDeEditalPersistida persistida,
            SolicitacaoDePreparacaoDaImportacao solicitacao) {
        if (persistida.estado() != EstadoDaImportacaoDeEdital.APLICADA
                || persistida.identificadorDaOperacaoAssistida() == null) {
            return null;
        }
        Map<String, Object> operacao = banco.queryForMap("""
                SELECT resumo, proposta_canonica::text AS proposta,
                       versoes_consultadas::text AS versoes
                  FROM operacoes_assistidas
                 WHERE identificador = ? AND usuario_id = ?
                """, persistida.identificadorDaOperacaoAssistida(),
                persistida.identificadorDoUsuario());
        Map<String, Object> proposta = lerMapa(operacao.get("proposta"));
        validarMesmaSolicitacao(solicitacao, proposta);
        Map<String, Object> versoes = lerMapa(operacao.get("versoes"));
        Map<String, Object> relatorio = obterRelatorio(
                persistida.identificadorDoUsuario(),
                persistida.identificador());
        Map<String, Object> recibo = mapa(relatorio.get("recibo"));
        ContagensDaImportacao contagens = new ContagensDaImportacao(
                enumeracao(proposta, "modo", ModoDaImportacaoDeEdital.class)
                        == ModoDaImportacaoDeEdital.CRIAR_NOVO ? 1 : 0,
                1, 1, inteiroOuZero(recibo, "provasCriadas"),
                inteiroOuZero(recibo, "gruposCriados"),
                inteiroOuZero(recibo, "materiasCriadas"),
                inteiroOuZero(recibo, "materiasReutilizadas"),
                inteiroOuZero(recibo, "topicosCriados"),
                inteiroOuZero(recibo, "topicosReutilizados"),
                inteiroOuZero(recibo, "itensCriados"),
                inteiroOuZero(recibo,
                        "sugestoesDeMapeamentoPendentes"));
        return new PreviaDaImportacaoCompleta(
                operacao.get("resumo").toString(), proposta, versoes,
                contagens, List.of(), List.of(), List.of(),
                listaDeTextos(relatorio.get("incertezas")), List.of());
    }

    private void validarMesmaSolicitacao(
            SolicitacaoDePreparacaoDaImportacao solicitacao,
            Map<String, Object> proposta) {
        boolean mesma = solicitacao.identificadorDaImportacao().equals(
                uuid(proposta, "identificadorDaImportacao"))
                && solicitacao.chaveDoCargoSelecionado().equals(
                        texto(proposta, "chaveDoCargoSelecionado"))
                && solicitacao.modo() == enumeracao(proposta, "modo",
                        ModoDaImportacaoDeEdital.class)
                && Objects.equals(
                        solicitacao.identificadorDoConcursoExistente(),
                        uuidOpcional(proposta,
                                "identificadorDoConcursoExistente"))
                && solicitacao.politicaDeReutilizacao() == enumeracao(
                        proposta, "politicaDeReutilizacao",
                        PoliticaDeReutilizacao.class)
                && solicitacao.decisoes().equals(decisoes(proposta));
        if (!mesma) {
            throw new ConflitoDeDominio("IMPORTACAO_JA_APLICADA",
                    "A importacao ja foi aplicada com outras decisoes.");
        }
    }

    private AnaliseDaPrevia analisar(
            SolicitacaoDePreparacaoDaImportacao solicitacao,
            ExtracaoEstruturadaDoEdital extracao,
            List<ProblemaDaImportacao> problemasDaFonte) {
        List<ProblemaDaImportacao> problemas = new ArrayList<>(
                problemasDaFonte);
        problemas.addAll(validador.validar(extracao));
        List<ItemDaPreviaDaImportacao> criar = new ArrayList<>();
        List<ItemDaPreviaDaImportacao> reutilizar = new ArrayList<>();
        Set<String> chavesConhecidas = new LinkedHashSet<>();
        int materiasCriadas = 0;
        int materiasReutilizadas = 0;
        int topicosCriados = 0;
        int topicosReutilizados = 0;
        int itens = 0;
        int sugestoes = 0;
        Map<String, UUID> decisoes = solicitacao.decisoes()
                .recursosParaReutilizar();

        CargoExtraido cargo = extracao.cargos().getFirst();
        criar.add(item("CARGO", cargo.chave(), nome(cargo.nome()), null));
        for (ProvaExtraida prova : extracao.provas()) {
            criar.add(item("PROVA", prova.chave(), nome(prova.nome()), null));
            for (GrupoExtraido grupo : prova.grupos()) {
                criar.add(item("GRUPO", grupo.chave(), nome(grupo.nome()),
                        null));
            }
        }
        for (MateriaExtraida materia : extracao.materias()) {
            chavesConhecidas.add(materia.chave());
            UUID decisaoDaMateria = decisoes.get(materia.chave());
            List<Map<String, Object>> existentes = encontrarMaterias(
                    solicitacao.identificadorDoUsuario(), nome(materia.nome()));
            UUID materiaResolvida = null;
            if (decisaoDaMateria != null) {
                validarMateriaEscolhida(solicitacao.identificadorDoUsuario(),
                        decisaoDaMateria);
                materiaResolvida = decisaoDaMateria;
                reutilizar.add(item("MATERIA", materia.chave(),
                        nome(materia.nome()), decisaoDaMateria));
                materiasReutilizadas++;
            } else if (!existentes.isEmpty()) {
                SeveridadeDoProblemaDaImportacao severidade =
                        solicitacao.politicaDeReutilizacao()
                                == PoliticaDeReutilizacao.CRIAR_SEPARADO
                                ? SeveridadeDoProblemaDaImportacao.BLOQUEANTE
                                : SeveridadeDoProblemaDaImportacao.EXIGE_DECISAO;
                problemas.add(new ProblemaDaImportacao(severidade,
                        existentes.size() == 1
                                ? "MATERIA_EXISTENTE_EXIGE_DECISAO"
                                : "MATERIA_EXISTENTE_AMBIGUA",
                        existentes.size() == 1
                                ? "A materia ja existe; escolha explicitamente "
                                        + "reutilizar ou corrija o nome da nova materia."
                                : "Mais de uma materia equivale ao nome extraido; "
                                        + "escolha explicitamente o recurso correto.",
                        "materias." + materia.chave()));
                existentes.forEach(existente -> reutilizar.add(item(
                        "MATERIA", materia.chave(), nome(materia.nome()),
                        UUID.fromString(existente.get("identificador")
                                .toString()))));
            } else {
                criar.add(item("MATERIA", materia.chave(),
                        nome(materia.nome()), null));
                materiasCriadas++;
            }
            Map<String, UUID> topicosResolvidos = new LinkedHashMap<>();
            Set<String> topicosNovos = new LinkedHashSet<>();
            Set<String> topicosPendentes = new LinkedHashSet<>();
            for (TopicoExtraido topico : ordenarTopicos(materia.topicos())) {
                chavesConhecidas.add(topico.chave());
                UUID decisaoDoTopico = decisoes.get(topico.chave());
                UUID paiEsperado = topico.chaveDoPai() == null ? null
                        : topicosResolvidos.get(topico.chaveDoPai());
                boolean paiNovo = topico.chaveDoPai() != null
                        && topicosNovos.contains(topico.chaveDoPai());
                boolean paiPendente = topico.chaveDoPai() != null
                        && topicosPendentes.contains(topico.chaveDoPai());
                if (decisaoDoTopico != null) {
                    if (topico.chaveDoPai() != null && paiEsperado == null) {
                        throw new RegraDeDominio(
                                "DECISAO_DE_PAI_DO_TOPICO_OBRIGATORIA",
                                "Reutilizar um topico filho exige reutilizar "
                                        + "explicitamente o pai correspondente.");
                    }
                    validarTopicoEscolhido(
                            solicitacao.identificadorDoUsuario(),
                            materiaResolvida, decisaoDoTopico, paiEsperado);
                    topicosResolvidos.put(topico.chave(), decisaoDoTopico);
                    reutilizar.add(item("TOPICO", topico.chave(),
                            nome(topico.nome()), decisaoDoTopico));
                    topicosReutilizados++;
                } else if (paiPendente) {
                    problemas.add(new ProblemaDaImportacao(
                            SeveridadeDoProblemaDaImportacao.EXIGE_DECISAO,
                            "PAI_DO_TOPICO_EXISTENTE_EXIGE_DECISAO",
                            "Escolha o topico pai antes de analisar a "
                                    + "reutilizacao deste filho.",
                            "topicos." + topico.chave()));
                    topicosPendentes.add(topico.chave());
                } else {
                    List<Map<String, Object>> topicosExistentes =
                            materiaResolvida == null || paiNovo
                                    ? List.of()
                                    : encontrarTopicos(materiaResolvida,
                                            paiEsperado, nome(topico.nome()));
                    if (!topicosExistentes.isEmpty()) {
                        problemas.add(new ProblemaDaImportacao(
                                SeveridadeDoProblemaDaImportacao.EXIGE_DECISAO,
                                topicosExistentes.size() == 1
                                        ? "TOPICO_EXISTENTE_EXIGE_DECISAO"
                                        : "TOPICO_EXISTENTE_AMBIGUO",
                                topicosExistentes.size() == 1
                                        ? "O topico ja existe na materia e no "
                                                + "pai esperados; escolha-o explicitamente."
                                        : "Mais de um topico equivalente existe "
                                                + "na materia e no pai esperados; "
                                                + "escolha explicitamente o correto.",
                                "topicos." + topico.chave()));
                        topicosExistentes.forEach(existente -> reutilizar.add(
                                item("TOPICO", topico.chave(),
                                        nome(topico.nome()), UUID.fromString(
                                                existente.get("identificador")
                                                        .toString()))));
                        topicosPendentes.add(topico.chave());
                    } else {
                        criar.add(item("TOPICO", topico.chave(),
                                nome(topico.nome()), null));
                        topicosNovos.add(topico.chave());
                        topicosCriados++;
                    }
                }
            }
            for (ItemExtraido registro : materia.itensDoEdital()) {
                criar.add(item("ITEM_DO_EDITAL", registro.chave(),
                        nome(registro.descricaoLiteral()), null));
                itens++;
                if (registro.chaveDoTopicoSugerido() != null) sugestoes++;
            }
        }
        decisoes.keySet().stream().filter(chave ->
                !chavesConhecidas.contains(chave)).findFirst().ifPresent(
                        chave -> { throw new RegraDeDominio(
                                "DECISAO_DE_OUTRO_CARGO_OU_VERSAO",
                                "Uma decisao nao pertence ao cargo e a versao "
                                        + "selecionados: " + chave); });
        int grupos = extracao.provas().stream().mapToInt(
                prova -> prova.grupos().size()).sum();
        ContagensDaImportacao contagens = new ContagensDaImportacao(
                solicitacao.modo() == ModoDaImportacaoDeEdital.CRIAR_NOVO
                        ? 1 : 0,
                1, 1, extracao.provas().size(), grupos, materiasCriadas,
                materiasReutilizadas, topicosCriados, topicosReutilizados,
                itens, sugestoes);
        return new AnaliseDaPrevia(contagens, criar, reutilizar,
                unir(problemas));
    }

    private List<TopicoExtraido> ordenarTopicos(
            List<TopicoExtraido> topicos) {
        List<TopicoExtraido> pendentes = new ArrayList<>(topicos);
        List<TopicoExtraido> ordenados = new ArrayList<>(topicos.size());
        Set<String> processados = new LinkedHashSet<>();
        while (!pendentes.isEmpty()) {
            List<TopicoExtraido> liberados = pendentes.stream().filter(item ->
                    item.chaveDoPai() == null
                            || processados.contains(item.chaveDoPai()))
                    .sorted(Comparator.comparingInt(TopicoExtraido::ordem))
                    .toList();
            if (liberados.isEmpty()) {
                ordenados.addAll(pendentes);
                break;
            }
            ordenados.addAll(liberados);
            liberados.forEach(item -> processados.add(item.chave()));
            pendentes.removeAll(liberados);
        }
        return List.copyOf(ordenados);
    }

    private Map<String, Object> proposta(
            SolicitacaoDePreparacaoDaImportacao solicitacao,
            ImportacaoDeEditalPersistida persistida,
            ImportacaoDeEdital importacao) {
        Map<String, Object> proposta = new LinkedHashMap<>();
        proposta.put("identificadorDaImportacao",
                solicitacao.identificadorDaImportacao());
        proposta.put("versaoDaExtracao", importacao.versaoAtualDaExtracao());
        proposta.put("hashDaExtracao", importacao.hashDaExtracaoAtual());
        proposta.put("chaveDoCargoSelecionado",
                solicitacao.chaveDoCargoSelecionado());
        proposta.put("modo", solicitacao.modo().name());
        if (solicitacao.identificadorDoConcursoExistente() != null) {
            proposta.put("identificadorDoConcursoExistente",
                    solicitacao.identificadorDoConcursoExistente());
        }
        proposta.put("politicaDeReutilizacao",
                solicitacao.politicaDeReutilizacao().name());
        proposta.put("tentativaDaPreparacao",
                persistida.tentativaDaPreparacao());
        Map<String, Object> decisoes = new LinkedHashMap<>();
        decisoes.put("recursosParaReutilizar", new TreeMap<>(
                solicitacao.decisoes().recursosParaReutilizar()));
        decisoes.put("definirEditalComoPrincipal",
                solicitacao.decisoes().definirEditalComoPrincipal());
        decisoes.put("selecionarCargoCriado",
                solicitacao.decisoes().selecionarCargoCriado());
        proposta.put("decisoes", decisoes);
        return proposta;
    }

    private ExtracaoEstruturadaDoEdital filtrarCargo(
            ExtracaoEstruturadaDoEdital extracao, String chave) {
        List<CargoExtraido> cargos = extracao.cargos().stream()
                .filter(item -> chave.equals(item.chave())).toList();
        if (cargos.size() != 1) {
            throw new RegraDeDominio("CARGO_SELECIONADO_INVALIDO",
                    "Selecione explicitamente um cargo da extracao atual.");
        }
        List<ProvaExtraida> provas = extracao.provas().stream()
                .filter(item -> chave.equals(item.chaveDoCargo())).toList();
        validarAssociacoesDoCargo(extracao, chave, provas);
        List<MateriaExtraida> materias = extracao.materias().stream()
                .filter(item -> chave.equals(item.chaveDoCargo()))
                .toList();
        return new ExtracaoEstruturadaDoEdital("1", extracao.fonte(),
                extracao.concurso(), extracao.edital(), cargos, provas,
                materias, extracao.avisos(), extracao.incertezas());
    }

    private void validarAssociacoesDoCargo(
            ExtracaoEstruturadaDoEdital extracao, String chaveDoCargo,
            List<ProvaExtraida> provasDoCargo) {
        Map<String, String> provaPorGrupo = new LinkedHashMap<>();
        Set<String> provas = new LinkedHashSet<>();
        provasDoCargo.forEach(prova -> {
            provas.add(prova.chave());
            prova.grupos().forEach(grupo -> provaPorGrupo.put(
                    grupo.chave(), prova.chave()));
        });
        boolean invalida = extracao.materias().stream()
                .filter(materia -> chaveDoCargo.equals(
                        materia.chaveDoCargo()))
                .anyMatch(materia -> !provas.contains(
                        materia.chaveDaProva())
                        || !materia.chaveDaProva().equals(
                                provaPorGrupo.get(materia.chaveDoGrupo())));
        if (invalida) {
            throw new RegraDeDominio("ASSOCIACAO_DA_MATERIA_INVALIDA",
                    "Uma materia do cargo selecionado referencia prova ou "
                            + "grupo de outro cargo.");
        }
    }

    private void validarModo(SolicitacaoDePreparacaoDaImportacao solicitacao) {
        validarDestino(solicitacao.identificadorDoUsuario(),
                solicitacao.modo(),
                solicitacao.identificadorDoConcursoExistente(), false);
    }

    private void validarDestino(UUID usuario, ModoDaImportacaoDeEdital modo,
            UUID concursoExistente, boolean exigirModo) {
        if (modo == null) {
            if (exigirModo) {
                throw new RegraDeDominio("MODO_DA_IMPORTACAO_OBRIGATORIO",
                        "Informe se deseja criar ou complementar concurso.");
            }
            return;
        }
        if (modo == ModoDaImportacaoDeEdital.COMPLEMENTAR_EXISTENTE) {
            if (concursoExistente == null) {
                throw new RegraDeDominio("CONCURSO_EXISTENTE_OBRIGATORIO",
                        "Informe o concurso que recebera o complemento.");
            }
            versoesDoConcurso(usuario, concursoExistente);
        } else if (concursoExistente != null) {
            throw new RegraDeDominio("CONCURSO_EXISTENTE_INCOMPATIVEL",
                    "Criacao de concurso novo nao aceita concurso existente.");
        }
    }

    private ConsultaDaImportacaoDeEdital consulta(
            ImportacaoDeEditalPersistida persistida,
            ResultadoDoStagingDaImportacao stagingAtual) {
        return new ConsultaDaImportacaoDeEdital(stagingAtual,
                persistida.modo(),
                persistida.identificadorDoConcursoExistente(),
                persistida.politicaDeReutilizacao(),
                persistida.identificadorDaOperacaoAssistida(),
                persistida.tentativaDaPreparacao());
    }

    private void exigirAusenciaDeOutroLote(
            ImportacaoDeEditalPersistida persistida, String cargo) {
        ImportacaoDeEditalPersistida anterior = loteIdempotente(
                persistida, cargo);
        if (anterior != null) {
            throw new ConflitoDeDominio(
                    "LOTE_DA_IMPORTACAO_JA_EXISTENTE",
                    "Ja existe lote para o mesmo arquivo, cargo e versao: "
                            + anterior.identificador() + ".");
        }
    }

    private ImportacaoDeEditalPersistida loteIdempotente(
            ImportacaoDeEditalPersistida persistida, String cargo) {
        bloquearChaveDoLote(persistida, cargo);
        List<ImportacaoDeEditalPersistida> encontrados = importacoes
                .encontrarLoteIdempotente(
                        persistida.identificadorDoUsuario(),
                        persistida.sha256(), cargo,
                        persistida.versaoAtualDaExtracao(),
                        persistida.identificador());
        if (encontrados.size() > 1) {
            throw new IllegalStateException(
                    "Mais de um lote idempotente foi encontrado.");
        }
        return encontrados.isEmpty() ? null : encontrados.getFirst();
    }

    private void bloquearChaveDoLote(
            ImportacaoDeEditalPersistida persistida, String cargo) {
        String chave = "lote-importacao-edital:"
                + persistida.identificadorDoUsuario() + ":"
                + persistida.sha256() + ":" + cargo + ":"
                + persistida.versaoAtualDaExtracao();
        banco.query("""
                SELECT pg_advisory_xact_lock(hashtextextended(?, 0))
                """, (ResultSetExtractor<Void>) resultado -> {
                    if (resultado.next()) resultado.getObject(1);
                    return null;
                }, chave);
    }

    private Map<String, Object> versoesDoCatalogo(UUID usuario) {
        return banco.queryForMap("""
                SELECT COUNT(*) AS materias,
                       COALESCE(SUM(m.versao), 0) AS versoes_das_materias,
                       (SELECT COUNT(*) FROM topicos_da_materia t
                         JOIN materias mt ON mt.identificador = t.materia_id
                        WHERE mt.usuario_id = ?) AS topicos,
                       (SELECT COALESCE(SUM(t.versao), 0)
                          FROM topicos_da_materia t
                          JOIN materias mt ON mt.identificador = t.materia_id
                         WHERE mt.usuario_id = ?) AS versoes_dos_topicos
                  FROM materias m WHERE m.usuario_id = ?
                """, usuario, usuario, usuario);
    }

    private Map<String, Object> versoesDoConcurso(UUID usuario,
            UUID concurso) {
        try {
            return banco.queryForMap("""
                    SELECT c.identificador, c.versao, c.situacao,
                      (SELECT COUNT(*) FROM editais e
                        WHERE e.concurso_id = c.identificador) AS editais,
                      (SELECT COALESCE(SUM(e.versao), 0) FROM editais e
                        WHERE e.concurso_id = c.identificador) AS versoes_editais,
                      (SELECT COUNT(*) FROM cargos_do_concurso ca
                        WHERE ca.concurso_id = c.identificador) AS cargos,
                      (SELECT COALESCE(SUM(ca.versao), 0)
                         FROM cargos_do_concurso ca
                        WHERE ca.concurso_id = c.identificador) AS versoes_cargos
                    FROM concursos c
                    WHERE c.identificador = ? AND c.usuario_id = ?
                    """, concurso, usuario);
        } catch (EmptyResultDataAccessException excecao) {
            throw new RecursoNaoEncontrado("CONCURSO_NAO_ENCONTRADO",
                    "Concurso nao encontrado.");
        }
    }

    private Map<String, Object> versaoDoRecurso(UUID usuario, UUID recurso) {
        List<Map<String, Object>> encontrados = banco.queryForList("""
                SELECT 'MATERIA' AS tipo, m.identificador, m.versao,
                       m.arquivada AS arquivado
                  FROM materias m
                 WHERE m.identificador = ? AND m.usuario_id = ?
                UNION ALL
                SELECT 'TOPICO' AS tipo, t.identificador, t.versao,
                       t.arquivado
                  FROM topicos_da_materia t
                  JOIN materias m ON m.identificador = t.materia_id
                 WHERE t.identificador = ? AND m.usuario_id = ?
                """, recurso, usuario, recurso, usuario);
        if (encontrados.size() != 1) {
            throw new RecursoNaoEncontrado("RECURSO_PARA_REUSO_NAO_ENCONTRADO",
                    "Recurso escolhido para reutilizacao nao encontrado.");
        }
        return encontrados.getFirst();
    }

    private List<Map<String, Object>> encontrarMaterias(UUID usuario,
            String nome) {
        String procurado = normalizador.normalizarNome(nome);
        if (procurado == null) return List.of();
        return banco.queryForList("""
                SELECT identificador, nome, versao, arquivada
                  FROM materias
                 WHERE usuario_id = ?
                 ORDER BY nome, identificador
                """, usuario).stream().filter(item -> Objects.equals(procurado,
                        normalizarNomeDoCandidato(item))).toList();
    }

    private List<Map<String, Object>> encontrarTopicos(UUID materia,
            UUID paiEsperado, String nome) {
        String procurado = normalizador.normalizarNome(nome);
        if (procurado == null) return List.of();
        return banco.queryForList("""
                SELECT identificador, topico_pai_id, nome, versao, arquivado
                  FROM topicos_da_materia
                 WHERE materia_id = ?
                 ORDER BY nome, identificador
                """, materia).stream().filter(item ->
                        Objects.equals(paiEsperado,
                                uuidDoBanco(item.get("topico_pai_id")))
                                && Objects.equals(procurado,
                                        normalizarNomeDoCandidato(item)))
                .toList();
    }

    private String normalizarNomeDoCandidato(Map<String, Object> candidato) {
        Object nome = candidato.get("nome");
        return normalizador.normalizarNome(
                nome == null ? null : nome.toString());
    }

    private UUID uuidDoBanco(Object valor) {
        if (valor == null) return null;
        return valor instanceof UUID identificador ? identificador
                : UUID.fromString(valor.toString());
    }

    private void validarMateriaEscolhida(UUID usuario, UUID materia) {
        Map<String, Object> versao = versaoDoRecurso(usuario, materia);
        if (!"MATERIA".equals(versao.get("tipo"))) {
            throw new RegraDeDominio("DECISAO_DE_MATERIA_INVALIDA",
                    "O recurso escolhido nao e uma materia do usuario.");
        }
        if (Boolean.TRUE.equals(versao.get("arquivado"))) {
            throw new RegraDeDominio("MATERIA_ARQUIVADA_NAO_REUTILIZAVEL",
                    "Restaure a materia arquivada antes de reutiliza-la.");
        }
    }

    private void validarTopicoEscolhido(UUID usuario, UUID materia,
            UUID topico, UUID paiEsperado) {
        if (materia == null) {
            throw new RegraDeDominio("MATERIA_REUTILIZADA_OBRIGATORIA",
                    "Reutilizar topico exige reutilizar a materia correspondente.");
        }
        List<Map<String, Object>> encontrados = banco.queryForList("""
                SELECT t.arquivado, t.topico_pai_id,
                       m.arquivada AS materia_arquivada
                  FROM topicos_da_materia t
                JOIN materias m ON m.identificador = t.materia_id
                WHERE t.identificador = ? AND t.materia_id = ?
                  AND m.usuario_id = ?
                """, topico, materia, usuario);
        if (encontrados.size() != 1) {
            throw new RegraDeDominio("DECISAO_DE_TOPICO_INVALIDA",
                    "O topico escolhido nao pertence a materia reutilizada.");
        }
        Map<String, Object> encontrado = encontrados.getFirst();
        if (Boolean.TRUE.equals(encontrado.get("arquivado"))
                || Boolean.TRUE.equals(encontrado.get("materia_arquivada"))) {
            throw new RegraDeDominio("TOPICO_ARQUIVADO_NAO_REUTILIZAVEL",
                    "Restaure a materia e o topico antes de reutiliza-los.");
        }
        if (!Objects.equals(paiEsperado,
                uuidDoBanco(encontrado.get("topico_pai_id")))) {
            throw new RegraDeDominio(
                    "HIERARQUIA_DO_TOPICO_REUTILIZADO_DIVERGENTE",
                    "O topico escolhido nao pertence ao pai esperado.");
        }
    }

    private List<ProvenienciaDaImportacaoDoEditalPersistida>
            criarProveniencias(UUID usuario, UUID importacao,
                    ExtracaoEstruturadaDoEdital extracao,
                    Map<String, UUID> alvos, ModoDaImportacaoDeEdital modo,
                    Set<String> chavesReutilizadas, OffsetDateTime agora) {
        List<ProvenienciaDaImportacaoDoEditalPersistida> resultado =
                new ArrayList<>();
        if (modo == ModoDaImportacaoDeEdital.CRIAR_NOVO) {
            adicionar(resultado, importacao, usuario, "CONCURSO",
                    alvos.get("concurso"), "nome", extracao.concurso().nome(),
                    agora);
            adicionar(resultado, importacao, usuario, "CONCURSO",
                    alvos.get("concurso"), "descricao",
                    extracao.concurso().descricao(), agora);
            adicionar(resultado, importacao, usuario, "CONCURSO",
                    alvos.get("concurso"), "orgao",
                    extracao.concurso().orgao(), agora);
            adicionar(resultado, importacao, usuario, "CONCURSO",
                    alvos.get("concurso"), "banca",
                    extracao.concurso().banca(), agora);
            adicionar(resultado, importacao, usuario, "CONCURSO",
                    alvos.get("concurso"), "dataPrevista",
                    extracao.concurso().dataPrevista(), agora);
        }
        adicionar(resultado, importacao, usuario, "EDITAL", alvos.get("edital"),
                "titulo", extracao.edital().titulo(), agora);
        adicionar(resultado, importacao, usuario, "EDITAL", alvos.get("edital"),
                "numero", extracao.edital().numero(), agora);
        adicionar(resultado, importacao, usuario, "EDITAL", alvos.get("edital"),
                "ano", extracao.edital().ano(), agora);
        adicionar(resultado, importacao, usuario, "EDITAL", alvos.get("edital"),
                "descricao", extracao.edital().descricao(), agora);
        adicionar(resultado, importacao, usuario, "EDITAL", alvos.get("edital"),
                "dataDePublicacao", extracao.edital().dataDePublicacao(), agora);
        for (CargoExtraido cargo : extracao.cargos()) {
            adicionar(resultado, importacao, usuario, "CARGO",
                    alvos.get(cargo.chave()), "nome", cargo.nome(), agora);
            adicionar(resultado, importacao, usuario, "CARGO",
                    alvos.get(cargo.chave()), "area", cargo.area(), agora);
            adicionar(resultado, importacao, usuario, "CARGO",
                    alvos.get(cargo.chave()), "especialidade",
                    cargo.especialidade(), agora);
            adicionar(resultado, importacao, usuario, "CARGO",
                    alvos.get(cargo.chave()), "nivelDeEscolaridade",
                    cargo.nivelDeEscolaridade(), agora);
        }
        for (ProvaExtraida prova : extracao.provas()) {
            adicionar(resultado, importacao, usuario, "PROVA",
                    alvos.get(prova.chave()), "nome", prova.nome(), agora);
            adicionar(resultado, importacao, usuario, "PROVA",
                    alvos.get(prova.chave()), "tipo", prova.tipo(), agora);
            adicionar(resultado, importacao, usuario, "PROVA",
                    alvos.get(prova.chave()), "carater", prova.carater(), agora);
            adicionar(resultado, importacao, usuario, "PROVA",
                    alvos.get(prova.chave()), "dataHora", prova.dataHora(), agora);
            adicionar(resultado, importacao, usuario, "PROVA",
                    alvos.get(prova.chave()), "duracaoEmMinutos",
                    prova.duracaoEmMinutos(), agora);
            adicionar(resultado, importacao, usuario, "PROVA",
                    alvos.get(prova.chave()), "quantidadeDeQuestoes",
                    prova.quantidadeDeQuestoes(), agora);
            adicionar(resultado, importacao, usuario, "PROVA",
                    alvos.get(prova.chave()), "pontuacaoMaxima",
                    prova.pontuacaoMaxima(), agora);
            adicionar(resultado, importacao, usuario, "PROVA",
                    alvos.get(prova.chave()), "pontuacaoMinima",
                    prova.pontuacaoMinima(), agora);
            for (GrupoExtraido grupo : prova.grupos()) {
                adicionar(resultado, importacao, usuario, "GRUPO",
                        alvos.get(grupo.chave()), "nome", grupo.nome(), agora);
                adicionar(resultado, importacao, usuario, "GRUPO",
                        alvos.get(grupo.chave()), "quantidadeDeQuestoes",
                        grupo.quantidadeDeQuestoes(), agora);
                adicionar(resultado, importacao, usuario, "GRUPO",
                        alvos.get(grupo.chave()), "pontuacaoMaxima",
                        grupo.pontuacaoMaxima(), agora);
                adicionar(resultado, importacao, usuario, "GRUPO",
                        alvos.get(grupo.chave()), "pontuacaoMinima",
                        grupo.pontuacaoMinima(), agora);
            }
        }
        for (MateriaExtraida materia : extracao.materias()) {
            UUID materiaId = alvos.get(materia.chave());
            UUID materiaDaProvaId = alvos.get(
                    materia.chave() + ":materiaDaProva");
            if (!chavesReutilizadas.contains(materia.chave())) {
                adicionar(resultado, importacao, usuario, "MATERIA", materiaId,
                        "nome", materia.nome(), agora);
                adicionar(resultado, importacao, usuario, "MATERIA", materiaId,
                        "descricao", materia.descricao(), agora);
            }
            adicionar(resultado, importacao, usuario, "MATERIA_DA_PROVA",
                    materiaDaProvaId,
                    "peso", materia.peso(), agora);
            adicionar(resultado, importacao, usuario, "MATERIA_DA_PROVA",
                    materiaDaProvaId,
                    "quantidadeDeQuestoes", materia.quantidadeDeQuestoes(), agora);
            adicionar(resultado, importacao, usuario, "MATERIA_DA_PROVA",
                    materiaDaProvaId,
                    "pontuacaoMaxima", materia.pontuacaoMaxima(), agora);
            for (TopicoExtraido topico : materia.topicos()) {
                UUID topicoId = alvos.get(topico.chave());
                if (!chavesReutilizadas.contains(topico.chave())) {
                    adicionar(resultado, importacao, usuario, "TOPICO",
                            topicoId, "numeroOficial",
                            topico.numeroOficial(), agora);
                    adicionar(resultado, importacao, usuario, "TOPICO",
                            topicoId, "nome", topico.nome(), agora);
                    adicionar(resultado, importacao, usuario, "TOPICO",
                            topicoId, "descricao", topico.descricao(), agora);
                }
            }
            for (ItemExtraido item : materia.itensDoEdital()) {
                UUID itemId = alvos.get(item.chave());
                adicionar(resultado, importacao, usuario, "ITEM_DO_EDITAL",
                        itemId, "numeroOficial", item.numeroOficial(), agora);
                adicionar(resultado, importacao, usuario, "ITEM_DO_EDITAL",
                        itemId, "descricaoLiteral", item.descricaoLiteral(),
                        agora);
            }
        }
        return resultado;
    }

    private void adicionar(
            List<ProvenienciaDaImportacaoDoEditalPersistida> destino,
            UUID importacao, UUID usuario, String tipo, UUID recurso,
            String campo, ValorExtraido<?> valor, OffsetDateTime agora) {
        if (recurso == null || valor == null || valor.valor() == null) return;
        destino.add(new ProvenienciaDaImportacaoDoEditalPersistida(importacao,
                usuario, tipo, recurso, campo, valor.fonte(), valor.confianca(),
                valor.inferido(), agora));
    }

    private Map<String, Object> relatorioCompleto(
            ResultadoDaAplicacaoDaImportacao resultado,
            Map<String, Object> recibo, ExtracaoEstruturadaDoEdital extracao,
            List<ProblemaDaImportacao> problemas,
            Map<String, Object> propostaConfirmada,
            ModoDaImportacaoDeEdital modo,
            Map<String, UUID> recursosReutilizados,
            OffsetDateTime aplicadoEm) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("recibo", recibo);
        dados.put("identificadoresPorChave",
                resultado.identificadoresPorChave());
        Map<String, UUID> criados = new LinkedHashMap<>(
                resultado.identificadoresPorChave());
        recursosReutilizados.keySet().forEach(criados::remove);
        if (modo == ModoDaImportacaoDeEdital.COMPLEMENTAR_EXISTENTE) {
            criados.remove("concurso");
        }
        dados.put("identificadoresCriados", criados);
        List<Map<String, Object>> reutilizacoes = new ArrayList<>();
        if (modo == ModoDaImportacaoDeEdital.COMPLEMENTAR_EXISTENTE) {
            reutilizacoes.add(Map.of(
                    "tipo", "CONCURSO",
                    "chaveExtraida", "concurso",
                    "identificadorDoRecurso",
                    resultado.identificadorDoConcurso()));
        }
        recursosReutilizados.forEach((chave, recurso) ->
                reutilizacoes.add(Map.of(
                        "tipo", tipoDoRecursoReutilizado(extracao, chave),
                        "chaveExtraida", chave,
                        "identificadorDoRecurso", recurso)));
        dados.put("reutilizacoes", reutilizacoes);
        dados.put("versaoConfirmada", Map.of(
                "identificadorDaImportacao", texto(propostaConfirmada,
                        "identificadorDaImportacao"),
                "versaoDaExtracao", inteiro(propostaConfirmada,
                        "versaoDaExtracao"),
                "hashDaExtracao", texto(propostaConfirmada,
                        "hashDaExtracao"),
                "chaveDoCargoSelecionado", texto(propostaConfirmada,
                        "chaveDoCargoSelecionado"),
                "tentativaDaPreparacao", inteiro(propostaConfirmada,
                        "tentativaDaPreparacao")));
        dados.put("problemas", problemas);
        dados.put("incertezas", extracao.incertezas());
        dados.put("avisos", extracao.avisos());
        dados.put("aplicadoEm", aplicadoEm);
        dados.put("corPadraoDaMateria", "#475569");
        return dados;
    }

    private String tipoDoRecursoReutilizado(
            ExtracaoEstruturadaDoEdital extracao, String chave) {
        boolean materia = extracao.materias().stream().anyMatch(
                item -> item.chave().equals(chave));
        return materia ? "MATERIA" : "TOPICO";
    }

    private Map<String, Object> reciboDoRelatorio(
            RelatorioDaImportacaoDoEditalPersistido relatorio) {
        Object recibo = mapa(relatorio).get("recibo");
        if (!(recibo instanceof Map<?, ?> mapa)) {
            throw new IllegalStateException("Recibo persistido invalido.");
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        mapa.forEach((chave, valor) -> resultado.put(chave.toString(), valor));
        return resultado;
    }

    private Map<String, Object> mapa(
            RelatorioDaImportacaoDoEditalPersistido relatorio) {
        try {
            return mapeador.readValue(relatorio.dados(), MAPA);
        } catch (Exception excecao) {
            throw new IllegalStateException("Relatorio persistido invalido.",
                    excecao);
        }
    }

    private DecisoesDaImportacaoDoEdital decisoes(
            Map<String, Object> proposta) {
        Map<String, Object> mapa = mapa(proposta.get("decisoes"));
        Map<String, UUID> recursos = new LinkedHashMap<>();
        mapa(mapa.get("recursosParaReutilizar")).forEach((chave, valor) ->
                recursos.put(chave, UUID.fromString(valor.toString())));
        return new DecisoesDaImportacaoDoEdital(recursos,
                booleano(mapa, "definirEditalComoPrincipal"),
                booleano(mapa, "selecionarCargoCriado"));
    }

    private Map<String, UUID> reutilizacoes(Map<String, Object> proposta) {
        return decisoes(proposta).recursosParaReutilizar();
    }

    private ImportacaoDeEditalPersistida bloquear(UUID importacao,
            UUID usuario) {
        return importacoes.encontrarParaAtualizacao(importacao, usuario)
                .orElseThrow(
                        ServicoDePreparacaoDaImportacaoCompletaDoEdital
                                ::naoEncontrada);
    }

    private ExtracaoEstruturadaDoEdital exigirExtracao(
            ResultadoDoStagingDaImportacao staging) {
        if (staging.extracao() == null
                || staging.importacao().versaoAtualDaExtracao() < 1) {
            throw new RegraDeDominio("EXTRACAO_DA_IMPORTACAO_AUSENTE",
                    "Extraia e valide o edital antes de preparar a importacao.");
        }
        return staging.extracao();
    }

    private void exigirPreviaAplicavel(List<ProblemaDaImportacao> problemas) {
        List<ProblemaDaImportacao> pendentes = problemas.stream().filter(
                item -> item.severidade()
                        != SeveridadeDoProblemaDaImportacao.AVISO).toList();
        if (!pendentes.isEmpty()) {
            String codigo = pendentes.stream().anyMatch(item ->
                    item.severidade()
                            == SeveridadeDoProblemaDaImportacao.BLOQUEANTE)
                    ? "IMPORTACAO_POSSUI_PROBLEMAS_BLOQUEANTES"
                    : "IMPORTACAO_EXIGE_DECISOES";
            throw new RegraDeDominio(codigo,
                    "Resolva os problemas da previa antes de preparar a importacao.");
        }
    }

    private List<ProblemaDaImportacao> problemasDaFonte(
            List<ProblemaDaImportacao> problemas) {
        return problemas.stream().filter(item -> "fonte".equals(item.caminho())
                || item.severidade()
                        == SeveridadeDoProblemaDaImportacao.AVISO).toList();
    }

    private List<String> camposAusentes(List<ProblemaDaImportacao> problemas) {
        return problemas.stream().filter(item -> item.codigo().contains("SEM_")
                        || item.codigo().endsWith("_AUSENTE"))
                .map(ProblemaDaImportacao::caminho).filter(Objects::nonNull)
                .distinct().limit(100).toList();
    }

    private Map<String, Object> mapa(Object valor) {
        if (!(valor instanceof Map<?, ?> bruto)) {
            throw new IllegalArgumentException("Objeto esperado na proposta.");
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        bruto.forEach((chave, item) -> resultado.put(chave.toString(), item));
        return resultado;
    }

    private Map<String, Object> lerMapa(Object json) {
        try {
            return mapeador.readValue(json.toString(), MAPA);
        } catch (Exception excecao) {
            throw new IllegalStateException(
                    "Objeto JSON persistido invalido.", excecao);
        }
    }

    private int inteiroOuZero(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave);
        if (valor == null) return 0;
        return valor instanceof Number numero ? numero.intValue()
                : Integer.parseInt(valor.toString());
    }

    private List<String> listaDeTextos(Object valor) {
        if (!(valor instanceof Collection<?> colecao)) return List.of();
        return colecao.stream().filter(Objects::nonNull)
                .map(Object::toString).limit(100).toList();
    }

    private String texto(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave);
        if (valor == null || valor.toString().isBlank()) {
            throw new IllegalArgumentException(chave + " e obrigatorio.");
        }
        return valor.toString().strip();
    }

    private UUID uuid(Map<String, Object> mapa, String chave) {
        UUID valor = uuidOpcional(mapa, chave);
        if (valor == null) throw new IllegalArgumentException(
                chave + " e obrigatorio.");
        return valor;
    }

    private UUID uuidOpcional(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave);
        return valor == null ? null : UUID.fromString(valor.toString());
    }

    private int inteiro(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave);
        if (valor instanceof Number numero) return numero.intValue();
        return Integer.parseInt(texto(mapa, chave));
    }

    private boolean booleano(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave);
        if (valor instanceof Boolean resultado) return resultado;
        throw new IllegalArgumentException(chave + " deve ser booleano.");
    }

    private <T extends Enum<T>> T enumeracao(Map<String, Object> mapa,
            String chave, Class<T> tipo) {
        return Enum.valueOf(tipo, texto(mapa, chave));
    }

    private String jsonCanonico(Object valor) {
        try {
            return mapeador.writeValueAsString(ordenar(valor));
        } catch (Exception excecao) {
            throw new IllegalStateException("Falha ao serializar importacao.",
                    excecao);
        }
    }

    private Object ordenar(Object valor) {
        if (valor instanceof Map<?, ?> mapa) {
            Map<String, Object> ordenado = new TreeMap<>();
            mapa.forEach((chave, item) -> ordenado.put(chave.toString(),
                    ordenar(item)));
            return ordenado;
        }
        if (valor instanceof Collection<?> colecao) {
            return colecao.stream().map(this::ordenar).toList();
        }
        return valor;
    }

    private String nome(ValorExtraido<?> valor) {
        return valor == null || valor.valor() == null
                ? "campo ausente" : valor.valor().toString();
    }

    private ItemDaPreviaDaImportacao item(String tipo, String chave,
            String nome, UUID existente) {
        return new ItemDaPreviaDaImportacao(tipo, chave, nome, existente);
    }

    private static <T> List<T> limitar(List<T> valores, int limite) {
        return valores.size() <= limite ? List.copyOf(valores)
                : List.copyOf(valores.subList(0, limite));
    }

    private static List<ProblemaDaImportacao> unir(
            List<ProblemaDaImportacao> problemas) {
        Map<String, ProblemaDaImportacao> unicos = new LinkedHashMap<>();
        problemas.forEach(item -> unicos.putIfAbsent(item.severidade() + "\0"
                + item.codigo() + "\0" + item.caminho(), item));
        return List.copyOf(unicos.values());
    }

    private static RecursoNaoEncontrado naoEncontrada() {
        return new RecursoNaoEncontrado("IMPORTACAO_DE_EDITAL_NAO_ENCONTRADA",
                "Importacao de edital nao encontrada.");
    }

    private record AnaliseDaPrevia(
            ContagensDaImportacao contagens,
            List<ItemDaPreviaDaImportacao> itensACriar,
            List<ItemDaPreviaDaImportacao> itensAReutilizar,
            List<ProblemaDaImportacao> problemas) {
    }
}
