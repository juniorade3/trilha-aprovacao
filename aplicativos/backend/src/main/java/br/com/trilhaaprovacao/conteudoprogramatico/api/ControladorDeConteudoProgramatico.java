package br.com.trilhaaprovacao.conteudoprogramatico.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.conteudoprogramatico.aplicacao.ServicoDeConteudoProgramatico;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeTopicos;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Conteúdo programático")
public class ControladorDeConteudoProgramatico {
    private final ServicoDeConteudoProgramatico conteudo;
    private final ServicoDeTopicos topicos;
    private final IdentidadeDoUsuarioAtual usuarioAtual;

    public ControladorDeConteudoProgramatico(
            ServicoDeConteudoProgramatico conteudo,
            ServicoDeTopicos topicos,
            IdentidadeDoUsuarioAtual usuarioAtual) {
        this.conteudo = conteudo;
        this.topicos = topicos;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping("/materias-da-prova/{materiaDaProva}/itens")
    public ResponseEntity<RespostaDeItemDoEdital> criarItem(
            @PathVariable UUID materiaDaProva,
            @Valid @RequestBody RequisicaoDeItemDoEdital requisicao,
            Authentication autenticacao) {
        var item = conteudo.criarItem(usuario(autenticacao), materiaDaProva,
                requisicao.identificadorDoEdital(), requisicao.descricaoOriginal(),
                requisicao.identificadorDoItemPai(), requisicao.ordem());
        return ResponseEntity.created(
                        URI.create("/api/v1/itens-do-edital/" + item.identificador()))
                .body(RespostaDeItemDoEdital.de(item));
    }

    @GetMapping("/materias-da-prova/{materiaDaProva}/itens")
    public List<RespostaDeItemDoEdital> listarItens(
            @PathVariable UUID materiaDaProva, Authentication autenticacao) {
        return conteudo.listarItens(usuario(autenticacao), materiaDaProva)
                .stream().map(RespostaDeItemDoEdital::de).toList();
    }

    @GetMapping("/itens-do-edital/{identificador}")
    public RespostaDeItemDoEdital obterItem(
            @PathVariable UUID identificador, Authentication autenticacao) {
        return RespostaDeItemDoEdital.de(
                conteudo.obterItem(usuario(autenticacao), identificador));
    }

    @PutMapping("/itens-do-edital/{identificador}")
    public RespostaDeItemDoEdital alterarItem(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeAlteracaoDoItemDoEdital requisicao,
            Authentication autenticacao) {
        return RespostaDeItemDoEdital.de(conteudo.alterarItem(
                usuario(autenticacao), identificador, requisicao.descricaoOriginal(),
                requisicao.identificadorDoItemPai(), requisicao.ordem()));
    }

    @DeleteMapping("/itens-do-edital/{identificador}")
    public ResponseEntity<Void> excluirItem(
            @PathVariable UUID identificador, Authentication autenticacao) {
        conteudo.excluirItem(usuario(autenticacao), identificador);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/itens-do-edital/{item}/mapeamentos")
    public ResponseEntity<RespostaDeMapeamento> criarMapeamento(
            @PathVariable UUID item,
            @Valid @RequestBody RequisicaoDeMapeamento requisicao,
            Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        var mapeamento = conteudo.criarMapeamento(
                usuario, item, requisicao.identificadorDoTopicoDaMateria());
        String nome = topicos.obter(
                usuario, mapeamento.identificadorDoTopicoDaMateria()).nome();
        return ResponseEntity.created(URI.create("/api/v1/itens-do-edital/" + item
                        + "/mapeamentos/" + mapeamento.identificadorDoTopicoDaMateria()))
                .body(RespostaDeMapeamento.de(mapeamento, nome));
    }

    @GetMapping("/itens-do-edital/{item}/mapeamentos")
    public List<RespostaDeMapeamento> listarMapeamentos(
            @PathVariable UUID item, Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        return conteudo.listarMapeamentos(usuario, item).stream()
                .map(mapeamento -> RespostaDeMapeamento.de(mapeamento,
                        topicos.obter(usuario,
                                mapeamento.identificadorDoTopicoDaMateria()).nome()))
                .toList();
    }

    @DeleteMapping("/itens-do-edital/{item}/mapeamentos/{topico}")
    public ResponseEntity<Void> excluirMapeamento(
            @PathVariable UUID item,
            @PathVariable UUID topico,
            Authentication autenticacao) {
        conteudo.excluirMapeamento(usuario(autenticacao), item, topico);
        return ResponseEntity.noContent().build();
    }

    private UUID usuario(Authentication autenticacao) {
        return usuarioAtual.obter(autenticacao);
    }
}
