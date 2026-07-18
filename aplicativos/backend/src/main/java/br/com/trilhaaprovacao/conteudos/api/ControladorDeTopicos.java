package br.com.trilhaaprovacao.conteudos.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.compartilhado.api.RespostaPaginada;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeTopicos;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
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
@Tag(name = "Matérias e tópicos")
public class ControladorDeTopicos {
    private final ServicoDeTopicos topicos;
    private final IdentidadeDoUsuarioAtual usuarioAtual;

    public ControladorDeTopicos(ServicoDeTopicos topicos, IdentidadeDoUsuarioAtual usuarioAtual) {
        this.topicos = topicos;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping("/materias/{identificadorDaMateria}/topicos")
    public ResponseEntity<RespostaDeTopico> criar(@PathVariable UUID identificadorDaMateria,
            @Valid @RequestBody RequisicaoDeTopico requisicao, Authentication autenticacao) {
        var topico = topicos.criar(usuarioAtual.obter(autenticacao), identificadorDaMateria,
                requisicao.identificadorDoTopicoPai(), requisicao.nome(),
                requisicao.descricao(), requisicao.ordem());
        return ResponseEntity.created(URI.create("/api/v1/topicos/" + topico.identificador()))
                .body(RespostaDeTopico.de(topico));
    }

    @GetMapping("/materias/{identificadorDaMateria}/topicos")
    public RespostaPaginada<RespostaDeTopico> listar(@PathVariable UUID identificadorDaMateria,
            @RequestParam(defaultValue = "") String pesquisa,
            @RequestParam(defaultValue = "false") boolean incluirArquivados,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int tamanho,
            Authentication autenticacao) {
        var resultado = topicos.listar(usuarioAtual.obter(autenticacao), identificadorDaMateria,
                pesquisa, incluirArquivados, pagina, tamanho);
        return new RespostaPaginada<>(resultado.map(RespostaDeTopico::de).getContent(),
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements(), resultado.getTotalPages());
    }

    @GetMapping("/topicos/{identificador}")
    public RespostaDeTopico obter(@PathVariable UUID identificador, Authentication autenticacao) {
        return RespostaDeTopico.de(topicos.obter(usuarioAtual.obter(autenticacao), identificador));
    }

    @PutMapping("/topicos/{identificador}")
    public RespostaDeTopico alterar(@PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeTopico requisicao, Authentication autenticacao) {
        return RespostaDeTopico.de(topicos.alterar(usuarioAtual.obter(autenticacao), identificador,
                requisicao.identificadorDoTopicoPai(), requisicao.nome(),
                requisicao.descricao(), requisicao.ordem()));
    }

    @PostMapping("/topicos/{identificador}/arquivamento")
    public RespostaDeTopico definirArquivamento(@PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeArquivamento requisicao, Authentication autenticacao) {
        return RespostaDeTopico.de(topicos.definirArquivamento(
                usuarioAtual.obter(autenticacao), identificador, requisicao.arquivada()));
    }

    @DeleteMapping("/topicos/{identificador}")
    public ResponseEntity<Void> excluir(@PathVariable UUID identificador, Authentication autenticacao) {
        topicos.excluir(usuarioAtual.obter(autenticacao), identificador);
        return ResponseEntity.noContent().build();
    }
}
