package br.com.trilhaaprovacao.estudos.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.compartilhado.api.RespostaPaginada;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeTopicos;
import br.com.trilhaaprovacao.estudos.aplicacao.ServicoDeMateriaisEEstudos;
import br.com.trilhaaprovacao.estudos.dominio.RegistroDeEstudo;
import br.com.trilhaaprovacao.estudos.dominio.TipoDeEstudo;
import br.com.trilhaaprovacao.evidencias.aplicacao.ServicoDeEvidenciasDeAprendizagem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
@Tag(name = "Materiais e estudos")
public class ControladorDeMateriaisEEstudos {
    private final ServicoDeMateriaisEEstudos servico;
    private final ServicoDeTopicos topicos;
    private final IdentidadeDoUsuarioAtual usuarioAtual;
    private final ServicoDeEvidenciasDeAprendizagem evidencias;

    public ControladorDeMateriaisEEstudos(
            ServicoDeMateriaisEEstudos servico,
            ServicoDeTopicos topicos,
            IdentidadeDoUsuarioAtual usuarioAtual,
            ServicoDeEvidenciasDeAprendizagem evidencias) {
        this.servico = servico;
        this.topicos = topicos;
        this.usuarioAtual = usuarioAtual;
        this.evidencias = evidencias;
    }

    @PostMapping("/materiais")
    public ResponseEntity<RespostaDeMaterial> criarMaterial(
            @Valid @RequestBody RequisicaoDeMaterial requisicao,
            Authentication autenticacao) {
        var material = servico.criarMaterial(usuario(autenticacao), requisicao.titulo(),
                requisicao.tipo(), requisicao.descricao(), requisicao.fonte(),
                requisicao.endereco(), requisicao.duracaoEstimadaEmMinutos());
        return ResponseEntity.created(
                        URI.create("/api/v1/materiais/" + material.identificador()))
                .body(RespostaDeMaterial.de(material));
    }

    @GetMapping("/materiais")
    public RespostaPaginada<RespostaDeMaterial> listarMateriais(
            @RequestParam(defaultValue = "") String pesquisa,
            @RequestParam(defaultValue = "false") boolean incluirArquivados,
            @RequestParam(required = false) UUID identificadorDoTopico,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int tamanho,
            Authentication autenticacao) {
        var resultado = servico.listarMateriais(usuario(autenticacao), pesquisa,
                incluirArquivados, identificadorDoTopico, pagina, tamanho);
        return new RespostaPaginada<>(
                resultado.map(RespostaDeMaterial::de).getContent(),
                resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
    }

    @GetMapping("/materiais/atalhos-por-topico")
    @Operation(summary = "Lista materiais ativos associados aos tópicos informados")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atalhos listados."),
            @ApiResponse(responseCode = "400", description = "Parâmetros inválidos."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada.")
    })
    public List<RespostaDeMaterialRelacionadoAoTopico> listarAtalhosPorTopico(
            @RequestParam
            @Size(min = 1, max = 100)
            List<UUID> identificadoresDosTopicos,
            Authentication autenticacao) {
        return servico.listarMateriaisAtivosDosTopicos(
                        usuario(autenticacao), identificadoresDosTopicos)
                .stream()
                .map(RespostaDeMaterialRelacionadoAoTopico::de)
                .toList();
    }

    @GetMapping("/materiais/{identificador}")
    public RespostaDeMaterial obterMaterial(
            @PathVariable UUID identificador, Authentication autenticacao) {
        return RespostaDeMaterial.de(
                servico.obterMaterial(usuario(autenticacao), identificador));
    }

    @PutMapping("/materiais/{identificador}")
    public RespostaDeMaterial alterarMaterial(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeMaterial requisicao,
            Authentication autenticacao) {
        return RespostaDeMaterial.de(servico.alterarMaterial(usuario(autenticacao),
                identificador, requisicao.titulo(), requisicao.tipo(),
                requisicao.descricao(), requisicao.fonte(), requisicao.endereco(),
                requisicao.duracaoEstimadaEmMinutos()));
    }

