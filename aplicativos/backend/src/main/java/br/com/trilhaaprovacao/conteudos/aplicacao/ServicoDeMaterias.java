package br.com.trilhaaprovacao.conteudos.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.conteudos.dominio.Materia;
import br.com.trilhaaprovacao.conteudos.infraestrutura.MateriaPersistida;
import br.com.trilhaaprovacao.conteudos.infraestrutura.RepositorioJpaDeMaterias;
import br.com.trilhaaprovacao.conteudos.infraestrutura.RepositorioJpaDeTopicos;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoDeMaterias {
    private final RepositorioJpaDeMaterias materias;
    private final RepositorioJpaDeTopicos topicos;

    public ServicoDeMaterias(RepositorioJpaDeMaterias materias, RepositorioJpaDeTopicos topicos) {
        this.materias = materias;
        this.topicos = topicos;
    }

    @Transactional
    public Materia criar(UUID identificadorDoUsuario, String nome, String descricao, String cor) {
        Materia materia = criarDominio(identificadorDoUsuario, nome, descricao, cor);
        validarNomeDisponivel(identificadorDoUsuario, materia.nomeNormalizado(), null);
        return materias.save(new MateriaPersistida(materia)).paraDominio();
    }

    @Transactional(readOnly = true)
    public Page<Materia> listar(UUID identificadorDoUsuario, String pesquisa,
            boolean incluirArquivadas, int pagina, int tamanho) {
        var paginacao = PageRequest.of(pagina, tamanho, Sort.by("nome").ascending());
        String termo = pesquisa == null ? "" : pesquisa.trim();
        Page<MateriaPersistida> resultado = incluirArquivadas
                ? materias.findByIdentificadorDoUsuarioAndNomeContainingIgnoreCase(
                        identificadorDoUsuario, termo, paginacao)
                : materias.findByIdentificadorDoUsuarioAndArquivadaAndNomeContainingIgnoreCase(
                        identificadorDoUsuario, false, termo, paginacao);
        return resultado.map(MateriaPersistida::paraDominio);
    }

    @Transactional(readOnly = true)
    public Materia obter(UUID identificadorDoUsuario, UUID identificador) {
        return obterPersistida(identificadorDoUsuario, identificador).paraDominio();
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> obterNomes(UUID identificadorDoUsuario,
            Set<UUID> identificadores) {
        if (identificadores == null || identificadores.isEmpty()) return Map.of();
        Map<UUID, String> encontradas = materias
                .findByIdentificadorDoUsuarioAndIdentificadorIn(
                        identificadorDoUsuario, identificadores)
                .stream()
                .map(MateriaPersistida::paraDominio)
                .collect(Collectors.toMap(Materia::identificador, Materia::nome));
        if (encontradas.size() != identificadores.size()) {
            throw new RecursoNaoEncontrado(
                    "MATERIA_NAO_ENCONTRADA", "Materia nao encontrada.");
        }
        return Map.copyOf(encontradas);
    }

    @Transactional
    public Materia alterar(UUID identificadorDoUsuario, UUID identificador,
            String nome, String descricao, String cor) {
        MateriaPersistida persistida = obterPersistida(identificadorDoUsuario, identificador);
        Materia materia = persistida.paraDominio();
        try {
            materia.alterar(nome, descricao, cor);
        } catch (IllegalArgumentException | IllegalStateException excecao) {
            throw new RegraDeDominio("MATERIA_INVALIDA", excecao.getMessage());
        }
        validarNomeDisponivel(identificadorDoUsuario, materia.nomeNormalizado(), identificador);
        persistida.atualizarDe(materia);
        return persistida.paraDominio();
    }

    @Transactional
    public Materia definirArquivamento(UUID identificadorDoUsuario, UUID identificador, boolean arquivada) {
        MateriaPersistida persistida = obterPersistida(identificadorDoUsuario, identificador);
        Materia materia = persistida.paraDominio();
        materia.definirArquivamento(arquivada);
        persistida.atualizarDe(materia);
        return persistida.paraDominio();
    }

    @Transactional
    public void excluir(UUID identificadorDoUsuario, UUID identificador) {
        MateriaPersistida persistida = obterPersistida(identificadorDoUsuario, identificador);
        if (topicos.existsByIdentificadorDaMateria(identificador)) {
            throw new ConflitoDeDominio("MATERIA_POSSUI_TOPICOS",
                    "Arquive a materia, pois ela possui topicos vinculados.");
        }
        materias.delete(persistida);
    }

    MateriaPersistida obterPersistida(UUID identificadorDoUsuario, UUID identificador) {
        return materias.findByIdentificadorAndIdentificadorDoUsuario(identificador, identificadorDoUsuario)
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "MATERIA_NAO_ENCONTRADA", "Materia nao encontrada."));
    }

    private Materia criarDominio(UUID identificadorDoUsuario, String nome, String descricao, String cor) {
        try {
            return Materia.criar(identificadorDoUsuario, nome, descricao, cor);
        } catch (IllegalArgumentException excecao) {
            throw new RegraDeDominio("MATERIA_INVALIDA", excecao.getMessage());
        }
    }

    private void validarNomeDisponivel(UUID identificadorDoUsuario, String nomeNormalizado, UUID ignorada) {
        boolean existe = ignorada == null
                ? materias.existsByIdentificadorDoUsuarioAndNomeNormalizado(identificadorDoUsuario, nomeNormalizado)
                : materias.existsByIdentificadorDoUsuarioAndNomeNormalizadoAndIdentificadorNot(
                        identificadorDoUsuario, nomeNormalizado, ignorada);
        if (existe) {
            throw new ConflitoDeDominio("MATERIA_JA_CADASTRADA",
                    "Ja existe uma materia com esse nome.");
        }
    }
}
