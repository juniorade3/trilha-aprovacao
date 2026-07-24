package br.com.trilhaaprovacao.estudos.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeMaterias;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeTopicos;
import br.com.trilhaaprovacao.conteudos.dominio.TopicoDaMateria;
import br.com.trilhaaprovacao.estudos.dominio.CoberturaDeTopicoPorMaterial;
import br.com.trilhaaprovacao.estudos.dominio.MaterialDeEstudo;
import br.com.trilhaaprovacao.estudos.dominio.RegistroDeEstudo;
import br.com.trilhaaprovacao.estudos.dominio.TipoDeMaterial;
import br.com.trilhaaprovacao.estudos.dominio.TipoDeEstudo;
import br.com.trilhaaprovacao.evidencias.aplicacao.DadosDaEvidencia;
import br.com.trilhaaprovacao.evidencias.aplicacao.ServicoDeEvidenciasDeAprendizagem;
import br.com.trilhaaprovacao.evidencias.dominio.EvidenciaDeAprendizagem;
import br.com.trilhaaprovacao.estudos.infraestrutura.CoberturaDeTopicoPersistida;
import br.com.trilhaaprovacao.estudos.infraestrutura.MaterialDeEstudoPersistido;
import br.com.trilhaaprovacao.estudos.infraestrutura.RegistroDeEstudoPersistido;
import br.com.trilhaaprovacao.estudos.infraestrutura.RepositorioDeCoberturasDeTopicos;
import br.com.trilhaaprovacao.estudos.infraestrutura.RepositorioDeMateriaisDeEstudo;
import br.com.trilhaaprovacao.estudos.infraestrutura.RepositorioDeRegistrosDeEstudo;
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
public class ServicoDeMateriaisEEstudos {
    private final RepositorioDeMateriaisDeEstudo materiais;
    private final RepositorioDeCoberturasDeTopicos coberturas;
    private final RepositorioDeRegistrosDeEstudo registros;
    private final ServicoDeTopicos topicos;
    private final ServicoDeMaterias materiasDoCatalogo;
    private final ServicoDeEvidenciasDeAprendizagem evidencias;

    public ServicoDeMateriaisEEstudos(
            RepositorioDeMateriaisDeEstudo materiais,
            RepositorioDeCoberturasDeTopicos coberturas,
            RepositorioDeRegistrosDeEstudo registros,
            ServicoDeTopicos topicos,
            ServicoDeMaterias materiasDoCatalogo,
            ServicoDeEvidenciasDeAprendizagem evidencias) {
        this.materiais = materiais;
        this.coberturas = coberturas;
        this.registros = registros;
        this.topicos = topicos;
        this.materiasDoCatalogo = materiasDoCatalogo;
        this.evidencias = evidencias;
    }

    @Transactional
    public MaterialDeEstudo criarMaterial(UUID usuario, String titulo, TipoDeMaterial tipo,
            String descricao, String fonte, String endereco, Integer duracao) {
        MaterialDeEstudo material = regra("MATERIAL_INVALIDO",
                () -> MaterialDeEstudo.criar(
                        usuario, titulo, tipo, descricao, fonte, endereco, duracao));
        return materiais.save(new MaterialDeEstudoPersistido(material)).paraDominio();
    }

    @Transactional(readOnly = true)
    public Page<MaterialDeEstudo> listarMateriais(UUID usuario, String pesquisa,
            boolean incluirArquivados, UUID identificadorDoTopico,
            int pagina, int tamanho) {
        var paginacao = PageRequest.of(pagina, tamanho, Sort.by("titulo").ascending());
        String termo = pesquisa == null ? "" : pesquisa.trim();
        Page<MaterialDeEstudoPersistido> resultado;
        if (identificadorDoTopico != null) {
            resultado = materiais.listarPorTopico(usuario, identificadorDoTopico,
                    termo, incluirArquivados, paginacao);
        } else {
            resultado = incluirArquivados
                    ? materiais.findByIdentificadorDoUsuarioAndTituloContainingIgnoreCase(
                            usuario, termo, paginacao)
                    : materiais
                            .findByIdentificadorDoUsuarioAndArquivadoAndTituloContainingIgnoreCase(
                                    usuario, false, termo, paginacao);
        }
        return resultado.map(MaterialDeEstudoPersistido::paraDominio);
    }

