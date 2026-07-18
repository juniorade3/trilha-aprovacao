package br.com.trilhaaprovacao.concursos.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.compartilhado.api.RespostaPaginada;
import br.com.trilhaaprovacao.concursos.aplicacao.ServicoDaEstruturaDeConcursos;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeMaterias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class ControladorDaEstruturaDeConcursos {
    private final ServicoDaEstruturaDeConcursos estrutura;
    private final ServicoDeMaterias materias;
    private final IdentidadeDoUsuarioAtual usuarioAtual;

    public ControladorDaEstruturaDeConcursos(
            ServicoDaEstruturaDeConcursos estrutura,
            ServicoDeMaterias materias,
            IdentidadeDoUsuarioAtual usuarioAtual) {
        this.estrutura = estrutura;
        this.materias = materias;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping("/concursos")
    public ResponseEntity<RespostaDeConcurso> criarConcurso(
            @Valid @RequestBody RequisicaoDeConcurso requisicao,
            Authentication autenticacao) {
        var concurso = estrutura.criarConcurso(usuario(autenticacao), requisicao.nome(),
                requisicao.descricao(), requisicao.orgao(), requisicao.banca(),
                requisicao.situacao(), requisicao.dataPrevistaPrincipal());
        return ResponseEntity.created(URI.create("/api/v1/concursos/" + concurso.identificador()))
                .body(RespostaDeConcurso.de(concurso));
    }

    @GetMapping("/concursos")
    public RespostaPaginada<RespostaDeConcurso> listarConcursos(
            @RequestParam(defaultValue = "") String pesquisa,
            @RequestParam(defaultValue = "false") boolean incluirArquivados,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int tamanho,
            Authentication autenticacao) {
        var resultado = estrutura.listarConcursos(
                usuario(autenticacao), pesquisa, incluirArquivados, pagina, tamanho);
        return new RespostaPaginada<>(resultado.map(RespostaDeConcurso::de).getContent(),
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements(),
                resultado.getTotalPages());
    }

    @GetMapping("/concursos/{identificador}")
    public RespostaDeConcurso obterConcurso(
            @PathVariable UUID identificador, Authentication autenticacao) {
        return RespostaDeConcurso.de(
                estrutura.obterConcurso(usuario(autenticacao), identificador));
    }

    @PutMapping("/concursos/{identificador}")
    public RespostaDeConcurso alterarConcurso(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeConcurso requisicao,
            Authentication autenticacao) {
        return RespostaDeConcurso.de(estrutura.alterarConcurso(usuario(autenticacao),
                identificador, requisicao.nome(), requisicao.descricao(), requisicao.orgao(),
                requisicao.banca(), requisicao.situacao(),
                requisicao.dataPrevistaPrincipal()));
    }

    @DeleteMapping("/concursos/{identificador}")
    public ResponseEntity<Void> excluirConcurso(
            @PathVariable UUID identificador, Authentication autenticacao) {
        estrutura.excluirConcurso(usuario(autenticacao), identificador);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/concursos/{identificador}/ativacao")
    public RespostaDeConcurso ativarConcurso(
            @PathVariable UUID identificador, Authentication autenticacao) {
        return RespostaDeConcurso.de(
                estrutura.ativarConcurso(usuario(autenticacao), identificador));
    }

    @PostMapping("/concursos/{identificador}/arquivamento")
    public RespostaDeConcurso arquivarConcurso(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeArquivamento requisicao,
            Authentication autenticacao) {
        return RespostaDeConcurso.de(estrutura.definirArquivamentoDoConcurso(
                usuario(autenticacao), identificador, requisicao.arquivado()));
    }

    @PostMapping("/concursos/{concurso}/editais")
    public ResponseEntity<RespostaDeEdital> criarEdital(
            @PathVariable UUID concurso,
            @Valid @RequestBody RequisicaoDeEdital requisicao,
            Authentication autenticacao) {
        var edital = estrutura.criarEdital(usuario(autenticacao), concurso,
                requisicao.titulo(), requisicao.numero(), requisicao.ano(),
                requisicao.descricao(), requisicao.dataDePublicacao(),
                requisicao.enderecoDoDocumento());
        return ResponseEntity.created(URI.create("/api/v1/editais/" + edital.identificador()))
                .body(RespostaDeEdital.de(edital));
    }

    @GetMapping("/concursos/{concurso}/editais")
    public List<RespostaDeEdital> listarEditais(
            @PathVariable UUID concurso, Authentication autenticacao) {
        return estrutura.listarEditais(usuario(autenticacao), concurso)
                .stream().map(RespostaDeEdital::de).toList();
    }

    @GetMapping("/editais/{identificador}")
    public RespostaDeEdital obterEdital(
            @PathVariable UUID identificador, Authentication autenticacao) {
        return RespostaDeEdital.de(
                estrutura.obterEdital(usuario(autenticacao), identificador));
    }

    @PutMapping("/editais/{identificador}")
    public RespostaDeEdital alterarEdital(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeEdital requisicao,
            Authentication autenticacao) {
        return RespostaDeEdital.de(estrutura.alterarEdital(usuario(autenticacao),
                identificador, requisicao.titulo(), requisicao.numero(), requisicao.ano(),
                requisicao.descricao(), requisicao.dataDePublicacao(),
                requisicao.enderecoDoDocumento()));
    }

    @DeleteMapping("/editais/{identificador}")
    public ResponseEntity<Void> excluirEdital(
            @PathVariable UUID identificador, Authentication autenticacao) {
        estrutura.excluirEdital(usuario(autenticacao), identificador);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/editais/{identificador}/definicao-como-principal")
    public RespostaDeEdital definirEditalPrincipal(
            @PathVariable UUID identificador, Authentication autenticacao) {
        return RespostaDeEdital.de(
                estrutura.definirEditalPrincipal(usuario(autenticacao), identificador));
    }

    @PostMapping("/concursos/{concurso}/cargos")
    public ResponseEntity<RespostaDeCargo> criarCargo(
            @PathVariable UUID concurso,
            @Valid @RequestBody RequisicaoDeCargo requisicao,
            Authentication autenticacao) {
        var cargo = estrutura.criarCargo(usuario(autenticacao), concurso, requisicao.nome(),
                requisicao.area(), requisicao.especialidade(),
                requisicao.nivelDeEscolaridade(), requisicao.ordem());
        return ResponseEntity.created(URI.create("/api/v1/cargos/" + cargo.identificador()))
                .body(RespostaDeCargo.de(cargo));
    }

    @GetMapping("/concursos/{concurso}/cargos")
    public List<RespostaDeCargo> listarCargos(
            @PathVariable UUID concurso, Authentication autenticacao) {
        return estrutura.listarCargos(usuario(autenticacao), concurso)
                .stream().map(RespostaDeCargo::de).toList();
    }

    @GetMapping("/cargos/{identificador}")
    public RespostaDeCargo obterCargo(
            @PathVariable UUID identificador, Authentication autenticacao) {
        return RespostaDeCargo.de(
                estrutura.obterCargo(usuario(autenticacao), identificador));
    }

    @PutMapping("/cargos/{identificador}")
    public RespostaDeCargo alterarCargo(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeCargo requisicao,
            Authentication autenticacao) {
        return RespostaDeCargo.de(estrutura.alterarCargo(usuario(autenticacao),
                identificador, requisicao.nome(), requisicao.area(),
                requisicao.especialidade(), requisicao.nivelDeEscolaridade(),
                requisicao.ordem()));
    }

    @DeleteMapping("/cargos/{identificador}")
    public ResponseEntity<Void> excluirCargo(
            @PathVariable UUID identificador, Authentication autenticacao) {
        estrutura.excluirCargo(usuario(autenticacao), identificador);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cargos/{identificador}/selecao")
    public RespostaDeCargo selecionarCargo(
            @PathVariable UUID identificador, Authentication autenticacao) {
        return RespostaDeCargo.de(
                estrutura.selecionarCargo(usuario(autenticacao), identificador));
    }

    @PostMapping("/cargos/{cargo}/provas")
    public ResponseEntity<RespostaDeProva> criarProva(
            @PathVariable UUID cargo,
            @Valid @RequestBody RequisicaoDeProva requisicao,
            Authentication autenticacao) {
        var prova = estrutura.criarProva(usuario(autenticacao), cargo, requisicao.nome(),
                requisicao.tipo(), requisicao.carater(), requisicao.ordem(),
                requisicao.dataHoraPrevista(), requisicao.duracaoEmMinutos(),
                requisicao.quantidadeDeQuestoes(), requisicao.pontuacaoMaxima(),
                requisicao.pontuacaoMinima());
        return ResponseEntity.created(URI.create("/api/v1/provas/" + prova.identificador()))
                .body(RespostaDeProva.de(prova));
    }

    @GetMapping("/cargos/{cargo}/provas")
    public List<RespostaDeProva> listarProvas(
            @PathVariable UUID cargo, Authentication autenticacao) {
        return estrutura.listarProvas(usuario(autenticacao), cargo)
                .stream().map(RespostaDeProva::de).toList();
    }

    @GetMapping("/provas/{identificador}")
    public RespostaDeProva obterProva(
            @PathVariable UUID identificador, Authentication autenticacao) {
        return RespostaDeProva.de(
                estrutura.obterProva(usuario(autenticacao), identificador));
    }

    @PutMapping("/provas/{identificador}")
    public RespostaDeProva alterarProva(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeProva requisicao,
            Authentication autenticacao) {
        return RespostaDeProva.de(estrutura.alterarProva(usuario(autenticacao),
                identificador, requisicao.nome(), requisicao.tipo(), requisicao.carater(),
                requisicao.ordem(), requisicao.dataHoraPrevista(),
                requisicao.duracaoEmMinutos(), requisicao.quantidadeDeQuestoes(),
                requisicao.pontuacaoMaxima(), requisicao.pontuacaoMinima()));
    }

    @DeleteMapping("/provas/{identificador}")
    public ResponseEntity<Void> excluirProva(
            @PathVariable UUID identificador, Authentication autenticacao) {
        estrutura.excluirProva(usuario(autenticacao), identificador);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/provas/{prova}/grupos")
    public ResponseEntity<RespostaDeGrupo> criarGrupo(
            @PathVariable UUID prova,
            @Valid @RequestBody RequisicaoDeGrupo requisicao,
            Authentication autenticacao) {
        var grupo = estrutura.criarGrupo(usuario(autenticacao), prova, requisicao.nome(),
                requisicao.ordem(), requisicao.quantidadeDeQuestoes(),
                requisicao.pontuacaoMaxima(), requisicao.pontuacaoMinima());
        return ResponseEntity.created(
                        URI.create("/api/v1/grupos-de-conteudo/" + grupo.identificador()))
                .body(RespostaDeGrupo.de(grupo));
    }

    @GetMapping("/provas/{prova}/grupos")
    public List<RespostaDeGrupo> listarGrupos(
            @PathVariable UUID prova, Authentication autenticacao) {
        return estrutura.listarGrupos(usuario(autenticacao), prova)
                .stream().map(RespostaDeGrupo::de).toList();
    }

    @GetMapping("/grupos-de-conteudo/{identificador}")
    public RespostaDeGrupo obterGrupo(
            @PathVariable UUID identificador, Authentication autenticacao) {
        return RespostaDeGrupo.de(
                estrutura.obterGrupo(usuario(autenticacao), identificador));
    }

    @PutMapping("/grupos-de-conteudo/{identificador}")
    public RespostaDeGrupo alterarGrupo(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeGrupo requisicao,
            Authentication autenticacao) {
        return RespostaDeGrupo.de(estrutura.alterarGrupo(usuario(autenticacao),
                identificador, requisicao.nome(), requisicao.ordem(),
                requisicao.quantidadeDeQuestoes(), requisicao.pontuacaoMaxima(),
                requisicao.pontuacaoMinima()));
    }

    @DeleteMapping("/grupos-de-conteudo/{identificador}")
    public ResponseEntity<Void> excluirGrupo(
            @PathVariable UUID identificador, Authentication autenticacao) {
        estrutura.excluirGrupo(usuario(autenticacao), identificador);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/grupos-de-conteudo/{grupo}/materias")
    public ResponseEntity<RespostaDeMateriaDaProva> criarMateriaDaProva(
            @PathVariable UUID grupo,
            @Valid @RequestBody RequisicaoDeMateriaDaProva requisicao,
            Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        var materia = estrutura.criarMateriaDaProva(usuario, grupo,
                requisicao.identificadorDaMateria(), requisicao.ordem(), requisicao.peso(),
                requisicao.quantidadeDeQuestoes(), requisicao.pontuacaoMaxima());
        return ResponseEntity.created(
                        URI.create("/api/v1/materias-da-prova/" + materia.identificador()))
                .body(respostaDaMateria(usuario, materia));
    }

    @GetMapping("/grupos-de-conteudo/{grupo}/materias")
    public List<RespostaDeMateriaDaProva> listarMateriasDaProva(
            @PathVariable UUID grupo, Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        return estrutura.listarMateriasDaProva(usuario, grupo)
                .stream().map(materia -> respostaDaMateria(usuario, materia)).toList();
    }

    @GetMapping("/materias-da-prova/{identificador}")
    public RespostaDeMateriaDaProva obterMateriaDaProva(
            @PathVariable UUID identificador, Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        return respostaDaMateria(
                usuario, estrutura.obterMateriaDaProva(usuario, identificador));
    }

    @PutMapping("/materias-da-prova/{identificador}")
    public RespostaDeMateriaDaProva alterarMateriaDaProva(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeAlteracaoDaMateriaDaProva requisicao,
            Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        var materia = estrutura.alterarMateriaDaProva(usuario, identificador,
                requisicao.ordem(), requisicao.peso(), requisicao.quantidadeDeQuestoes(),
                requisicao.pontuacaoMaxima());
        return respostaDaMateria(usuario, materia);
    }

    @DeleteMapping("/materias-da-prova/{identificador}")
    public ResponseEntity<Void> excluirMateriaDaProva(
            @PathVariable UUID identificador, Authentication autenticacao) {
        estrutura.excluirMateriaDaProva(usuario(autenticacao), identificador);
        return ResponseEntity.noContent().build();
    }

    private UUID usuario(Authentication autenticacao) {
        return usuarioAtual.obter(autenticacao);
    }

    private RespostaDeMateriaDaProva respostaDaMateria(
            UUID usuario, br.com.trilhaaprovacao.concursos.dominio.MateriaDaProva materia) {
        String nome = materias.obter(usuario, materia.identificadorDaMateria()).nome();
        return RespostaDeMateriaDaProva.de(materia, nome);
    }
}
