package br.com.trilhaaprovacao.conteudos.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.conteudos.dominio.Materia;
import br.com.trilhaaprovacao.conteudos.dominio.TopicoDaMateria;
import br.com.trilhaaprovacao.conteudos.infraestrutura.RepositorioJpaDeTopicos;
import br.com.trilhaaprovacao.conteudos.infraestrutura.TopicoPersistido;
import br.com.trilhaaprovacao.conteudoprogramatico.infraestrutura.RepositorioDeMapeamentosDeItens;
import br.com.trilhaaprovacao.estudos.infraestrutura.RepositorioDeCoberturasDeTopicos;
import br.com.trilhaaprovacao.estudos.infraestrutura.RepositorioDeRegistrosDeEstudo;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoDeTopicos {
    private final RepositorioJpaDeTopicos topicos;
    private final RepositorioDeMapeamentosDeItens mapeamentos;
    private final RepositorioDeCoberturasDeTopicos coberturas;
    private final RepositorioDeRegistrosDeEstudo registros;
    private final ServicoDeMaterias materias;

    public ServicoDeTopicos(RepositorioJpaDeTopicos topicos,
            RepositorioDeMapeamentosDeItens mapeamentos,
            RepositorioDeCoberturasDeTopicos coberturas,
            RepositorioDeRegistrosDeEstudo registros,
            ServicoDeMaterias materias) {
        this.topicos = topicos;
        this.mapeamentos = mapeamentos;
        this.coberturas = coberturas;
        this.registros = registros;
        this.materias = materias;
    }

    @Transactional
    public TopicoDaMateria criar(UUID identificadorDoUsuario, UUID identificadorDaMateria,
            UUID identificadorDoTopicoPai, String nome, String descricao, int ordem) {
        Materia materia = materias.obter(identificadorDoUsuario, identificadorDaMateria);
        validarMateriaAtiva(materia);
        validarPai(identificadorDaMateria, identificadorDoTopicoPai, null);
        TopicoDaMateria topico = criarDominio(
                identificadorDaMateria, identificadorDoTopicoPai, nome, descricao, ordem);
        validarNomeDisponivel(topico, null);
        return topicos.save(new TopicoPersistido(topico)).paraDominio();
    }

    @Transactional(readOnly = true)
    public Page<TopicoDaMateria> listar(UUID identificadorDoUsuario, UUID identificadorDaMateria,
            String pesquisa, boolean incluirArquivados, int pagina, int tamanho) {
        materias.obter(identificadorDoUsuario, identificadorDaMateria);
        var paginacao = PageRequest.of(pagina, tamanho,
                Sort.by("ordem").ascending().and(Sort.by("nome").ascending()));
        String termo = pesquisa == null ? "" : pesquisa.trim();
        Page<TopicoPersistido> resultado = incluirArquivados
                ? topicos.findByIdentificadorDaMateriaAndNomeContainingIgnoreCase(
                        identificadorDaMateria, termo, paginacao)
                : topicos.findByIdentificadorDaMateriaAndArquivadoAndNomeContainingIgnoreCase(
                        identificadorDaMateria, false, termo, paginacao);
        return resultado.map(TopicoPersistido::paraDominio);
    }

    @Transactional(readOnly = true)
    public TopicoDaMateria obter(UUID identificadorDoUsuario, UUID identificador) {
        return obterPersistidoDoUsuario(identificadorDoUsuario, identificador).paraDominio();
    }

    @Transactional
    public TopicoDaMateria alterar(UUID identificadorDoUsuario, UUID identificador,
            UUID identificadorDoTopicoPai, String nome, String descricao, int ordem) {
        TopicoPersistido persistido = obterPersistidoDoUsuario(identificadorDoUsuario, identificador);
        Materia materia = materias.obter(identificadorDoUsuario, persistido.identificadorDaMateria());
        validarMateriaAtiva(materia);
        validarPai(persistido.identificadorDaMateria(), identificadorDoTopicoPai, identificador);
        TopicoDaMateria topico = persistido.paraDominio();
        try {
            topico.alterar(nome, descricao, identificadorDoTopicoPai, ordem);
        } catch (IllegalArgumentException | IllegalStateException excecao) {
            throw new RegraDeDominio("TOPICO_INVALIDO", excecao.getMessage());
        }
        validarNomeDisponivel(topico, identificador);
        persistido.atualizarDe(topico);
        return persistido.paraDominio();
    }

    @Transactional
    public TopicoDaMateria definirArquivamento(UUID identificadorDoUsuario, UUID identificador, boolean arquivado) {
        TopicoPersistido persistido = obterPersistidoDoUsuario(identificadorDoUsuario, identificador);
        if (!arquivado) {
            validarMateriaAtiva(materias.obter(identificadorDoUsuario, persistido.identificadorDaMateria()));
        }
        TopicoDaMateria topico = persistido.paraDominio();
        topico.definirArquivamento(arquivado);
        persistido.atualizarDe(topico);
        return persistido.paraDominio();
    }

    @Transactional
    public void excluir(UUID identificadorDoUsuario, UUID identificador) {
        TopicoPersistido persistido = obterPersistidoDoUsuario(identificadorDoUsuario, identificador);
        if (topicos.existsByIdentificadorDoTopicoPai(identificador)) {
            throw new ConflitoDeDominio("TOPICO_POSSUI_FILHOS",
                    "Arquive o topico, pois ele possui topicos filhos.");
        }
        if (mapeamentos.existsByIdentificadorDoTopicoDaMateria(identificador)) {
            throw new ConflitoDeDominio("TOPICO_POSSUI_MAPEAMENTOS",
                    "Remova os mapeamentos antes de excluir o topico.");
        }
        if (coberturas.existsByIdentificadorDoTopico(identificador)
                || registros.existsByIdentificadorDoTopico(identificador)) {
            throw new ConflitoDeDominio("TOPICO_POSSUI_HISTORICO_DE_ESTUDO",
                    "Arquive o topico, pois ele possui materiais ou estudos vinculados.");
        }
        topicos.delete(persistido);
    }

    private TopicoPersistido obterPersistidoDoUsuario(UUID identificadorDoUsuario, UUID identificador) {
        return topicos.encontrarDoUsuario(identificador, identificadorDoUsuario)
                .orElseThrow(() -> new RecursoNaoEncontrado(
                        "TOPICO_NAO_ENCONTRADO", "Topico nao encontrado."));
    }

    private void validarPai(UUID identificadorDaMateria, UUID identificadorDoPai, UUID identificadorAtual) {
        if (identificadorDoPai == null) {
            return;
        }
        if (identificadorDoPai.equals(identificadorAtual)) {
            throw new RegraDeDominio("TOPICO_NAO_PODE_SER_PAI_DE_SI",
                    "Topico nao pode ser pai de si mesmo.");
        }
        TopicoPersistido cursor = topicos.findByIdentificadorAndIdentificadorDaMateria(
                        identificadorDoPai, identificadorDaMateria)
                .orElseThrow(() -> new RegraDeDominio(
                        "TOPICO_PAI_INVALIDO", "O topico-pai deve pertencer a mesma materia."));
        var visitados = new HashSet<UUID>();
        while (cursor != null && cursor.identificadorDoTopicoPai() != null) {
            if (!visitados.add(cursor.identificador())) {
                throw new RegraDeDominio("CICLO_DE_TOPICOS", "A arvore de topicos contem um ciclo.");
            }
            if (cursor.identificadorDoTopicoPai().equals(identificadorAtual)) {
                throw new RegraDeDominio("CICLO_DE_TOPICOS",
                        "Um topico nao pode ser movido para um de seus descendentes.");
            }
            cursor = topicos.findByIdentificadorAndIdentificadorDaMateria(
                    cursor.identificadorDoTopicoPai(), identificadorDaMateria).orElse(null);
        }
    }

    private TopicoDaMateria criarDominio(UUID materia, UUID pai, String nome, String descricao, int ordem) {
        try {
            return TopicoDaMateria.criar(materia, pai, nome, descricao, ordem);
        } catch (IllegalArgumentException excecao) {
            throw new RegraDeDominio("TOPICO_INVALIDO", excecao.getMessage());
        }
    }

    private void validarNomeDisponivel(TopicoDaMateria topico, UUID ignorado) {
        if (topicos.existeIrmaoComNome(topico.identificadorDaMateria(),
                topico.identificadorDoTopicoPai(), topico.nomeNormalizado(), ignorado)) {
            throw new ConflitoDeDominio("TOPICO_IRMAO_JA_CADASTRADO",
                    "Ja existe um topico com esse nome no mesmo nivel.");
        }
    }

    private void validarMateriaAtiva(Materia materia) {
        if (materia.arquivada()) {
            throw new RegraDeDominio("MATERIA_ARQUIVADA",
                    "Restaure a materia antes de alterar seus topicos.");
        }
    }
}
