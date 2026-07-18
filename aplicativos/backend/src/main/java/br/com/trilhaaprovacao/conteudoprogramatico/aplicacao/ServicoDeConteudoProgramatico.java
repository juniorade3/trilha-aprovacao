package br.com.trilhaaprovacao.conteudoprogramatico.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
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
import br.com.trilhaaprovacao.conteudoprogramatico.dominio.ItemDoEdital;
import br.com.trilhaaprovacao.conteudoprogramatico.dominio.MapeamentoDeItemDoEdital;
import br.com.trilhaaprovacao.conteudoprogramatico.infraestrutura.ItemDoEditalPersistido;
import br.com.trilhaaprovacao.conteudoprogramatico.infraestrutura.MapeamentoDeItemDoEditalPersistido;
import br.com.trilhaaprovacao.conteudoprogramatico.infraestrutura.RepositorioDeItensDoEdital;
import br.com.trilhaaprovacao.conteudoprogramatico.infraestrutura.RepositorioDeMapeamentosDeItens;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeMaterias;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeTopicos;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoDeConteudoProgramatico {
    private final RepositorioDeItensDoEdital itens;
    private final RepositorioDeMapeamentosDeItens mapeamentos;
    private final RepositorioDeMateriasDaProva materiasDaProva;
    private final RepositorioDeGrupos grupos;
    private final RepositorioDeProvas provas;
    private final RepositorioDeCargos cargos;
    private final RepositorioDeConcursos concursos;
    private final RepositorioDeEditais editais;
    private final ServicoDeTopicos topicos;
    private final ServicoDeMaterias materias;

    public ServicoDeConteudoProgramatico(
            RepositorioDeItensDoEdital itens,
            RepositorioDeMapeamentosDeItens mapeamentos,
            RepositorioDeMateriasDaProva materiasDaProva,
            RepositorioDeGrupos grupos,
            RepositorioDeProvas provas,
            RepositorioDeCargos cargos,
            RepositorioDeConcursos concursos,
            RepositorioDeEditais editais,
            ServicoDeTopicos topicos,
            ServicoDeMaterias materias) {
        this.itens = itens;
        this.mapeamentos = mapeamentos;
        this.materiasDaProva = materiasDaProva;
        this.grupos = grupos;
        this.provas = provas;
        this.cargos = cargos;
        this.concursos = concursos;
        this.editais = editais;
        this.topicos = topicos;
        this.materias = materias;
    }

    @Transactional
    public ItemDoEdital criarItem(UUID usuario, UUID materiaDaProva, UUID edital,
            String descricaoOriginal, UUID itemPai, int ordem) {
        ContextoDaMateria contexto = contextoDaMateria(usuario, materiaDaProva, true);
        EditalPersistido editalEncontrado = editalPersistido(usuario, edital);
        if (!editalEncontrado.identificadorDoConcurso().equals(contexto.concurso())) {
            throw regra("EDITAL_DE_OUTRO_CONCURSO",
                    "O edital deve pertencer ao mesmo concurso da materia da prova.");
        }
        validarPai(materiaDaProva, edital, itemPai, null);
        ItemDoEdital item = executarRegra("ITEM_DO_EDITAL_INVALIDO",
                () -> ItemDoEdital.criar(
                        edital, materiaDaProva, descricaoOriginal, itemPai, ordem));
        return itens.save(new ItemDoEditalPersistido(item)).paraDominio();
    }

    @Transactional(readOnly = true)
    public List<ItemDoEdital> listarItens(UUID usuario, UUID materiaDaProva) {
        contextoDaMateria(usuario, materiaDaProva, false);
        List<ItemDoEdital> encontrados = itens
                .findByIdentificadorDaMateriaDaProvaOrderByOrdemAscCriadoEmAsc(
                        materiaDaProva)
                .stream().map(ItemDoEditalPersistido::paraDominio).toList();
        return ordenarEmArvore(encontrados);
    }

    @Transactional(readOnly = true)
    public ItemDoEdital obterItem(UUID usuario, UUID identificador) {
        return itemPersistido(usuario, identificador).paraDominio();
    }

    @Transactional
    public ItemDoEdital alterarItem(UUID usuario, UUID identificador,
            String descricaoOriginal, UUID itemPai, int ordem) {
        ItemDoEditalPersistido persistido = itemPersistido(usuario, identificador);
        contextoDaMateria(usuario, persistido.identificadorDaMateriaDaProva(), true);
        validarPai(persistido.identificadorDaMateriaDaProva(),
                persistido.identificadorDoEdital(), itemPai, identificador);
        ItemDoEdital alterado = executarRegra("ITEM_DO_EDITAL_INVALIDO",
                () -> persistido.paraDominio().alterar(descricaoOriginal, itemPai, ordem));
        persistido.atualizarDe(alterado);
        return persistido.paraDominio();
    }

    @Transactional
    public void excluirItem(UUID usuario, UUID identificador) {
        ItemDoEditalPersistido item = itemPersistido(usuario, identificador);
        contextoDaMateria(usuario, item.identificadorDaMateriaDaProva(), true);
        if (itens.existsByIdentificadorDoItemPai(identificador)) {
            throw conflito("ITEM_POSSUI_FILHOS",
                    "O item possui filhos e nao pode ser excluido.");
        }
        if (mapeamentos.existsByIdentificadorDoItemDoEdital(identificador)) {
            throw conflito("ITEM_POSSUI_MAPEAMENTOS",
                    "Remova os mapeamentos antes de excluir o item.");
        }
        itens.delete(item);
    }

    @Transactional
    public MapeamentoDeItemDoEdital criarMapeamento(
            UUID usuario, UUID item, UUID topico) {
        ItemDoEditalPersistido itemEncontrado = itemPersistido(usuario, item);
        ContextoDaMateria contexto = contextoDaMateria(
                usuario, itemEncontrado.identificadorDaMateriaDaProva(), true);
        var topicoEncontrado = topicos.obter(usuario, topico);
        if (!topicoEncontrado.identificadorDaMateria().equals(contexto.materiaDoCatalogo())) {
            throw regra("TOPICO_DE_OUTRA_MATERIA",
                    "O topico deve pertencer a mesma materia do item.");
        }
        if (topicoEncontrado.arquivado()) {
            throw regra("TOPICO_ARQUIVADO",
                    "Restaure o topico antes de utiliza-lo em um mapeamento.");
        }
        if (materias.obter(usuario, contexto.materiaDoCatalogo()).arquivada()) {
            throw regra("MATERIA_ARQUIVADA",
                    "Restaure a materia antes de criar um mapeamento.");
        }
        if (mapeamentos
                .existsByIdentificadorDoItemDoEditalAndIdentificadorDoTopicoDaMateria(
                        item, topico)) {
            throw conflito("MAPEAMENTO_DUPLICADO",
                    "O item ja esta mapeado para esse topico.");
        }
        MapeamentoDeItemDoEdital mapeamento =
                MapeamentoDeItemDoEdital.criarManual(item, topico);
        return mapeamentos.save(
                new MapeamentoDeItemDoEditalPersistido(mapeamento)).paraDominio();
    }

    @Transactional(readOnly = true)
    public List<MapeamentoDeItemDoEdital> listarMapeamentos(UUID usuario, UUID item) {
        itemPersistido(usuario, item);
        return mapeamentos.findByIdentificadorDoItemDoEditalOrderByCriadoEmAsc(item)
                .stream().map(MapeamentoDeItemDoEditalPersistido::paraDominio).toList();
    }

    @Transactional
    public void excluirMapeamento(UUID usuario, UUID item, UUID topico) {
        ItemDoEditalPersistido itemEncontrado = itemPersistido(usuario, item);
        contextoDaMateria(usuario, itemEncontrado.identificadorDaMateriaDaProva(), true);
        topicos.obter(usuario, topico);
        MapeamentoDeItemDoEditalPersistido mapeamento = mapeamentos
                .findByIdentificadorDoItemDoEditalAndIdentificadorDoTopicoDaMateria(
                        item, topico)
                .orElseThrow(() -> naoEncontrado(
                        "MAPEAMENTO_NAO_ENCONTRADO", "Mapeamento"));
        mapeamentos.delete(mapeamento);
    }

    private void validarPai(UUID materiaDaProva, UUID edital, UUID itemPai, UUID itemAtual) {
        if (itemPai == null) {
            return;
        }
        if (itemPai.equals(itemAtual)) {
            throw regra("ITEM_NAO_PODE_SER_PAI_DE_SI",
                    "Item nao pode ser pai de si mesmo.");
        }
        ItemDoEditalPersistido cursor = itens
                .findByIdentificadorAndIdentificadorDaMateriaDaProva(itemPai, materiaDaProva)
                .orElseThrow(() -> regra("ITEM_PAI_INVALIDO",
                        "O item-pai deve pertencer a mesma materia da prova."));
        if (!cursor.identificadorDoEdital().equals(edital)) {
            throw regra("ITEM_PAI_DE_OUTRO_EDITAL",
                    "O item-pai deve pertencer ao mesmo edital.");
        }
        var visitados = new HashSet<UUID>();
        while (cursor != null) {
            if (!visitados.add(cursor.identificador())) {
                throw regra("CICLO_DE_ITENS", "A arvore de itens contem um ciclo.");
            }
            if (cursor.identificador().equals(itemAtual)) {
                throw regra("CICLO_DE_ITENS",
                        "Um item nao pode ser movido para um de seus descendentes.");
            }
            UUID pai = cursor.identificadorDoItemPai();
            cursor = pai == null ? null : itens
                    .findByIdentificadorAndIdentificadorDaMateriaDaProva(
                            pai, materiaDaProva)
                    .orElse(null);
        }
    }

    private List<ItemDoEdital> ordenarEmArvore(List<ItemDoEdital> encontrados) {
        var ordenados = new ArrayList<ItemDoEdital>();
        var visitados = new HashSet<UUID>();
        encontrados.stream()
                .filter(item -> item.identificadorDoItemPai() == null)
                .forEach(item -> adicionarComFilhos(
                        item, encontrados, ordenados, visitados));
        encontrados.stream()
                .filter(item -> !visitados.contains(item.identificador()))
                .forEach(item -> adicionarComFilhos(
                        item, encontrados, ordenados, visitados));
        return List.copyOf(ordenados);
    }

    private void adicionarComFilhos(ItemDoEdital item, List<ItemDoEdital> encontrados,
            List<ItemDoEdital> ordenados, HashSet<UUID> visitados) {
        if (!visitados.add(item.identificador())) {
            return;
        }
        ordenados.add(item);
        encontrados.stream()
                .filter(filho -> Objects.equals(
                        filho.identificadorDoItemPai(), item.identificador()))
                .forEach(filho -> adicionarComFilhos(
                        filho, encontrados, ordenados, visitados));
    }

    private ContextoDaMateria contextoDaMateria(
            UUID usuario, UUID materiaDaProva, boolean exigirAlteravel) {
        MateriaDaProvaPersistida materia = materiasDaProva
                .encontrarDoUsuario(materiaDaProva, usuario)
                .orElseThrow(() -> naoEncontrado(
                        "MATERIA_DA_PROVA_NAO_ENCONTRADA", "Materia da prova"));
        GrupoPersistido grupo = grupos
                .encontrarDoUsuario(materia.identificadorDoGrupoDeConteudo(), usuario)
                .orElseThrow(() -> naoEncontrado("GRUPO_NAO_ENCONTRADO", "Grupo"));
        ProvaPersistida prova = provas
                .encontrarDoUsuario(grupo.identificadorDaProva(), usuario)
                .orElseThrow(() -> naoEncontrado("PROVA_NAO_ENCONTRADA", "Prova"));
        CargoPersistido cargo = cargos
                .encontrarDoUsuario(prova.identificadorDoCargo(), usuario)
                .orElseThrow(() -> naoEncontrado("CARGO_NAO_ENCONTRADO", "Cargo"));
        ConcursoPersistido concurso = concursos
                .findByIdentificadorAndIdentificadorDoUsuario(
                        cargo.identificadorDoConcurso(), usuario)
                .orElseThrow(() -> naoEncontrado(
                        "CONCURSO_NAO_ENCONTRADO", "Concurso"));
        if (exigirAlteravel) {
            executarRegra("CONCURSO_ARQUIVADO", () -> {
                concurso.paraDominio().exigirAtivoParaAlteracoes();
                return concurso;
            });
        }
        return new ContextoDaMateria(
                concurso.identificador(), materia.identificadorDaMateria());
    }

    private EditalPersistido editalPersistido(UUID usuario, UUID edital) {
        return editais.encontrarDoUsuario(edital, usuario)
                .orElseThrow(() -> naoEncontrado("EDITAL_NAO_ENCONTRADO", "Edital"));
    }

    private ItemDoEditalPersistido itemPersistido(UUID usuario, UUID item) {
        return itens.encontrarDoUsuario(item, usuario)
                .orElseThrow(() -> naoEncontrado(
                        "ITEM_DO_EDITAL_NAO_ENCONTRADO", "Item do edital"));
    }

    private <T> T executarRegra(String codigo, Supplier<T> acao) {
        try {
            return acao.get();
        } catch (IllegalArgumentException | IllegalStateException excecao) {
            throw regra(codigo, excecao.getMessage());
        }
    }

    private RegraDeDominio regra(String codigo, String mensagem) {
        return new RegraDeDominio(codigo, mensagem);
    }

    private ConflitoDeDominio conflito(String codigo, String mensagem) {
        return new ConflitoDeDominio(codigo, mensagem);
    }

    private RecursoNaoEncontrado naoEncontrado(String codigo, String recurso) {
        return new RecursoNaoEncontrado(codigo, recurso + " nao encontrado.");
    }

    private record ContextoDaMateria(UUID concurso, UUID materiaDoCatalogo) {
    }
}
