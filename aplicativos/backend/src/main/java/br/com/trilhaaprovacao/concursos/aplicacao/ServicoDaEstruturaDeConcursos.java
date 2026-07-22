package br.com.trilhaaprovacao.concursos.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.concursos.dominio.CaraterDaProva;
import br.com.trilhaaprovacao.concursos.dominio.CargoDoConcurso;
import br.com.trilhaaprovacao.concursos.dominio.Concurso;
import br.com.trilhaaprovacao.concursos.dominio.Edital;
import br.com.trilhaaprovacao.concursos.dominio.GrupoDeConteudo;
import br.com.trilhaaprovacao.concursos.dominio.MateriaDaProva;
import br.com.trilhaaprovacao.concursos.dominio.NivelDeEscolaridade;
import br.com.trilhaaprovacao.concursos.dominio.Prova;
import br.com.trilhaaprovacao.concursos.dominio.SituacaoDoConcurso;
import br.com.trilhaaprovacao.concursos.dominio.TipoDeProva;
import br.com.trilhaaprovacao.concursos.infraestrutura.CargoPersistido;
import br.com.trilhaaprovacao.concursos.infraestrutura.ConcursoPersistido;
import br.com.trilhaaprovacao.concursos.infraestrutura.EditalPersistido;
import br.com.trilhaaprovacao.concursos.infraestrutura.GrupoPersistido;
import br.com.trilhaaprovacao.concursos.infraestrutura.MateriaDaProvaPersistida;
import br.com.trilhaaprovacao.concursos.infraestrutura.ProvaPersistida;
import br.com.trilhaaprovacao.concursos.infraestrutura.RepositorioDeCargos;
import br.com.trilhaaprovacao.concursos.infraestrutura.RepositorioDeConcursos;
import br.com.trilhaaprovacao.concursos.infraestrutura.RepositorioDeEditais;
import br.com.trilhaaprovacao.concursos.infraestrutura.RepositorioDeGrupos;
import br.com.trilhaaprovacao.concursos.infraestrutura.RepositorioDeMateriasDaProva;
import br.com.trilhaaprovacao.concursos.infraestrutura.RepositorioDeProvas;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeMaterias;
import br.com.trilhaaprovacao.conteudoprogramatico.infraestrutura.RepositorioDeItensDoEdital;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoDaEstruturaDeConcursos {
    private final RepositorioDeConcursos concursos;
    private final RepositorioDeEditais editais;
    private final RepositorioDeCargos cargos;
    private final RepositorioDeProvas provas;
    private final RepositorioDeGrupos grupos;
    private final RepositorioDeMateriasDaProva materiasDaProva;
    private final RepositorioDeItensDoEdital itensDoEdital;
    private final ServicoDeMaterias materias;

    public ServicoDaEstruturaDeConcursos(
            RepositorioDeConcursos concursos,
            RepositorioDeEditais editais,
            RepositorioDeCargos cargos,
            RepositorioDeProvas provas,
            RepositorioDeGrupos grupos,
            RepositorioDeMateriasDaProva materiasDaProva,
            RepositorioDeItensDoEdital itensDoEdital,
            ServicoDeMaterias materias) {
        this.concursos = concursos;
        this.editais = editais;
        this.cargos = cargos;
        this.provas = provas;
        this.grupos = grupos;
        this.materiasDaProva = materiasDaProva;
        this.itensDoEdital = itensDoEdital;
        this.materias = materias;
    }

    @Transactional
    public Concurso criarConcurso(UUID usuario, String nome, String descricao, String orgao,
            String banca, SituacaoDoConcurso situacao, LocalDate dataPrevista) {
        Concurso concurso = regra("CONCURSO_INVALIDO",
                () -> Concurso.criar(usuario, nome, descricao, orgao, banca, situacao, dataPrevista));
        return concursos.save(new ConcursoPersistido(concurso)).paraDominio();
    }

    @Transactional(readOnly = true)
    public Page<Concurso> listarConcursos(UUID usuario, String pesquisa,
            boolean incluirArquivados, int pagina, int tamanho) {
        var paginacao = PageRequest.of(pagina, tamanho,
                Sort.by("ativo").descending().and(Sort.by("nome").ascending()));
        String termo = pesquisa == null ? "" : pesquisa.trim();
        Page<ConcursoPersistido> resultado = incluirArquivados
                ? concursos.findByIdentificadorDoUsuarioAndNomeContainingIgnoreCase(
                        usuario, termo, paginacao)
                : concursos.findByIdentificadorDoUsuarioAndSituacaoNotAndNomeContainingIgnoreCase(
                        usuario, SituacaoDoConcurso.ARQUIVADO, termo, paginacao);
        return resultado.map(ConcursoPersistido::paraDominio);
    }

    @Transactional(readOnly = true)
    public Concurso obterConcurso(UUID usuario, UUID identificador) {
        return concursoPersistido(usuario, identificador).paraDominio();
    }

    @Transactional
    public Concurso alterarConcurso(UUID usuario, UUID identificador, String nome,
            String descricao, String orgao, String banca, SituacaoDoConcurso situacao,
            LocalDate dataPrevista) {
        ConcursoPersistido persistido = concursoPersistido(usuario, identificador);
        Concurso alterado = regra("CONCURSO_INVALIDO",
                () -> persistido.paraDominio().alterar(
                        nome, descricao, orgao, banca, situacao, dataPrevista));
        persistido.atualizarDe(alterado);
        return persistido.paraDominio();
    }

    @Transactional
    public Concurso ativarConcurso(UUID usuario, UUID identificador) {
        ConcursoPersistido alvo = concursoPersistido(usuario, identificador);
        concursos.findByIdentificadorDoUsuarioAndAtivoTrue(usuario)
                .filter(atual -> !atual.identificador().equals(identificador))
                .ifPresent(atual -> atual.atualizarDe(atual.paraDominio().definirAtivacao(false)));
        concursos.flush();
        Concurso ativado = regra("CONCURSO_ARQUIVADO",
                () -> alvo.paraDominio().definirAtivacao(true));
        alvo.atualizarDe(ativado);
        concursos.flush();
        return alvo.paraDominio();
    }

    @Transactional
    public Concurso cancelarConcurso(UUID usuario, UUID identificador) {
        ConcursoPersistido persistido = concursoPersistido(usuario, identificador);
        Concurso atual = persistido.paraDominio();
        Concurso cancelado = regra("CONCURSO_INVALIDO", () -> atual.alterar(
                atual.nome(), atual.descricao(), atual.orgao(), atual.banca(),
                SituacaoDoConcurso.CANCELADO, atual.dataPrevistaPrincipal())
                .definirAtivacao(false));
        persistido.atualizarDe(cancelado);
        return persistido.paraDominio();
    }

    @Transactional
    public Concurso definirArquivamentoDoConcurso(
            UUID usuario, UUID identificador, boolean arquivado) {
        ConcursoPersistido persistido = concursoPersistido(usuario, identificador);
        persistido.atualizarDe(persistido.paraDominio().definirArquivamento(arquivado));
        return persistido.paraDominio();
    }

    @Transactional
    public void excluirConcurso(UUID usuario, UUID identificador) {
        ConcursoPersistido concurso = concursoPersistido(usuario, identificador);
        if (editais.existsByIdentificadorDoConcurso(identificador)
                || cargos.existsByIdentificadorDoConcurso(identificador)) {
            throw conflito("CONCURSO_POSSUI_DEPENDENCIAS",
                    "Arquive o concurso, pois ele possui editais ou cargos.");
        }
        concursos.delete(concurso);
    }

    @Transactional
    public Edital criarEdital(UUID usuario, UUID concurso, String titulo, String numero,
            Integer ano, String descricao, LocalDate data, String endereco) {
        exigirConcursoAlteravel(usuario, concurso);
        Edital edital = regra("EDITAL_INVALIDO",
                () -> Edital.criar(concurso, titulo, numero, ano, descricao, data, endereco));
        return editais.save(new EditalPersistido(edital)).paraDominio();
    }

    @Transactional(readOnly = true)
    public List<Edital> listarEditais(UUID usuario, UUID concurso) {
        concursoPersistido(usuario, concurso);
        return editais.findByIdentificadorDoConcursoOrderByPrincipalDescCriadoEmAsc(concurso)
                .stream().map(EditalPersistido::paraDominio).toList();
    }

    @Transactional(readOnly = true)
    public Edital obterEdital(UUID usuario, UUID identificador) {
        return editalPersistido(usuario, identificador).paraDominio();
    }

    @Transactional
    public Edital alterarEdital(UUID usuario, UUID identificador, String titulo, String numero,
            Integer ano, String descricao, LocalDate data, String endereco) {
        EditalPersistido persistido = editalPersistido(usuario, identificador);
        exigirConcursoAlteravel(usuario, persistido.identificadorDoConcurso());
        Edital alterado = regra("EDITAL_INVALIDO",
                () -> persistido.paraDominio().alterar(
                        titulo, numero, ano, descricao, data, endereco));
        persistido.atualizarDe(alterado);
        return persistido.paraDominio();
    }

    @Transactional
    public Edital definirEditalPrincipal(UUID usuario, UUID identificador) {
        EditalPersistido alvo = editalPersistido(usuario, identificador);
        exigirConcursoAlteravel(usuario, alvo.identificadorDoConcurso());
        editais.findByIdentificadorDoConcursoAndPrincipalTrue(alvo.identificadorDoConcurso())
                .filter(atual -> !atual.identificador().equals(identificador))
                .ifPresent(atual -> atual.atualizarDe(
                        atual.paraDominio().definirComoPrincipal(false)));
        editais.flush();
        alvo.atualizarDe(alvo.paraDominio().definirComoPrincipal(true));
        editais.flush();
        return alvo.paraDominio();
    }

    @Transactional
    public void excluirEdital(UUID usuario, UUID identificador) {
        EditalPersistido edital = editalPersistido(usuario, identificador);
        exigirConcursoAlteravel(usuario, edital.identificadorDoConcurso());
        if (itensDoEdital.existsByIdentificadorDoEdital(identificador)) {
            throw conflito("EDITAL_POSSUI_ITENS",
                    "O edital possui itens de conteudo e nao pode ser excluido.");
        }
        editais.delete(edital);
    }

    @Transactional
    public CargoDoConcurso criarCargo(UUID usuario, UUID concurso, String nome, String area,
            String especialidade, NivelDeEscolaridade nivel, int ordem) {
        exigirConcursoAlteravel(usuario, concurso);
        CargoDoConcurso cargo = regra("CARGO_INVALIDO",
                () -> CargoDoConcurso.criar(concurso, nome, area, especialidade, nivel, ordem));
        validarNomeDoCargo(cargo, null);
        return cargos.save(new CargoPersistido(cargo)).paraDominio();
    }

    @Transactional(readOnly = true)
    public List<CargoDoConcurso> listarCargos(UUID usuario, UUID concurso) {
        concursoPersistido(usuario, concurso);
        return cargos.findByIdentificadorDoConcursoOrderByOrdemAscNomeAsc(concurso)
                .stream().map(CargoPersistido::paraDominio).toList();
    }

    @Transactional(readOnly = true)
    public CargoDoConcurso obterCargo(UUID usuario, UUID identificador) {
        return cargoPersistido(usuario, identificador).paraDominio();
    }

    @Transactional
    public CargoDoConcurso alterarCargo(UUID usuario, UUID identificador, String nome,
            String area, String especialidade, NivelDeEscolaridade nivel, int ordem) {
        CargoPersistido persistido = cargoPersistido(usuario, identificador);
        exigirConcursoAlteravel(usuario, persistido.identificadorDoConcurso());
        CargoDoConcurso alterado = regra("CARGO_INVALIDO",
                () -> persistido.paraDominio().alterar(
                        nome, area, especialidade, nivel, ordem));
        validarNomeDoCargo(alterado, identificador);
        persistido.atualizarDe(alterado);
        return persistido.paraDominio();
    }

    @Transactional
    public CargoDoConcurso selecionarCargo(UUID usuario, UUID identificador) {
        CargoPersistido alvo = cargoPersistido(usuario, identificador);
        exigirConcursoAlteravel(usuario, alvo.identificadorDoConcurso());
        cargos.findByIdentificadorDoConcursoAndSelecionadoTrue(alvo.identificadorDoConcurso())
                .filter(atual -> !atual.identificador().equals(identificador))
                .ifPresent(atual -> atual.atualizarDe(
                        atual.paraDominio().definirSelecao(false)));
        cargos.flush();
        alvo.atualizarDe(alvo.paraDominio().definirSelecao(true));
        cargos.flush();
        return alvo.paraDominio();
    }

    @Transactional
    public void excluirCargo(UUID usuario, UUID identificador) {
        CargoPersistido cargo = cargoPersistido(usuario, identificador);
        exigirConcursoAlteravel(usuario, cargo.identificadorDoConcurso());
        if (provas.existsByIdentificadorDoCargo(identificador)) {
            throw conflito("CARGO_POSSUI_PROVAS",
                    "O cargo possui provas e nao pode ser excluido.");
        }
        cargos.delete(cargo);
    }

    @Transactional
    public Prova criarProva(UUID usuario, UUID cargo, String nome, TipoDeProva tipo,
            CaraterDaProva carater, int ordem, OffsetDateTime data, Integer duracao,
            Integer questoes, BigDecimal maxima, BigDecimal minima) {
        exigirConcursoAlteravelDoCargo(usuario, cargo);
        Prova prova = regra("PROVA_INVALIDA",
                () -> Prova.criar(cargo, nome, tipo, carater, ordem, data,
                        duracao, questoes, maxima, minima));
        validarNomeDaProva(prova, null);
        return provas.save(new ProvaPersistida(prova)).paraDominio();
    }

    @Transactional(readOnly = true)
    public List<Prova> listarProvas(UUID usuario, UUID cargo) {
        cargoPersistido(usuario, cargo);
        return provas.findByIdentificadorDoCargoOrderByOrdemAscNomeAsc(cargo)
                .stream().map(ProvaPersistida::paraDominio).toList();
    }

    @Transactional(readOnly = true)
    public Prova obterProva(UUID usuario, UUID identificador) {
        return provaPersistida(usuario, identificador).paraDominio();
    }

    @Transactional
    public Prova alterarProva(UUID usuario, UUID identificador, String nome,
            TipoDeProva tipo, CaraterDaProva carater, int ordem, OffsetDateTime data,
            Integer duracao, Integer questoes, BigDecimal maxima, BigDecimal minima) {
        ProvaPersistida persistida = provaPersistida(usuario, identificador);
        exigirConcursoAlteravelDoCargo(usuario, persistida.identificadorDoCargo());
        Prova alterada = regra("PROVA_INVALIDA",
                () -> persistida.paraDominio().alterar(nome, tipo, carater, ordem,
                        data, duracao, questoes, maxima, minima));
        validarNomeDaProva(alterada, identificador);
        persistida.atualizarDe(alterada);
        return persistida.paraDominio();
    }

    @Transactional
    public void excluirProva(UUID usuario, UUID identificador) {
        ProvaPersistida prova = provaPersistida(usuario, identificador);
        exigirConcursoAlteravelDoCargo(usuario, prova.identificadorDoCargo());
        if (grupos.existsByIdentificadorDaProva(identificador)) {
            throw conflito("PROVA_POSSUI_GRUPOS",
                    "A prova possui grupos e nao pode ser excluida.");
        }
        provas.delete(prova);
    }

    @Transactional
    public GrupoDeConteudo criarGrupo(UUID usuario, UUID prova, String nome, int ordem,
            Integer questoes, BigDecimal maxima, BigDecimal minima) {
        exigirConcursoAlteravelDaProva(usuario, prova);
        GrupoDeConteudo grupo = regra("GRUPO_INVALIDO",
                () -> GrupoDeConteudo.criar(prova, nome, ordem, questoes, maxima, minima));
        validarNomeDoGrupo(grupo, null);
        return grupos.save(new GrupoPersistido(grupo)).paraDominio();
    }

    @Transactional(readOnly = true)
    public List<GrupoDeConteudo> listarGrupos(UUID usuario, UUID prova) {
        provaPersistida(usuario, prova);
        return grupos.findByIdentificadorDaProvaOrderByOrdemAscNomeAsc(prova)
                .stream().map(GrupoPersistido::paraDominio).toList();
    }

    @Transactional(readOnly = true)
    public GrupoDeConteudo obterGrupo(UUID usuario, UUID identificador) {
        return grupoPersistido(usuario, identificador).paraDominio();
    }

    @Transactional
    public GrupoDeConteudo alterarGrupo(UUID usuario, UUID identificador,
            String nome, int ordem, Integer questoes, BigDecimal maxima, BigDecimal minima) {
        GrupoPersistido persistido = grupoPersistido(usuario, identificador);
        exigirConcursoAlteravelDaProva(usuario, persistido.identificadorDaProva());
        GrupoDeConteudo alterado = regra("GRUPO_INVALIDO",
                () -> persistido.paraDominio().alterar(
                        nome, ordem, questoes, maxima, minima));
        validarNomeDoGrupo(alterado, identificador);
        persistido.atualizarDe(alterado);
        return persistido.paraDominio();
    }

    @Transactional
    public void excluirGrupo(UUID usuario, UUID identificador) {
        GrupoPersistido grupo = grupoPersistido(usuario, identificador);
        exigirConcursoAlteravelDaProva(usuario, grupo.identificadorDaProva());
        if (materiasDaProva.existsByIdentificadorDoGrupoDeConteudo(identificador)) {
            throw conflito("GRUPO_POSSUI_MATERIAS",
                    "O grupo possui materias e nao pode ser excluido.");
        }
        grupos.delete(grupo);
    }

    @Transactional
    public MateriaDaProva criarMateriaDaProva(UUID usuario, UUID grupo, UUID materia,
            int ordem, BigDecimal peso, Integer questoes, BigDecimal pontuacaoMaxima) {
        exigirConcursoAlteravelDoGrupo(usuario, grupo);
        var materiaDoCatalogo = materias.obter(usuario, materia);
        if (materiaDoCatalogo.arquivada()) {
            throw new RegraDeDominio("MATERIA_ARQUIVADA",
                    "Restaure a materia antes de utiliza-la em uma prova.");
        }
        if (materiasDaProva.existsByIdentificadorDoGrupoDeConteudoAndIdentificadorDaMateria(
                grupo, materia)) {
            throw conflito("MATERIA_JA_UTILIZADA_NO_GRUPO",
                    "A materia ja esta vinculada a este grupo.");
        }
        MateriaDaProva vinculada = regra("MATERIA_DA_PROVA_INVALIDA",
                () -> MateriaDaProva.criar(
                        grupo, materia, ordem, peso, questoes, pontuacaoMaxima));
        return materiasDaProva.save(new MateriaDaProvaPersistida(vinculada)).paraDominio();
    }

    @Transactional(readOnly = true)
    public List<MateriaDaProva> listarMateriasDaProva(UUID usuario, UUID grupo) {
        grupoPersistido(usuario, grupo);
        return materiasDaProva
                .findByIdentificadorDoGrupoDeConteudoOrderByOrdemAsc(grupo)
                .stream().map(MateriaDaProvaPersistida::paraDominio).toList();
    }

    @Transactional(readOnly = true)
    public MateriaDaProva obterMateriaDaProva(UUID usuario, UUID identificador) {
        return materiaDaProvaPersistida(usuario, identificador).paraDominio();
    }

    @Transactional
    public MateriaDaProva alterarMateriaDaProva(UUID usuario, UUID identificador,
            int ordem, BigDecimal peso, Integer questoes, BigDecimal pontuacaoMaxima) {
        MateriaDaProvaPersistida persistida = materiaDaProvaPersistida(usuario, identificador);
        exigirConcursoAlteravelDoGrupo(
                usuario, persistida.identificadorDoGrupoDeConteudo());
        MateriaDaProva alterada = regra("MATERIA_DA_PROVA_INVALIDA",
                () -> persistida.paraDominio().alterar(
                        ordem, peso, questoes, pontuacaoMaxima));
        persistida.atualizarDe(alterada);
        return persistida.paraDominio();
    }

    @Transactional
    public void excluirMateriaDaProva(UUID usuario, UUID identificador) {
        MateriaDaProvaPersistida materia = materiaDaProvaPersistida(usuario, identificador);
        exigirConcursoAlteravelDoGrupo(
                usuario, materia.identificadorDoGrupoDeConteudo());
        if (itensDoEdital.existsByIdentificadorDaMateriaDaProva(identificador)) {
            throw conflito("MATERIA_DA_PROVA_POSSUI_ITENS",
                    "A materia da prova possui itens e nao pode ser excluida.");
        }
        materiasDaProva.delete(materia);
    }

    private ConcursoPersistido concursoPersistido(UUID usuario, UUID identificador) {
        return concursos.findByIdentificadorAndIdentificadorDoUsuario(identificador, usuario)
                .orElseThrow(() -> naoEncontrado("CONCURSO_NAO_ENCONTRADO", "Concurso"));
    }

    private EditalPersistido editalPersistido(UUID usuario, UUID identificador) {
        return editais.encontrarDoUsuario(identificador, usuario)
                .orElseThrow(() -> naoEncontrado("EDITAL_NAO_ENCONTRADO", "Edital"));
    }

    private CargoPersistido cargoPersistido(UUID usuario, UUID identificador) {
        return cargos.encontrarDoUsuario(identificador, usuario)
                .orElseThrow(() -> naoEncontrado("CARGO_NAO_ENCONTRADO", "Cargo"));
    }

    private ProvaPersistida provaPersistida(UUID usuario, UUID identificador) {
        return provas.encontrarDoUsuario(identificador, usuario)
                .orElseThrow(() -> naoEncontrado("PROVA_NAO_ENCONTRADA", "Prova"));
    }

    private GrupoPersistido grupoPersistido(UUID usuario, UUID identificador) {
        return grupos.encontrarDoUsuario(identificador, usuario)
                .orElseThrow(() -> naoEncontrado("GRUPO_NAO_ENCONTRADO", "Grupo"));
    }

    private MateriaDaProvaPersistida materiaDaProvaPersistida(
            UUID usuario, UUID identificador) {
        return materiasDaProva.encontrarDoUsuario(identificador, usuario)
                .orElseThrow(() -> naoEncontrado(
                        "MATERIA_DA_PROVA_NAO_ENCONTRADA", "Materia da prova"));
    }

    private Concurso exigirConcursoAlteravel(UUID usuario, UUID concurso) {
        Concurso encontrado = concursoPersistido(usuario, concurso).paraDominio();
        return regra("CONCURSO_ARQUIVADO", () -> {
            encontrado.exigirAtivoParaAlteracoes();
            return encontrado;
        });
    }

    private void exigirConcursoAlteravelDoCargo(UUID usuario, UUID cargo) {
        CargoPersistido encontrado = cargoPersistido(usuario, cargo);
        exigirConcursoAlteravel(usuario, encontrado.identificadorDoConcurso());
    }

    private void exigirConcursoAlteravelDaProva(UUID usuario, UUID prova) {
        ProvaPersistida encontrada = provaPersistida(usuario, prova);
        exigirConcursoAlteravelDoCargo(usuario, encontrada.identificadorDoCargo());
    }

    private void exigirConcursoAlteravelDoGrupo(UUID usuario, UUID grupo) {
        GrupoPersistido encontrado = grupoPersistido(usuario, grupo);
        exigirConcursoAlteravelDaProva(usuario, encontrado.identificadorDaProva());
    }

    private void validarNomeDoCargo(CargoDoConcurso cargo, UUID ignorado) {
        boolean existe = ignorado == null
                ? cargos.existsByIdentificadorDoConcursoAndNomeNormalizado(
                        cargo.identificadorDoConcurso(), cargo.nomeNormalizado())
                : cargos.existsByIdentificadorDoConcursoAndNomeNormalizadoAndIdentificadorNot(
                        cargo.identificadorDoConcurso(), cargo.nomeNormalizado(), ignorado);
        if (existe) {
            throw conflito("CARGO_DUPLICADO", "Ja existe um cargo com esse nome no concurso.");
        }
    }

    private void validarNomeDaProva(Prova prova, UUID ignorada) {
        boolean existe = ignorada == null
                ? provas.existsByIdentificadorDoCargoAndNomeNormalizado(
                        prova.identificadorDoCargo(), prova.nomeNormalizado())
                : provas.existsByIdentificadorDoCargoAndNomeNormalizadoAndIdentificadorNot(
                        prova.identificadorDoCargo(), prova.nomeNormalizado(), ignorada);
        if (existe) {
            throw conflito("PROVA_DUPLICADA", "Ja existe uma prova com esse nome no cargo.");
        }
    }

    private void validarNomeDoGrupo(GrupoDeConteudo grupo, UUID ignorado) {
        boolean existe = ignorado == null
                ? grupos.existsByIdentificadorDaProvaAndNomeNormalizado(
                        grupo.identificadorDaProva(), grupo.nomeNormalizado())
                : grupos.existsByIdentificadorDaProvaAndNomeNormalizadoAndIdentificadorNot(
                        grupo.identificadorDaProva(), grupo.nomeNormalizado(), ignorado);
        if (existe) {
            throw conflito("GRUPO_DUPLICADO", "Ja existe um grupo com esse nome na prova.");
        }
    }

    private <T> T regra(String codigo, Supplier<T> acao) {
        try {
            return acao.get();
        } catch (IllegalArgumentException | IllegalStateException excecao) {
            throw new RegraDeDominio(codigo, excecao.getMessage());
        }
    }

    private RecursoNaoEncontrado naoEncontrado(String codigo, String recurso) {
        return new RecursoNaoEncontrado(codigo, recurso + " nao encontrado.");
    }

    private ConflitoDeDominio conflito(String codigo, String mensagem) {
        return new ConflitoDeDominio(codigo, mensagem);
    }
}
