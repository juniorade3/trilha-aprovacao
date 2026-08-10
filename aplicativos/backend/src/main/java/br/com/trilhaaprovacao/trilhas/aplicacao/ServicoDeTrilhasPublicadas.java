package br.com.trilhaaprovacao.trilhas.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.trilhas.dominio.AcompanhamentoDaTarefa;
import br.com.trilhaaprovacao.trilhas.dominio.SituacaoDoAcompanhamentoDaTarefa;
import br.com.trilhaaprovacao.trilhas.infraestrutura.AcompanhamentoDaTarefaPersistido;
import br.com.trilhaaprovacao.trilhas.infraestrutura.AdesaoATrilhaPersistida;
import br.com.trilhaaprovacao.trilhas.infraestrutura.DisciplinaDaTrilhaPersistida;
import br.com.trilhaaprovacao.trilhas.infraestrutura.RepositorioDeAcompanhamentosDeTarefas;
import br.com.trilhaaprovacao.trilhas.infraestrutura.RepositorioDeAdesoesATrilhas;
import br.com.trilhaaprovacao.trilhas.infraestrutura.RepositorioDeDisciplinasDaTrilha;
import br.com.trilhaaprovacao.trilhas.infraestrutura.RepositorioDeTarefasPublicadasDaTrilha;
import br.com.trilhaaprovacao.trilhas.infraestrutura.RepositorioDeTrilhasPublicadas;
import br.com.trilhaaprovacao.trilhas.infraestrutura.TarefaPublicadaPersistida;
import br.com.trilhaaprovacao.trilhas.infraestrutura.TrilhaPublicadaPersistida;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoDeTrilhasPublicadas {
    private final RepositorioDeTrilhasPublicadas trilhas;
    private final RepositorioDeDisciplinasDaTrilha disciplinas;
    private final RepositorioDeTarefasPublicadasDaTrilha tarefas;
    private final RepositorioDeAdesoesATrilhas adesoes;
    private final RepositorioDeAcompanhamentosDeTarefas acompanhamentos;

    public ServicoDeTrilhasPublicadas(RepositorioDeTrilhasPublicadas trilhas,
            RepositorioDeDisciplinasDaTrilha disciplinas,
            RepositorioDeTarefasPublicadasDaTrilha tarefas,
            RepositorioDeAdesoesATrilhas adesoes,
            RepositorioDeAcompanhamentosDeTarefas acompanhamentos) {
        this.trilhas = trilhas;
        this.disciplinas = disciplinas;
        this.tarefas = tarefas;
        this.adesoes = adesoes;
        this.acompanhamentos = acompanhamentos;
    }

    @Transactional(readOnly = true)
    public List<ResumoDaTrilhaPublicada> listar(UUID usuario) {
        return trilhas.findAllByOrderByNomeAsc().stream()
                .map(trilha -> resumo(trilha,
                        adesoes.findByIdentificadorDoUsuarioAndIdentificadorDaTrilha(
                                usuario, trilha.identificador()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public DetalheDaTrilhaPublicada detalhar(UUID usuario, UUID identificadorDaTrilha) {
        TrilhaPublicadaPersistida trilha = trilha(identificadorDaTrilha);
        AdesaoATrilhaPersistida adesao = adesoes
                .findByIdentificadorDoUsuarioAndIdentificadorDaTrilha(usuario, identificadorDaTrilha)
                .orElse(null);
        Map<UUID, AcompanhamentoDaTarefa> acompanhamentosPorTarefa = acompanhamentosDa(adesao);
        List<DisciplinaDaTrilhaPersistida> disciplinasDaTrilha = disciplinas
                .findByIdentificadorDaTrilhaOrderByOrdemAsc(identificadorDaTrilha);
        List<DisciplinaComTarefasDaTrilha> detalhesDasDisciplinas = disciplinasDaTrilha.stream()
                .map(disciplina -> disciplinaComTarefas(disciplina, acompanhamentosPorTarefa))
                .toList();
        return new DetalheDaTrilhaPublicada(resumo(trilha, adesao,
                disciplinasDaTrilha, acompanhamentosPorTarefa), detalhesDasDisciplinas);
    }

    @Transactional
    public ResumoDaTrilhaPublicada aderir(UUID usuario, UUID identificadorDaTrilha) {
        TrilhaPublicadaPersistida trilha = trilha(identificadorDaTrilha);
        AdesaoATrilhaPersistida adesao = adesoes
                .findByIdentificadorDoUsuarioAndIdentificadorDaTrilha(usuario, identificadorDaTrilha)
                .orElseGet(() -> adesoes.save(AdesaoATrilhaPersistida.criar(usuario, identificadorDaTrilha)));
        return resumo(trilha, adesao);
    }

    @Transactional
    public TarefaComAcompanhamentoDaTrilha atualizarAcompanhamento(UUID usuario,
            UUID identificadorDaTrilha, UUID identificadorDaTarefa,
            SituacaoDoAcompanhamentoDaTarefa situacao, String observacao) {
        AdesaoATrilhaPersistida adesao = adesoes
                .findByIdentificadorDoUsuarioAndIdentificadorDaTrilha(usuario, identificadorDaTrilha)
                .orElseThrow(() -> new RegraDeDominio("ADESAO_A_TRILHA_OBRIGATORIA",
                        "Aderir a trilha e necessario antes de acompanhar tarefas."));
        TarefaPublicadaPersistida tarefa = tarefas.findById(identificadorDaTarefa)
                .orElseThrow(() -> naoEncontrada("TAREFA_DA_TRILHA_NAO_ENCONTRADA", "Tarefa"));
        DisciplinaDaTrilhaPersistida disciplina = disciplinas.findById(tarefa.identificadorDaDisciplina())
                .orElseThrow(() -> naoEncontrada("DISCIPLINA_DA_TRILHA_NAO_ENCONTRADA", "Disciplina"));
        if (!disciplina.identificadorDaTrilha().equals(identificadorDaTrilha)) {
            throw naoEncontrada("TAREFA_DA_TRILHA_NAO_ENCONTRADA", "Tarefa");
        }
        AcompanhamentoDaTarefaPersistido persistido = acompanhamentos
                .findByIdentificadorDaAdesaoAndIdentificadorDaTarefa(adesao.identificador(),
                        identificadorDaTarefa)
                .orElse(null);
        AcompanhamentoDaTarefa atualizado = (persistido == null
                ? AcompanhamentoDaTarefa.criar(adesao.identificador(), identificadorDaTarefa)
                : persistido.paraDominio())
                .alterar(situacao, observacao);
        if (persistido == null) {
            acompanhamentos.save(new AcompanhamentoDaTarefaPersistido(atualizado));
        } else {
            persistido.atualizarDe(atualizado);
        }
        return tarefaComAcompanhamento(tarefa, atualizado);
    }

    private ResumoDaTrilhaPublicada resumo(TrilhaPublicadaPersistida trilha,
            AdesaoATrilhaPersistida adesao) {
        List<DisciplinaDaTrilhaPersistida> disciplinasDaTrilha = disciplinas
                .findByIdentificadorDaTrilhaOrderByOrdemAsc(trilha.identificador());
        return resumo(trilha, adesao, disciplinasDaTrilha, acompanhamentosDa(adesao));
    }

    private ResumoDaTrilhaPublicada resumo(TrilhaPublicadaPersistida trilha,
            AdesaoATrilhaPersistida adesao, List<DisciplinaDaTrilhaPersistida> disciplinasDaTrilha,
            Map<UUID, AcompanhamentoDaTarefa> acompanhamentosPorTarefa) {
        int quantidadeDeTarefas = disciplinasDaTrilha.stream()
                .mapToInt(disciplina -> tarefas
                        .findByIdentificadorDaDisciplinaOrderByNumeroAsc(disciplina.identificador()).size())
                .sum();
        int concluidas = (int) acompanhamentosPorTarefa.values().stream()
                .filter(acompanhamento -> acompanhamento.situacao()
                        == SituacaoDoAcompanhamentoDaTarefa.CONCLUIDA)
                .count();
        var dominio = trilha.paraDominio();
        return new ResumoDaTrilhaPublicada(dominio.identificador(), dominio.codigo(), dominio.nome(),
                dominio.versaoPublicada(), dominio.descricao(), disciplinasDaTrilha.size(),
                quantidadeDeTarefas, concluidas, adesao != null);
    }

    private DisciplinaComTarefasDaTrilha disciplinaComTarefas(DisciplinaDaTrilhaPersistida disciplina,
            Map<UUID, AcompanhamentoDaTarefa> acompanhamentosPorTarefa) {
        var dominio = disciplina.paraDominio();
        List<TarefaComAcompanhamentoDaTrilha> tarefasDaDisciplina = tarefas
                .findByIdentificadorDaDisciplinaOrderByNumeroAsc(disciplina.identificador()).stream()
                .map(tarefa -> tarefaComAcompanhamento(tarefa,
                        acompanhamentosPorTarefa.get(tarefa.identificador())))
                .toList();
        return new DisciplinaComTarefasDaTrilha(dominio.identificador(), dominio.nome(),
                dominio.ordem(), tarefasDaDisciplina);
    }

    private TarefaComAcompanhamentoDaTrilha tarefaComAcompanhamento(
            TarefaPublicadaPersistida tarefa, AcompanhamentoDaTarefa acompanhamento) {
        var dominio = tarefa.paraDominio();
        return new TarefaComAcompanhamentoDaTrilha(dominio.identificador(), dominio.numero(),
                dominio.titulo(), dominio.aula(), dominio.tipoDeAtividade(),
                dominio.enderecoDoMaterial(), dominio.orientacao(),
                acompanhamento == null ? SituacaoDoAcompanhamentoDaTarefa.PENDENTE
                        : acompanhamento.situacao(),
                acompanhamento == null ? null : acompanhamento.observacao(),
                acompanhamento == null ? null : acompanhamento.concluidaEm());
    }

    private Map<UUID, AcompanhamentoDaTarefa> acompanhamentosDa(AdesaoATrilhaPersistida adesao) {
        Map<UUID, AcompanhamentoDaTarefa> porTarefa = new HashMap<>();
        if (adesao == null) return porTarefa;
        acompanhamentos.findByIdentificadorDaAdesao(adesao.identificador()).forEach(acompanhamento -> {
            AcompanhamentoDaTarefa dominio = acompanhamento.paraDominio();
            porTarefa.put(dominio.identificadorDaTarefa(), dominio);
        });
        return porTarefa;
    }

    private TrilhaPublicadaPersistida trilha(UUID identificador) {
        return trilhas.findById(identificador)
                .orElseThrow(() -> naoEncontrada("TRILHA_PUBLICADA_NAO_ENCONTRADA", "Trilha"));
    }

    private RecursoNaoEncontrado naoEncontrada(String codigo, String recurso) {
        return new RecursoNaoEncontrado(codigo, recurso + " nao encontrada.");
    }
}