    @DeleteMapping("/materiais/{identificador}")
    public ResponseEntity<Void> excluirMaterial(
            @PathVariable UUID identificador, Authentication autenticacao) {
        servico.excluirMaterial(usuario(autenticacao), identificador);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/materiais/{identificador}/arquivamento")
    public RespostaDeMaterial arquivarMaterial(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeArquivamentoDoMaterial requisicao,
            Authentication autenticacao) {
        return RespostaDeMaterial.de(servico.definirArquivamento(
                usuario(autenticacao), identificador, requisicao.arquivado()));
    }

    @PostMapping("/materiais/{material}/topicos")
    public ResponseEntity<RespostaDeCobertura> adicionarCobertura(
            @PathVariable UUID material,
            @Valid @RequestBody RequisicaoDeCobertura requisicao,
            Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        var cobertura = servico.adicionarCobertura(
                usuario, material, requisicao.identificadorDoTopico());
        return ResponseEntity.created(URI.create("/api/v1/materiais/" + material
                        + "/topicos/" + cobertura.identificadorDoTopico()))
                .body(RespostaDeCobertura.de(cobertura,
                        topicos.obter(usuario, cobertura.identificadorDoTopico()).nome()));
    }

    @GetMapping("/materiais/{material}/topicos")
    public List<RespostaDeCobertura> listarCoberturas(
            @PathVariable UUID material, Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        return servico.listarCoberturas(usuario, material).stream()
                .map(cobertura -> RespostaDeCobertura.de(cobertura,
                        topicos.obter(usuario, cobertura.identificadorDoTopico()).nome()))
                .toList();
    }

    @DeleteMapping("/materiais/{material}/topicos/{topico}")
    public ResponseEntity<Void> removerCobertura(
            @PathVariable UUID material, @PathVariable UUID topico,
            Authentication autenticacao) {
        servico.removerCobertura(usuario(autenticacao), material, topico);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/estudos")
    @Operation(summary = "Registra estudo e sua evidência de aprendizagem")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Estudo registrado."),
            @ApiResponse(responseCode = "400", description = "Requisição inválida."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada."),
            @ApiResponse(responseCode = "403", description = "Acesso recusado."),
            @ApiResponse(responseCode = "404", description = "Tópico ou material não encontrado."),
            @ApiResponse(responseCode = "409", description = "Conflito de estado."),
            @ApiResponse(responseCode = "422", description = "Evidência ou regra de negócio inválida.")
    })
    public ResponseEntity<RespostaDeRegistroDeEstudo> registrarEstudo(
            @Valid @RequestBody RequisicaoDeRegistroDeEstudo requisicao,
            Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        var registro = servico.registrarEstudo(usuario,
                requisicao.identificadorDoTopico(), requisicao.identificadorDoMaterial(),
                requisicao.tipoDeEstudo() == null ? TipoDeEstudo.OUTRA : requisicao.tipoDeEstudo(),
                requisicao.dataHora(), requisicao.duracaoEmMinutos(), requisicao.observacao(),
                requisicao.evidencia() == null ? null : requisicao.evidencia().paraDados(), true);
        return ResponseEntity.created(
                        URI.create("/api/v1/estudos/" + registro.identificador()))
                .body(respostaDoRegistro(usuario, registro));
    }

    @GetMapping("/estudos")
    public RespostaPaginada<RespostaDeRegistroDeEstudo> listarEstudos(
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        var resultado = servico.listarEstudos(usuario, pagina, tamanho);
        return new RespostaPaginada<>(
                resultado.map(registro -> respostaDoRegistro(usuario, registro))
                        .getContent(),
                resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
    }

    @GetMapping("/estudos/{identificador}")
    public RespostaDeRegistroDeEstudo obterEstudo(
            @PathVariable UUID identificador, Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        return respostaDoRegistro(
                usuario, servico.obterEstudo(usuario, identificador));
    }

    @PutMapping("/estudos/{identificador}/correcao")
    @Operation(summary = "Corrige estudo preservando o fato e a evidência anteriores")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Correção registrada."),
            @ApiResponse(responseCode = "400", description = "Requisição inválida."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada."),
            @ApiResponse(responseCode = "403", description = "Acesso recusado."),
            @ApiResponse(responseCode = "404", description = "Estudo, tópico ou material não encontrado."),
            @ApiResponse(responseCode = "409", description = "Conflito de estado."),
            @ApiResponse(responseCode = "422", description = "Evidência ou regra de negócio inválida.")
    })
    public RespostaDeRegistroDeEstudo corrigirEstudo(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeRegistroDeEstudo requisicao,
            Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        TipoDeEstudo tipoDeEstudo = requisicao.tipoDeEstudo() == null
                ? servico.obterEstudo(usuario, identificador).tipoDeEstudo()
                : requisicao.tipoDeEstudo();
        return respostaDoRegistro(usuario, servico.corrigirEstudo(usuario,
                identificador, requisicao.identificadorDoTopico(),
                requisicao.identificadorDoMaterial(),
                tipoDeEstudo,
                requisicao.dataHora(),
                requisicao.duracaoEmMinutos(), requisicao.observacao(),
                requisicao.evidencia() == null ? null : requisicao.evidencia().paraDados(), true));
    }

    @PostMapping("/estudos/{identificador}/cancelamento")
    public RespostaDeRegistroDeEstudo cancelarEstudo(
            @PathVariable UUID identificador, Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        return respostaDoRegistro(
                usuario, servico.cancelarEstudo(usuario, identificador));
    }

    private RespostaDeRegistroDeEstudo respostaDoRegistro(
            UUID usuario, RegistroDeEstudo registro) {
        String nomeDoTopico = topicos.obter(
                usuario, registro.identificadorDoTopico()).nome();
        String tituloDoMaterial = registro.identificadorDoMaterial() == null
                ? null
                : servico.obterMaterial(
                        usuario, registro.identificadorDoMaterial()).titulo();
        return RespostaDeRegistroDeEstudo.de(registro, nomeDoTopico, tituloDoMaterial,
                evidencias.obterPorRegistro(registro.identificador()).orElse(null));
    }

    private UUID usuario(Authentication autenticacao) {
        return usuarioAtual.obter(autenticacao);
    }
}
