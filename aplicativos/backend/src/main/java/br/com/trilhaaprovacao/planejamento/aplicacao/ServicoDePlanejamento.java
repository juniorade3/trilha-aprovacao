package br.com.trilhaaprovacao.planejamento.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeMaterias;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeTopicos;
import br.com.trilhaaprovacao.conteudos.dominio.Materia;
import br.com.trilhaaprovacao.conteudos.dominio.TopicoDaMateria;
import br.com.trilhaaprovacao.estudos.aplicacao.ServicoDeMateriaisEEstudos;
import br.com.trilhaaprovacao.estudos.dominio.RegistroDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.BlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.DisponibilidadeDoDia;
import br.com.trilhaaprovacao.planejamento.dominio.ExecucaoDoBloco;
import br.com.trilhaaprovacao.planejamento.dominio.EstadoDoBlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.EstadoDoPlanoSemanal;
import br.com.trilhaaprovacao.planejamento.dominio.PlanoSemanal;
import br.com.trilhaaprovacao.planejamento.dominio.ResultadoDaExecucao;
import br.com.trilhaaprovacao.planejamento.infraestrutura.BlocoDeEstudoPersistido;
import br.com.trilhaaprovacao.planejamento.infraestrutura.DisponibilidadeDoDiaPersistida;
import br.com.trilhaaprovacao.planejamento.infraestrutura.ExecucaoDoBlocoPersistida;
import br.com.trilhaaprovacao.planejamento.infraestrutura.PlanoSemanalPersistido;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDeBlocosDeEstudo;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDeDisponibilidadesDoDia;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDeExecucoesDeBloco;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDePlanosSemanais;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDeReplanejamentos;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoDePlanejamento {
    private final RepositorioDePlanosSemanais planos;
    private final RepositorioDeDisponibilidadesDoDia disponibilidades;
    private final RepositorioDeBlocosDeEstudo blocos;
    private final RepositorioDeExecucoesDeBloco execucoes;
    private final ServicoDeMaterias materias;
    private final ServicoDeTopicos topicos;
    private final ServicoDeMateriaisEEstudos estudos;
    private final RepositorioDeReplanejamentos replanejamentos;

    public ServicoDePlanejamento(RepositorioDePlanosSemanais planos,
            RepositorioDeDisponibilidadesDoDia disponibilidades,
            RepositorioDeBlocosDeEstudo blocos,
            RepositorioDeExecucoesDeBloco execucoes,
            ServicoDeMaterias materias,
            ServicoDeTopicos topicos,
            ServicoDeMateriaisEEstudos estudos,
            RepositorioDeReplanejamentos replanejamentos) {
        this.planos = planos;
        this.disponibilidades = disponibilidades;
        this.blocos = blocos;
        this.execucoes = execucoes;
        this.materias = materias;
        this.topicos = topicos;
        this.estudos = estudos;
        this.replanejamentos = replanejamentos;
    }

    @Transactional
    public ResultadoDoPlanoSemanal criarPlanoSemanal(UUID usuario, LocalDate dataInicial) {
        if (planos.existsByIdentificadorDoUsuarioAndDataInicialAndEstadoNot(
                usuario, dataInicial, EstadoDoPlanoSemanal.CANCELADO)) {
            throw new ConflitoDeDominio("PLANO_SEMANAL_JA_EXISTE",
                    "Ja existe um plano para a semana informada.");
        }
        PlanoSemanal plano = regra("PLANO_SEMANAL_INVALIDO",
                () -> PlanoSemanal.criar(usuario, dataInicial));
        PlanoSemanal salvo = planos.saveAndFlush(new PlanoSemanalPersistido(plano)).paraDominio();
        List<DisponibilidadeDoDiaPersistida> dias = IntStream.range(0, 7)
                .mapToObj(indice -> regra("DISPONIBILIDADE_INVALIDA",
                        () -> DisponibilidadeDoDia.criar(salvo, salvo.dataInicial().plusDays(indice))))
                .map(DisponibilidadeDoDiaPersistida::new).toList();
        disponibilidades.saveAllAndFlush(dias);
        return resultado(salvo);
    }

    @Transactional(readOnly = true)
    public ResultadoDoPlanoSemanal obterPlanoSemanal(UUID usuario, LocalDate dataInicial) {
        PlanoSemanal plano = planos
                .findFirstByIdentificadorDoUsuarioAndDataInicialOrderByCriadoEmDesc(
                        usuario, dataInicial)
                .orElseThrow(this::naoEncontrado).paraDominio();
        return resultado(plano);
    }

    @Transactional
    public ResultadoDoPlanoSemanal alterarDisponibilidades(UUID usuario,
            UUID identificadorDoPlano, List<DisponibilidadeInformada> informadas) {
        PlanoSemanalPersistido planoPersistido = planos
                .findByIdentificadorAndIdentificadorDoUsuario(identificadorDoPlano, usuario)
                .orElseThrow(this::naoEncontrado);
        PlanoSemanal plano = planoPersistido.paraDominio();
        exigirPlanoAjustavel(plano);
        validarConjuntoDeDias(plano, informadas);
        validarCargaDoPlanoAtivo(plano, informadas);
        Map<LocalDate, DisponibilidadeInformada> porData = informadas.stream()
                .collect(Collectors.toMap(DisponibilidadeInformada::data, item -> item));
        List<DisponibilidadeDoDiaPersistida> persistidas = disponibilidades
                .findByIdentificadorDoPlanoOrderByDataAsc(identificadorDoPlano);
        if (persistidas.size() != 7) {
            throw new ConflitoDeDominio("DISPONIBILIDADES_INCONSISTENTES",
                    "O plano nao possui os sete dias esperados.");
        }
        for (DisponibilidadeDoDiaPersistida persistida : persistidas) {
            DisponibilidadeDoDia atual = persistida.paraDominio();
            DisponibilidadeDoDia atualizada = regra("DISPONIBILIDADE_INVALIDA",
                    () -> atual.alterarMinutos(porData.get(atual.data()).minutosDisponiveis()));
            persistida.atualizarDe(atualizada);
        }
        disponibilidades.flush();
        return resultado(plano);
    }

    @Transactional
    public ResultadoDoPlanoSemanal ativarPlanoSemanal(UUID usuario, UUID identificadorDoPlano) {
        PlanoSemanalPersistido persistido = planoPersistido(usuario, identificadorDoPlano);
        PlanoSemanal plano = persistido.paraDominio();
        if (plano.estaAtivo()) return resultado(plano);
        if (plano.estado() != EstadoDoPlanoSemanal.RASCUNHO) {
            throw new ConflitoDeDominio("PLANO_SEMANAL_NAO_ATIVAVEL",
                    "O estado atual do plano nao permite ativacao.");
        }
        ResultadoDoPlanoSemanal atual = resultado(plano);
        validarAtivacao(atual);
        replanejamentos.capturarSnapshot(identificadorDoPlano);
        PlanoSemanal ativo;
        try {
            ativo = plano.ativar();
        } catch (IllegalStateException excecao) {
            throw new ConflitoDeDominio("PLANO_SEMANAL_NAO_ATIVAVEL", excecao.getMessage());
        }
        persistido.atualizarDe(ativo);
        planos.flush();
        return resultado(persistido.paraDominio());
    }

    @Transactional
    public BlocoDeEstudo adicionarBloco(UUID usuario, UUID identificadorDoPlano,
            DadosDoBlocoDeEstudo dados) {
        PlanoSemanal plano = planoPersistido(usuario, identificadorDoPlano).paraDominio();
        exigirPlanoEditavel(plano);
        validarDadosDoBloco(usuario, plano, dados);
        List<BlocoDeEstudoPersistido> blocosDoDia = blocos
                .findByIdentificadorDoPlanoAndDataOrderByOrdemAsc(
                        identificadorDoPlano, dados.data());
        validarOrdemDeInsercao(dados.ordem(), blocosDoDia.size());
        BlocoDeEstudo criado = regra("BLOCO_DE_ESTUDO_INVALIDO",
                () -> BlocoDeEstudo.criar(identificadorDoPlano,
                        dados.identificadorDaMateria(), dados.identificadorDoTopico(),
                        dados.titulo(), dados.tipoDeAtividade(), dados.data(),
                        dados.duracaoPrevistaEmMinutos(), dados.ordem(),
                        dados.horarioPrevisto(), dados.observacao()));
        BlocoDeEstudoPersistido persistido = new BlocoDeEstudoPersistido(criado);
        blocosDoDia.add(dados.ordem() - 1, persistido);
        normalizarDia(blocosDoDia, dados.data());
        return blocos.saveAndFlush(persistido).paraDominio();
    }

    @Transactional
    public BlocoDeEstudo alterarBloco(UUID usuario, UUID identificadorDoBloco,
            DadosDoBlocoDeEstudo dados) {
        BlocoDeEstudoPersistido persistido = blocoPersistido(usuario, identificadorDoBloco);
        BlocoDeEstudo atual = persistido.paraDominio();
        PlanoSemanal plano = planoPersistido(usuario, atual.identificadorDoPlano()).paraDominio();
        exigirPlanoAjustavel(plano);
        if (plano.estaAtivo()
                && (!atual.data().equals(dados.data()) || atual.ordem() != dados.ordem())) {
            throw new ConflitoDeDominio("BLOCO_ATIVO_EXIGE_REAGENDAMENTO",
                    "Use o reagendamento para alterar a data ou a ordem do bloco ativo.");
        }
        validarDadosDoBloco(usuario, plano, dados);
        List<BlocoDeEstudoPersistido> origem = blocos
                .findByIdentificadorDoPlanoAndDataOrderByOrdemAsc(
                        atual.identificadorDoPlano(), atual.data());
        origem.removeIf(item -> item.paraDominio().identificador().equals(identificadorDoBloco));
        List<BlocoDeEstudoPersistido> destino = atual.data().equals(dados.data())
                ? origem
                : blocos.findByIdentificadorDoPlanoAndDataOrderByOrdemAsc(
                        atual.identificadorDoPlano(), dados.data());
        validarOrdemDeInsercao(dados.ordem(), destino.size());
        BlocoDeEstudo alterado;
        try {
            alterado = atual.alterarPlanejamento(dados.identificadorDaMateria(),
                    dados.identificadorDoTopico(), dados.titulo(),
                    dados.tipoDeAtividade(), dados.data(),
                    dados.duracaoPrevistaEmMinutos(), dados.ordem(),
                    dados.horarioPrevisto(), dados.observacao());
        } catch (IllegalStateException excecao) {
            throw new ConflitoDeDominio("BLOCO_DE_ESTUDO_NAO_EDITAVEL", excecao.getMessage());
        } catch (IllegalArgumentException excecao) {
            throw new RegraDeDominio("BLOCO_DE_ESTUDO_INVALIDO", excecao.getMessage());
        }
        persistido.atualizarDe(alterado);
        destino.add(dados.ordem() - 1, persistido);
        if (!atual.data().equals(dados.data())) normalizarDia(origem, atual.data());
        normalizarDia(destino, dados.data());
        blocos.flush();
        return persistido.paraDominio();
    }

    @Transactional
    public BlocoDeEstudo reagendarBloco(UUID usuario, UUID identificadorDoBloco,
            LocalDate data, java.time.LocalTime horario, int ordem) {
        BlocoDeEstudoPersistido persistido = blocoPersistido(usuario, identificadorDoBloco);
        BlocoDeEstudo atual = persistido.paraDominio();
        PlanoSemanal plano = planoPersistido(usuario, atual.identificadorDoPlano()).paraDominio();
        exigirPlanoAjustavel(plano);
        if (!plano.contem(data)) {
            throw new RegraDeDominio("DATA_DO_BLOCO_INVALIDA",
                    "O reagendamento deve permanecer na mesma semana.");
        }
        List<BlocoDeEstudoPersistido> origem = blocos
                .findByIdentificadorDoPlanoAndDataOrderByOrdemAsc(
                        atual.identificadorDoPlano(), atual.data());
        origem.removeIf(item -> item.paraDominio().identificador().equals(identificadorDoBloco)
                || item.paraDominio().estado() != EstadoDoBlocoDeEstudo.PLANEJADO);
        List<BlocoDeEstudoPersistido> destino = atual.data().equals(data)
                ? origem : blocos.findByIdentificadorDoPlanoAndDataOrderByOrdemAsc(
                        atual.identificadorDoPlano(), data);
        destino.removeIf(item -> item.paraDominio().estado()
                != EstadoDoBlocoDeEstudo.PLANEJADO);
        validarOrdemDeInsercao(ordem, destino.size());
        BlocoDeEstudo reagendado;
        try {
            reagendado = atual.reagendar(data, horario, ordem);
        } catch (IllegalStateException excecao) {
            throw new ConflitoDeDominio("BLOCO_DE_ESTUDO_NAO_REAGENDAVEL", excecao.getMessage());
        }
        persistido.atualizarDe(reagendado);
        destino.add(ordem - 1, persistido);
        if (!atual.data().equals(data)) normalizarDia(origem, atual.data());
        normalizarDia(destino, data);
        blocos.flush();
        return persistido.paraDominio();
    }

    @Transactional
    public BlocoDeEstudo cancelarBloco(UUID usuario, UUID identificadorDoBloco) {
        BlocoDeEstudoPersistido persistido = blocoPersistido(usuario, identificadorDoBloco);
        BlocoDeEstudo atual = persistido.paraDominio();
        PlanoSemanal plano = planoPersistido(usuario, atual.identificadorDoPlano()).paraDominio();
        exigirPlanoAjustavel(plano);
        try {
            persistido.atualizarDe(atual.cancelar());
        } catch (IllegalStateException excecao) {
            throw new ConflitoDeDominio("BLOCO_DE_ESTUDO_NAO_CANCELAVEL", excecao.getMessage());
        }
        List<BlocoDeEstudoPersistido> restantes = blocos
                .findByIdentificadorDoPlanoAndDataOrderByOrdemAsc(
                        atual.identificadorDoPlano(), atual.data());
        restantes.removeIf(item -> item.paraDominio().identificador().equals(identificadorDoBloco)
                || item.paraDominio().estado() != EstadoDoBlocoDeEstudo.PLANEJADO);
        normalizarDia(restantes, atual.data());
        blocos.flush();
        return persistido.paraDominio();
    }

    @Transactional
    public void excluirBloco(UUID usuario, UUID identificadorDoBloco) {
        BlocoDeEstudoPersistido persistido = blocoPersistido(usuario, identificadorDoBloco);
        BlocoDeEstudo bloco = persistido.paraDominio();
        PlanoSemanal plano = planoPersistido(usuario, bloco.identificadorDoPlano()).paraDominio();
        exigirPlanoEditavel(plano);
        if (bloco.estado() != EstadoDoBlocoDeEstudo.PLANEJADO) {
            throw new ConflitoDeDominio("BLOCO_DE_ESTUDO_NAO_EDITAVEL",
                    "Somente bloco planejado pode ser excluido.");
        }
        List<BlocoDeEstudoPersistido> restantes = blocos
                .findByIdentificadorDoPlanoAndDataOrderByOrdemAsc(
                        bloco.identificadorDoPlano(), bloco.data());
        restantes.removeIf(item -> item.paraDominio().identificador().equals(identificadorDoBloco));
        blocos.delete(persistido);
        normalizarDia(restantes, bloco.data());
        blocos.flush();
    }

    @Transactional
    public ResultadoDoPlanoSemanal reordenarBlocos(UUID usuario, UUID identificadorDoPlano,
            LocalDate data, List<UUID> identificadoresOrdenados) {
        PlanoSemanal plano = planoPersistido(usuario, identificadorDoPlano).paraDominio();
        exigirPlanoEditavel(plano);
        if (!plano.contem(data)) {
            throw new RegraDeDominio("DATA_DO_BLOCO_INVALIDA",
                    "A data deve pertencer a semana do plano.");
        }
        List<BlocoDeEstudoPersistido> blocosDoDia = blocos
                .findByIdentificadorDoPlanoAndDataOrderByOrdemAsc(identificadorDoPlano, data);
        if (identificadoresOrdenados == null
                || new HashSet<>(identificadoresOrdenados).size() != identificadoresOrdenados.size()
                || !blocosDoDia.stream().map(item -> item.paraDominio().identificador())
                        .collect(Collectors.toSet()).equals(new HashSet<>(identificadoresOrdenados))) {
            throw new RegraDeDominio("ORDEM_DOS_BLOCOS_INVALIDA",
                    "Informe todos os blocos do dia, sem repeticao.");
        }
        Map<UUID, BlocoDeEstudoPersistido> porIdentificador = blocosDoDia.stream()
                .collect(Collectors.toMap(
                        item -> item.paraDominio().identificador(), item -> item));
        List<BlocoDeEstudoPersistido> ordenados = identificadoresOrdenados.stream()
                .map(porIdentificador::get).toList();
        normalizarDia(ordenados, data);
        blocos.flush();
        return resultado(plano);
    }

    @Transactional
    public ResultadoDaExecucaoDoBloco iniciarBloco(UUID usuario,
            UUID identificadorDoBloco, LocalDate dataDeReferencia) {
        if (dataDeReferencia == null) {
            throw new RegraDeDominio("DATA_DE_REFERENCIA_INVALIDA",
                    "Informe a data local de referencia.");
        }
        BlocoDeEstudoPersistido blocoPersistido = blocoPersistido(usuario, identificadorDoBloco);
        BlocoDeEstudo bloco = blocoPersistido.paraDominio();
        PlanoSemanal plano = planoPersistido(usuario, bloco.identificadorDoPlano()).paraDominio();
        if (!plano.estaAtivo()) {
            throw new ConflitoDeDominio("PLANO_SEMANAL_NAO_ATIVO",
                    "Ative o plano antes de iniciar um bloco.");
        }
        if (bloco.data().isAfter(dataDeReferencia)) {
            throw new RegraDeDominio("BLOCO_FUTURO_NAO_EXECUTAVEL",
                    "Um bloco futuro precisa ser reagendado antes de iniciar.");
        }
        if (execucoes.findByIdentificadorDoBloco(identificadorDoBloco).isPresent()) {
            throw new ConflitoDeDominio("BLOCO_JA_POSSUI_EXECUCAO",
                    "O bloco ja possui uma execucao.");
        }
        execucoes.findByIdentificadorDoUsuarioAndEncerradaEmIsNull(usuario)
                .ifPresent(existente -> {
                    throw new ConflitoDeDominio("USUARIO_POSSUI_EXECUCAO_EM_ANDAMENTO",
                            "Conclua ou interrompa o bloco em andamento antes de iniciar outro.");
                });
        OffsetDateTime agora = OffsetDateTime.now();
        ExecucaoDoBloco execucao = regra("EXECUCAO_DO_BLOCO_INVALIDA",
                () -> ExecucaoDoBloco.iniciar(usuario, identificadorDoBloco, agora));
        BlocoDeEstudo iniciado;
        try {
            iniciado = bloco.iniciar();
        } catch (IllegalStateException excecao) {
            throw new ConflitoDeDominio("BLOCO_NAO_INICIAVEL", excecao.getMessage());
        }
        blocoPersistido.atualizarDe(iniciado);
        ExecucaoDoBlocoPersistida execucaoPersistida =
                execucoes.saveAndFlush(new ExecucaoDoBlocoPersistida(execucao));
        blocos.flush();
        return new ResultadoDaExecucaoDoBloco(blocoPersistido.paraDominio(),
                execucaoPersistida.paraDominio());
    }

    @Transactional
    public ResultadoDaExecucaoDoBloco concluirBloco(UUID usuario, UUID bloco,
            int duracao, String observacao, UUID identificadorDoTopico) {
        return finalizarBloco(usuario, bloco, ResultadoDaExecucao.CONCLUIDO,
                duracao, observacao, identificadorDoTopico);
    }

    @Transactional
    public ResultadoDaExecucaoDoBloco interromperBloco(UUID usuario, UUID bloco,
            int duracao, String observacao, UUID identificadorDoTopico) {
        return finalizarBloco(usuario, bloco,
                ResultadoDaExecucao.PARCIALMENTE_CONCLUIDO, duracao,
                observacao, identificadorDoTopico);
    }

    @Transactional
    public ResultadoDaExecucaoDoBloco corrigirExecucao(UUID usuario, UUID identificador,
            ResultadoDaExecucao resultado, int duracao, String observacao) {
        ExecucaoDoBlocoPersistida persistida = execucoes
                .findByIdentificadorAndIdentificadorDoUsuario(identificador, usuario)
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "EXECUCAO_DO_BLOCO_NAO_ENCONTRADA", "Execucao do bloco nao encontrada."));
        ExecucaoDoBloco atual = persistida.paraDominio();
        if (atual.estaEmAndamento()) {
            throw new ConflitoDeDominio("EXECUCAO_EM_ANDAMENTO_NAO_CORRIGIVEL",
                    "Interrompa ou conclua a execução antes de corrigi-la.");
        }
        UUID novoRegistro = atual.identificadorDoRegistroDeEstudo();
        RegistroDeEstudo estudo = null;
        if (novoRegistro != null) {
            RegistroDeEstudo anterior = estudos.obterEstudo(usuario, novoRegistro);
            estudo = estudos.corrigirEstudo(usuario, anterior.identificador(),
                    anterior.identificadorDoTopico(), anterior.identificadorDoMaterial(),
                    atual.iniciadaEm(), duracao, observacao);
            novoRegistro = estudo.identificador();
        }
        UUID registroCorrigido = novoRegistro;
        ExecucaoDoBloco corrigida = regra("EXECUCAO_DO_BLOCO_INVALIDA",
                () -> atual.corrigir(resultado, duracao, observacao,
                        registroCorrigido, OffsetDateTime.now()));
        persistida.atualizarDe(corrigida);
        BlocoDeEstudoPersistido bloco = blocoPersistido(usuario, atual.identificadorDoBloco());
        try {
            bloco.atualizarDe(bloco.paraDominio().corrigirResultado(resultado));
        } catch (IllegalStateException excecao) {
            throw new ConflitoDeDominio("BLOCO_DE_ESTUDO_NAO_CORRIGIVEL",
                    excecao.getMessage());
        }
        blocos.flush();
        execucoes.flush();
        return new ResultadoDaExecucaoDoBloco(bloco.paraDominio(), persistida.paraDominio(), estudo);
    }

    @Transactional
    public ResultadoDoPlanoSemanal encerrarPlano(UUID usuario, UUID identificadorDoPlano) {
        PlanoSemanalPersistido persistido = planoPersistido(usuario, identificadorDoPlano);
        impedirEstadoFinalComExecucaoAberta(usuario, identificadorDoPlano);
        PlanoSemanal encerrado;
        try {
            encerrado = persistido.paraDominio().encerrar();
        } catch (IllegalStateException excecao) {
            throw new ConflitoDeDominio("PLANO_SEMANAL_NAO_ENCERRAVEL", excecao.getMessage());
        }
        persistido.atualizarDe(encerrado);
        planos.flush();
        return resultado(persistido.paraDominio());
    }

    @Transactional
    public ResultadoDoPlanoSemanal cancelarPlano(UUID usuario, UUID identificadorDoPlano) {
        PlanoSemanalPersistido persistido = planoPersistido(usuario, identificadorDoPlano);
        impedirEstadoFinalComExecucaoAberta(usuario, identificadorDoPlano);
        PlanoSemanal cancelado;
        try {
            cancelado = persistido.paraDominio().cancelar();
        } catch (IllegalStateException excecao) {
            throw new ConflitoDeDominio("PLANO_SEMANAL_NAO_CANCELAVEL", excecao.getMessage());
        }
        for (BlocoDeEstudoPersistido bloco : blocos
                .findByIdentificadorDoPlanoOrderByDataAscOrdemAsc(identificadorDoPlano)) {
            if (bloco.paraDominio().estado() == EstadoDoBlocoDeEstudo.PLANEJADO) {
                bloco.atualizarDe(bloco.paraDominio().cancelar());
            }
        }
        persistido.atualizarDe(cancelado);
        blocos.flush();
        planos.flush();
        return resultado(persistido.paraDominio());
    }

    @Transactional(readOnly = true)
    public ResultadoDaExecucaoDoBloco obterExecucaoEmAndamento(UUID usuario) {
        ExecucaoDoBlocoPersistida execucao = execucoes
                .findByIdentificadorDoUsuarioAndEncerradaEmIsNull(usuario)
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "EXECUCAO_EM_ANDAMENTO_NAO_ENCONTRADA",
                        "Nao existe execucao em andamento."));
        BlocoDeEstudoPersistido bloco = blocoPersistido(usuario,
                execucao.paraDominio().identificadorDoBloco());
        return resultadoDaExecucao(usuario, bloco, execucao);
    }

    @Transactional(readOnly = true)
    public ResultadoDaExecucaoDoBloco obterExecucaoDoBloco(UUID usuario, UUID bloco) {
        BlocoDeEstudoPersistido encontrado = blocoPersistido(usuario, bloco);
        ExecucaoDoBlocoPersistida execucao = execucoes.findByIdentificadorDoBloco(bloco)
                .filter(item -> item.paraDominio().identificadorDoUsuario().equals(usuario))
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "EXECUCAO_DO_BLOCO_NAO_ENCONTRADA",
                        "Execucao do bloco nao encontrada."));
        return resultadoDaExecucao(usuario, encontrado, execucao);
    }

    @Transactional(readOnly = true)
    public List<TopicoDaMateria> listarTopicosParaRegistro(UUID usuario, UUID bloco) {
        BlocoDeEstudo encontrado = blocoPersistido(usuario, bloco).paraDominio();
        if (encontrado.identificadorDoTopico() != null) {
            return List.of(topicos.obter(usuario, encontrado.identificadorDoTopico()));
        }
        if (encontrado.identificadorDaMateria() == null) return List.of();
        return topicos.listar(usuario, encontrado.identificadorDaMateria(),
                "", false, 0, 500).getContent();
    }

    @Transactional
    public ResultadoDaExecucaoDoBloco registrarExecucaoNoHistorico(UUID usuario,
            UUID identificadorDaExecucao, UUID identificadorDoTopico) {
        ExecucaoDoBlocoPersistida execucao = execucoes
                .findByIdentificadorAndIdentificadorDoUsuario(
                        identificadorDaExecucao, usuario)
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "EXECUCAO_DO_BLOCO_NAO_ENCONTRADA",
                        "Execucao do bloco nao encontrada."));
        BlocoDeEstudoPersistido bloco = blocoPersistido(usuario,
                execucao.paraDominio().identificadorDoBloco());
        if (execucao.paraDominio().estaEmAndamento()) {
            throw new ConflitoDeDominio("EXECUCAO_AINDA_EM_ANDAMENTO",
                    "Encerre a execucao antes de registra-la no historico.");
        }
        RegistroDeEstudo estudo = integrarComHistorico(
                usuario, bloco.paraDominio(), execucao,
                identificadorDoTopico, true);
        return new ResultadoDaExecucaoDoBloco(
                bloco.paraDominio(), execucao.paraDominio(), estudo);
    }

    private ResultadoDaExecucaoDoBloco finalizarBloco(UUID usuario,
            UUID identificadorDoBloco, ResultadoDaExecucao resultado,
            int duracao, String observacao, UUID identificadorDoTopico) {
        BlocoDeEstudoPersistido blocoPersistido = blocoPersistido(
                usuario, identificadorDoBloco);
        BlocoDeEstudo bloco = blocoPersistido.paraDominio();
        ExecucaoDoBlocoPersistida execucaoPersistida = execucoes
                .findByIdentificadorDoBloco(identificadorDoBloco)
                .filter(item -> item.paraDominio().identificadorDoUsuario().equals(usuario))
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "EXECUCAO_DO_BLOCO_NAO_ENCONTRADA",
                        "Execucao do bloco nao encontrada."));
        ExecucaoDoBloco execucao = execucaoPersistida.paraDominio();
        if (!execucao.estaEmAndamento()) {
            if (!execucao.equivaleAoEncerramento(resultado, duracao, observacao)) {
                throw new ConflitoDeDominio("EXECUCAO_JA_ENCERRADA",
                        "A execucao ja foi encerrada com dados diferentes.");
            }
            RegistroDeEstudo estudo = integrarComHistorico(usuario, bloco,
                    execucaoPersistida, identificadorDoTopico, false);
            return new ResultadoDaExecucaoDoBloco(bloco,
                    execucaoPersistida.paraDominio(), estudo);
        }
        OffsetDateTime agora = OffsetDateTime.now();
        ExecucaoDoBloco encerrada = regra("EXECUCAO_DO_BLOCO_INVALIDA",
                () -> execucao.encerrar(resultado, duracao, observacao, agora));
        BlocoDeEstudo finalizado;
        try {
            finalizado = resultado == ResultadoDaExecucao.CONCLUIDO
                    ? bloco.concluir() : bloco.concluirParcialmente();
        } catch (IllegalStateException excecao) {
            throw new ConflitoDeDominio(
                    "BLOCO_NAO_FINALIZAVEL", excecao.getMessage());
        }
        execucaoPersistida.atualizarDe(encerrada);
        blocoPersistido.atualizarDe(finalizado);
        RegistroDeEstudo estudo = integrarComHistorico(usuario, finalizado,
                execucaoPersistida, identificadorDoTopico, false);
        execucoes.flush();
        blocos.flush();
        return new ResultadoDaExecucaoDoBloco(blocoPersistido.paraDominio(),
                execucaoPersistida.paraDominio(), estudo);
    }

    private RegistroDeEstudo integrarComHistorico(UUID usuario,
            BlocoDeEstudo bloco, ExecucaoDoBlocoPersistida persistida,
            UUID identificadorDoTopico, boolean exigirTopico) {
        ExecucaoDoBloco execucao = persistida.paraDominio();
        if (execucao.identificadorDoRegistroDeEstudo() != null) {
            return estudos.obterEstudo(
                    usuario, execucao.identificadorDoRegistroDeEstudo());
        }
        UUID topicoResolvido = resolverTopico(
                usuario, bloco, identificadorDoTopico);
        if (topicoResolvido == null) {
            if (exigirTopico) {
                throw new RegraDeDominio("TOPICO_NAO_INFORMADO",
                        "Selecione um topico para registrar o estudo.");
            }
            return null;
        }
        RegistroDeEstudo estudo = estudos.registrarEstudo(usuario,
                topicoResolvido, null, execucao.iniciadaEm(),
                execucao.duracaoExecutadaEmMinutos(),
                observacaoDoEstudo(bloco, execucao));
        ExecucaoDoBloco vinculada;
        try {
            vinculada = execucao.vincularRegistroDeEstudo(
                    estudo.identificador(), OffsetDateTime.now());
        } catch (IllegalStateException excecao) {
            throw new ConflitoDeDominio(
                    "EXECUCAO_JA_VINCULADA", excecao.getMessage());
        }
        persistida.atualizarDe(vinculada);
        execucoes.flush();
        return estudo;
    }

    private UUID resolverTopico(UUID usuario, BlocoDeEstudo bloco,
            UUID identificadorInformado) {
        if (bloco.identificadorDoTopico() != null) {
            if (identificadorInformado != null
                    && !bloco.identificadorDoTopico().equals(identificadorInformado)) {
                throw new RegraDeDominio("TOPICO_DIVERGENTE_DO_PLANEJADO",
                        "O bloco ja possui um topico planejado.");
            }
            topicos.obter(usuario, bloco.identificadorDoTopico());
            return bloco.identificadorDoTopico();
        }
        if (bloco.identificadorDaMateria() == null) {
            if (identificadorInformado != null) {
                throw new RegraDeDominio("TOPICO_NAO_APLICAVEL",
                        "Atividade livre nao possui materia para vincular o topico.");
            }
            return null;
        }
        if (identificadorInformado == null) return null;
        TopicoDaMateria topico = topicos.obter(usuario, identificadorInformado);
        if (!topico.identificadorDaMateria().equals(
                bloco.identificadorDaMateria())) {
            throw new RegraDeDominio("TOPICO_INCOMPATIVEL_COM_MATERIA",
                    "O topico deve pertencer a materia do bloco.");
        }
        return topico.identificador();
    }

    private String observacaoDoEstudo(BlocoDeEstudo bloco,
            ExecucaoDoBloco execucao) {
        String texto = execucao.observacao() == null
                ? bloco.titulo()
                : bloco.titulo() + " - " + execucao.observacao();
        return texto.length() <= 2000 ? texto : texto.substring(0, 2000);
    }

    private ResultadoDaExecucaoDoBloco resultadoDaExecucao(UUID usuario,
            BlocoDeEstudoPersistido bloco, ExecucaoDoBlocoPersistida execucao) {
        RegistroDeEstudo estudo = execucao.paraDominio()
                .identificadorDoRegistroDeEstudo() == null ? null
                : estudos.obterEstudo(usuario, execucao.paraDominio()
                        .identificadorDoRegistroDeEstudo());
        return new ResultadoDaExecucaoDoBloco(
                bloco.paraDominio(), execucao.paraDominio(), estudo);
    }

    private void validarConjuntoDeDias(PlanoSemanal plano,
            List<DisponibilidadeInformada> informadas) {
        if (informadas == null || informadas.size() != 7) {
            throw new RegraDeDominio("DISPONIBILIDADES_INVALIDAS",
                    "Informe a disponibilidade dos sete dias da semana.");
        }
        if (informadas.stream().anyMatch(item -> item == null || item.data() == null)) {
            throw new RegraDeDominio("DISPONIBILIDADES_INVALIDAS",
                    "Cada disponibilidade deve possuir uma data.");
        }
        Set<LocalDate> datas = informadas.stream().map(DisponibilidadeInformada::data)
                .collect(Collectors.toSet());
        Set<LocalDate> esperadas = IntStream.range(0, 7)
                .mapToObj(indice -> plano.dataInicial().plusDays(indice))
                .collect(Collectors.toCollection(HashSet::new));
        if (!datas.equals(esperadas)) {
            throw new RegraDeDominio("DISPONIBILIDADES_INVALIDAS",
                    "As datas devem corresponder aos sete dias da semana do plano.");
        }
    }

    private void validarCargaDoPlanoAtivo(PlanoSemanal plano,
            List<DisponibilidadeInformada> informadas) {
        if (!plano.estaAtivo()) return;
        Map<LocalDate, Integer> carga = blocos
                .findByIdentificadorDoPlanoOrderByDataAscOrdemAsc(plano.identificador()).stream()
                .map(BlocoDeEstudoPersistido::paraDominio)
                .filter(item -> item.estado() == EstadoDoBlocoDeEstudo.PLANEJADO)
                .collect(Collectors.groupingBy(BlocoDeEstudo::data,
                        Collectors.summingInt(BlocoDeEstudo::duracaoPrevistaEmMinutos)));
        for (DisponibilidadeInformada informada : informadas) {
            int minima = carga.getOrDefault(informada.data(), 0);
            if (informada.minutosDisponiveis() < minima) {
                throw new ConflitoDeDominio("DISPONIBILIDADE_ABAIXO_DA_CARGA_PLANEJADA",
                        "A disponibilidade de " + informada.data()
                                + " deve ser de pelo menos " + minima + " minutos.");
            }
        }
    }

    private void impedirEstadoFinalComExecucaoAberta(UUID usuario, UUID plano) {
        execucoes.findByIdentificadorDoUsuarioAndEncerradaEmIsNull(usuario)
                .ifPresent(execucao -> {
                    BlocoDeEstudo bloco = blocoPersistido(usuario,
                            execucao.paraDominio().identificadorDoBloco()).paraDominio();
                    if (bloco.identificadorDoPlano().equals(plano)) {
                        throw new ConflitoDeDominio("PLANO_POSSUI_EXECUCAO_EM_ANDAMENTO",
                                "Conclua ou interrompa o bloco em andamento.");
                    }
                });
    }

    private void validarAtivacao(ResultadoDoPlanoSemanal resultado) {
        if (resultado.totalDeMinutosDisponiveis() == 0) {
            throw new RegraDeDominio("PLANO_SEMANAL_SEM_DISPONIBILIDADE",
                    "Informe disponibilidade positiva antes de ativar o plano.");
        }
        if (resultado.blocos().isEmpty()) {
            throw new RegraDeDominio("PLANO_SEMANAL_SEM_BLOCOS",
                    "Adicione pelo menos um bloco antes de ativar o plano.");
        }
        if (resultado.possuiExcesso()) {
            throw new RegraDeDominio("PLANO_SEMANAL_COM_EXCESSO",
                    "A carga planejada nao pode superar a disponibilidade do dia.");
        }
        for (var resumo : resultado.resumosDosDias()) {
            List<BlocoDeEstudo> blocosDoDia = resultado.blocos().stream()
                    .filter(bloco -> bloco.data().equals(resumo.data()))
                    .sorted(java.util.Comparator.comparingInt(BlocoDeEstudo::ordem)).toList();
            for (int indice = 0; indice < blocosDoDia.size(); indice++) {
                if (blocosDoDia.get(indice).ordem() != indice + 1) {
                    throw new RegraDeDominio("ORDEM_DOS_BLOCOS_INVALIDA",
                            "A ordem dos blocos deve ser continua em cada dia.");
                }
            }
        }
        if (resultado.blocos().stream()
                .anyMatch(bloco -> !resultado.plano().contem(bloco.data()))) {
            throw new RegraDeDominio("DATA_DO_BLOCO_INVALIDA",
                    "Todos os blocos devem pertencer a semana do plano.");
        }
    }

    private ResultadoDoPlanoSemanal resultado(PlanoSemanal plano) {
        List<DisponibilidadeDoDia> dias = disponibilidades
                .findByIdentificadorDoPlanoOrderByDataAsc(plano.identificador()).stream()
                .map(DisponibilidadeDoDiaPersistida::paraDominio).toList();
        List<BlocoDeEstudo> blocosDoPlano = blocos
                .findByIdentificadorDoPlanoOrderByDataAscOrdemAsc(plano.identificador())
                .stream().map(BlocoDeEstudoPersistido::paraDominio).toList();
        return new ResultadoDoPlanoSemanal(plano, dias, blocosDoPlano);
    }

    private void validarDadosDoBloco(
            UUID usuario, PlanoSemanal plano, DadosDoBlocoDeEstudo dados) {
        if (dados == null || !plano.contem(dados.data())) {
            throw new RegraDeDominio("DATA_DO_BLOCO_INVALIDA",
                    "A data deve pertencer a semana do plano.");
        }
        if (dados.identificadorDoTopico() != null
                && dados.identificadorDaMateria() == null) {
            throw new RegraDeDominio("CONTEUDO_DO_BLOCO_INVALIDO",
                    "Topico exige uma materia.");
        }
        if (dados.identificadorDaMateria() == null) return;
        Materia materia = materias.obter(usuario, dados.identificadorDaMateria());
        if (materia.arquivada()) {
            throw new RegraDeDominio("MATERIA_ARQUIVADA",
                    "Restaure a materia antes de planejar o bloco.");
        }
        if (dados.identificadorDoTopico() == null) return;
        TopicoDaMateria topico = topicos.obter(usuario, dados.identificadorDoTopico());
        if (!topico.identificadorDaMateria().equals(dados.identificadorDaMateria())) {
            throw new RegraDeDominio("TOPICO_INCOMPATIVEL_COM_MATERIA",
                    "O topico deve pertencer a materia selecionada.");
        }
        if (topico.arquivado()) {
            throw new RegraDeDominio("TOPICO_ARQUIVADO",
                    "Restaure o topico antes de planejar o bloco.");
        }
    }

    private void validarOrdemDeInsercao(int ordem, int quantidadeExistente) {
        if (ordem < 1 || ordem > quantidadeExistente + 1) {
            throw new RegraDeDominio("ORDEM_DO_BLOCO_INVALIDA",
                    "A ordem deve indicar uma posicao valida no dia.");
        }
    }

    private void normalizarDia(List<BlocoDeEstudoPersistido> persistidos, LocalDate data) {
        List<BlocoDeEstudoPersistido> copia = new ArrayList<>(persistidos);
        for (int indice = 0; indice < copia.size(); indice++) {
            BlocoDeEstudoPersistido persistido = copia.get(indice);
            BlocoDeEstudo atual = persistido.paraDominio();
            int ordem = indice + 1;
            if (!atual.data().equals(data) || atual.ordem() != ordem) {
                try {
                    persistido.atualizarDe(atual.normalizarPosicao(data, ordem));
                } catch (IllegalStateException excecao) {
                    throw new ConflitoDeDominio(
                            "BLOCO_DE_ESTUDO_NAO_EDITAVEL", excecao.getMessage());
                }
            }
        }
    }

    private PlanoSemanalPersistido planoPersistido(UUID usuario, UUID plano) {
        return planos.findByIdentificadorAndIdentificadorDoUsuario(plano, usuario)
                .orElseThrow(this::naoEncontrado);
    }

    private BlocoDeEstudoPersistido blocoPersistido(UUID usuario, UUID bloco) {
        return blocos.encontrarDoUsuario(bloco, usuario)
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "BLOCO_DE_ESTUDO_NAO_ENCONTRADO", "Bloco de estudo nao encontrado."));
    }

    private void exigirPlanoAjustavel(PlanoSemanal plano) {
        if (plano.estado() != EstadoDoPlanoSemanal.RASCUNHO
                && plano.estado() != EstadoDoPlanoSemanal.ATIVO) {
            throw new ConflitoDeDominio("PLANO_SEMANAL_NAO_EDITAVEL",
                    "Somente plano em rascunho ou ativo permite ajustes.");
        }
    }

    private void exigirPlanoEditavel(PlanoSemanal plano) {
        try {
            plano.exigirEditavel();
        } catch (IllegalStateException excecao) {
            throw new ConflitoDeDominio("PLANO_SEMANAL_NAO_EDITAVEL", excecao.getMessage());
        }
    }

    private <T> T regra(String codigo, Supplier<T> acao) {
        try {
            return acao.get();
        } catch (IllegalArgumentException excecao) {
            throw new RegraDeDominio(codigo, excecao.getMessage());
        }
    }

    private RecursoNaoEncontrado naoEncontrado() {
        return new RecursoNaoEncontrado("PLANO_SEMANAL_NAO_ENCONTRADO",
                "Plano semanal nao encontrado.");
    }
}
