package br.com.trilhaaprovacao.estudos.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.compartilhado.api.RespostaPaginada;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeTopicos;
import br.com.trilhaaprovacao.estudos.aplicacao.ServicoDeMateriaisEEstudos;
import br.com.trilhaaprovacao.estudos.dominio.RegistroDeEstudo;
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
public class ControladorDeMateriaisEEstudos {
    private final ServicoDeMateriaisEEstudos servico;
    private final ServicoDeTopicos topicos;
    private final IdentidadeDoUsuarioAtual usuarioAtual;

    public ControladorDeMateriaisEEstudos(
            ServicoDeMateriaisEEstudos servico,
            ServicoDeTopicos topicos,
            IdentidadeDoUsuarioAtual usuarioAtual) {
        this.servico = servico;
        this.topicos = topicos;
        this.usuarioAtual = usuarioAtual;
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
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int tamanho,
            Authentication autenticacao) {
        var resultado = servico.listarMateriais(usuario(autenticacao), pesquisa,
                incluirArquivados, pagina, tamanho);
        return new RespostaPaginada<>(
                resultado.map(RespostaDeMaterial::de).getContent(),
                resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
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
    public ResponseEntity<RespostaDeRegistroDeEstudo> registrarEstudo(
            @Valid @RequestBody RequisicaoDeRegistroDeEstudo requisicao,
            Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        var registro = servico.registrarEstudo(usuario,
                requisicao.identificadorDoTopico(), requisicao.identificadorDoMaterial(),
                requisicao.dataHora(), requisicao.duracaoEmMinutos(),
                requisicao.observacao());
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
    public RespostaDeRegistroDeEstudo corrigirEstudo(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeRegistroDeEstudo requisicao,
            Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        return respostaDoRegistro(usuario, servico.corrigirEstudo(usuario,
                identificador, requisicao.identificadorDoTopico(),
                requisicao.identificadorDoMaterial(), requisicao.dataHora(),
                requisicao.duracaoEmMinutos(), requisicao.observacao()));
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
        return RespostaDeRegistroDeEstudo.de(
                registro, nomeDoTopico, tituloDoMaterial);
    }

    private UUID usuario(Authentication autenticacao) {
        return usuarioAtual.obter(autenticacao);
    }
}