    @Transactional(readOnly = true)
    public List<MaterialRelacionadoAoTopico> listarMateriaisAtivosDosTopicos(
            UUID usuario, List<UUID> identificadoresDosTopicos) {
        if (identificadoresDosTopicos == null || identificadoresDosTopicos.isEmpty()) {
            return List.of();
        }
        return coberturas.listarMateriaisAtivosDosTopicos(
                        usuario, identificadoresDosTopicos.stream().distinct().toList())
                .stream()
                .map(item -> new MaterialRelacionadoAoTopico(
                        item.getIdentificadorDoTopico(),
                        item.getIdentificadorDoMaterial(),
                        item.getTituloDoMaterial()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MaterialDeEstudo obterMaterial(UUID usuario, UUID identificador) {
        return materialPersistido(usuario, identificador).paraDominio();
    }

    @Transactional
    public MaterialDeEstudo alterarMaterial(UUID usuario, UUID identificador,
            String titulo, TipoDeMaterial tipo, String descricao, String fonte,
            String endereco, Integer duracao) {
        MaterialDeEstudoPersistido persistido = materialPersistido(usuario, identificador);
        MaterialDeEstudo alterado = regra("MATERIAL_INVALIDO",
                () -> persistido.paraDominio().alterar(
                        titulo, tipo, descricao, fonte, endereco, duracao));
        persistido.atualizarDe(alterado);
        return persistido.paraDominio();
    }

    @Transactional
    public MaterialDeEstudo definirArquivamento(
            UUID usuario, UUID identificador, boolean arquivado) {
        MaterialDeEstudoPersistido persistido = materialPersistido(usuario, identificador);
        persistido.atualizarDe(
                persistido.paraDominio().definirArquivamento(arquivado));
        return persistido.paraDominio();
    }

    @Transactional
    public void excluirMaterial(UUID usuario, UUID identificador) {
        MaterialDeEstudoPersistido material = materialPersistido(usuario, identificador);
        if (coberturas.existsByIdentificadorDoMaterial(identificador)
                || registros.existsByIdentificadorDoMaterial(identificador)) {
            throw conflito("MATERIAL_POSSUI_HISTORICO",
                    "Arquive o material, pois ele possui topicos ou estudos vinculados.");
        }
        materiais.delete(material);
    }

    @Transactional
    public CoberturaDeTopicoPorMaterial adicionarCobertura(
            UUID usuario, UUID material, UUID topico) {
        MaterialDeEstudo encontrado = exigirMaterialAtivo(usuario, material);
        TopicoDaMateria topicoEncontrado = exigirTopicoAtivo(usuario, topico);
        if (!encontrado.identificadorDoUsuario().equals(usuario)) {
            throw naoEncontrado("MATERIAL_NAO_ENCONTRADO", "Material");
        }
        if (coberturas.existsByIdentificadorDoMaterialAndIdentificadorDoTopico(
                material, topico)) {
            throw conflito("COBERTURA_DUPLICADA",
                    "O material ja cobre esse topico.");
        }
        CoberturaDeTopicoPorMaterial cobertura =
                CoberturaDeTopicoPorMaterial.criar(
                        encontrado.identificador(), topicoEncontrado.identificador());
        return coberturas.save(
                new CoberturaDeTopicoPersistida(cobertura)).paraDominio();
    }

    @Transactional(readOnly = true)
    public List<CoberturaDeTopicoPorMaterial> listarCoberturas(
            UUID usuario, UUID material) {
        materialPersistido(usuario, material);
        return coberturas.findByIdentificadorDoMaterialOrderByCriadoEmAsc(material)
                .stream().map(CoberturaDeTopicoPersistida::paraDominio).toList();
    }

    @Transactional
    public void removerCobertura(UUID usuario, UUID material, UUID topico) {
        exigirMaterialAtivo(usuario, material);
        topicos.obter(usuario, topico);
        CoberturaDeTopicoPersistida cobertura = coberturas
                .findByIdentificadorDoMaterialAndIdentificadorDoTopico(material, topico)
                .orElseThrow(() -> naoEncontrado(
                        "COBERTURA_NAO_ENCONTRADA", "Cobertura"));
        coberturas.delete(cobertura);
    }

    @Transactional
    public RegistroDeEstudo registrarEstudo(UUID usuario, UUID topico, UUID material,
            OffsetDateTime dataHora, int duracao, String observacao) {
        return registrarEstudo(usuario, topico, material, TipoDeEstudo.OUTRA,
                dataHora, duracao, observacao, null, true);
    }

    @Transactional
    public RegistroDeEstudo registrarEstudo(UUID usuario, UUID topico, UUID material,
            TipoDeEstudo tipo, OffsetDateTime dataHora, int duracao, String observacao,
            DadosDaEvidencia dadosDaEvidencia, boolean exigirResultado) {
        validarEstudo(usuario, topico, material);
        TipoDeEstudo tipoEfetivo = tipo == null ? TipoDeEstudo.OUTRA : tipo;
        RegistroDeEstudo registro = regra("REGISTRO_DE_ESTUDO_INVALIDO",
                () -> RegistroDeEstudo.criar(
                        topico, material, tipoEfetivo, dataHora, duracao, observacao));
        RegistroDeEstudo salvo = registros.saveAndFlush(
                new RegistroDeEstudoPersistido(registro)).paraDominio();
        evidencias.registrar(usuario, topico, salvo.identificador(), tipoEfetivo,
                dadosDaEvidencia, exigirResultado);
        return salvo;
    }

    @Transactional(readOnly = true)
    public Page<RegistroDeEstudo> listarEstudos(
            UUID usuario, int pagina, int tamanho) {
        var paginacao = PageRequest.of(pagina, tamanho,
                Sort.by("dataHora").descending().and(Sort.by("criadoEm").descending()));
        return registros.listarDoUsuario(usuario, paginacao)
                .map(RegistroDeEstudoPersistido::paraDominio);
    }

    @Transactional(readOnly = true)
    public RegistroDeEstudo obterEstudo(UUID usuario, UUID identificador) {
        return registroPersistido(usuario, identificador).paraDominio();
    }

    @Transactional(readOnly = true)
    public EvidenciaDeAprendizagem obterEvidencia(UUID usuario, UUID registro) {
        registroPersistido(usuario, registro);
        return evidencias.obterPorRegistro(registro).orElse(null);
    }

    @Transactional
    public RegistroDeEstudo corrigirEstudo(UUID usuario, UUID identificador,
            UUID topico, UUID material, OffsetDateTime dataHora,
            int duracao, String observacao) {
        RegistroDeEstudo anterior = registroPersistido(usuario, identificador).paraDominio();
        return corrigirEstudo(usuario, identificador, topico, material,
                anterior.tipoDeEstudo(), dataHora, duracao, observacao, null, false);
    }

    @Transactional
    public RegistroDeEstudo corrigirEstudo(UUID usuario, UUID identificador,
            UUID topico, UUID material, TipoDeEstudo tipo, OffsetDateTime dataHora,
            int duracao, String observacao, DadosDaEvidencia dadosDaEvidencia,
            boolean exigirResultado) {
        RegistroDeEstudoPersistido original = registroPersistido(usuario, identificador);
        validarEstudo(usuario, topico, material);
        RegistroDeEstudo correcao = regra("REGISTRO_DE_ESTUDO_INVALIDO",
                () -> original.paraDominio().criarCorrecao(
                        topico, material, tipo, dataHora, duracao, observacao));
        RegistroDeEstudo encerrado = regra("REGISTRO_DE_ESTUDO_INVALIDO",
                () -> original.paraDominio().encerrarComoCorrigido());
        original.atualizarDe(encerrado);
        registros.flush();
        RegistroDeEstudo salvo = registros.saveAndFlush(
                new RegistroDeEstudoPersistido(correcao)).paraDominio();
        evidencias.registrar(usuario, topico, salvo.identificador(), tipo,
                dadosDaEvidencia, exigirResultado);
        return salvo;
    }

    @Transactional
    public RegistroDeEstudo cancelarEstudo(UUID usuario, UUID identificador) {
        RegistroDeEstudoPersistido persistido = registroPersistido(usuario, identificador);
        RegistroDeEstudo cancelado = regra("REGISTRO_DE_ESTUDO_INVALIDO",
                () -> persistido.paraDominio().cancelar());
        persistido.atualizarDe(cancelado);
        return persistido.paraDominio();
    }

    private void validarEstudo(UUID usuario, UUID topico, UUID material) {
        exigirTopicoAtivo(usuario, topico);
        if (material == null) {
            return;
        }
        exigirMaterialAtivo(usuario, material);
        if (!coberturas.existsByIdentificadorDoMaterialAndIdentificadorDoTopico(
                material, topico)) {
            throw regra("MATERIAL_NAO_COBRE_TOPICO",
                    "O material selecionado nao cobre o topico.");
        }
    }

    private TopicoDaMateria exigirTopicoAtivo(UUID usuario, UUID topico) {
        TopicoDaMateria encontrado = topicos.obter(usuario, topico);
        if (encontrado.arquivado()
                || materiasDoCatalogo.obter(
                        usuario, encontrado.identificadorDaMateria()).arquivada()) {
            throw regra("TOPICO_ARQUIVADO",
                    "Restaure a materia e o topico antes de utiliza-lo.");
        }
        return encontrado;
    }

    private MaterialDeEstudo exigirMaterialAtivo(UUID usuario, UUID material) {
        MaterialDeEstudo encontrado = materialPersistido(usuario, material).paraDominio();
        if (encontrado.arquivado()) {
            throw regra("MATERIAL_ARQUIVADO",
                    "Restaure o material antes de utiliza-lo.");
        }
        return encontrado;
    }

    private MaterialDeEstudoPersistido materialPersistido(UUID usuario, UUID material) {
        return materiais.findByIdentificadorAndIdentificadorDoUsuario(material, usuario)
                .orElseThrow(() -> naoEncontrado(
                        "MATERIAL_NAO_ENCONTRADO", "Material"));
    }

    private RegistroDeEstudoPersistido registroPersistido(UUID usuario, UUID registro) {
        return registros.encontrarDoUsuario(registro, usuario)
                .orElseThrow(() -> naoEncontrado(
                        "REGISTRO_DE_ESTUDO_NAO_ENCONTRADO", "Registro de estudo"));
    }

    private <T> T regra(String codigo, Supplier<T> acao) {
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
}
