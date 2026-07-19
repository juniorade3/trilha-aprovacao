package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.planejamento.aplicacao.ServicoDePlanejamento;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Planejamento")
public class ControladorDeBlocosDeEstudo {
    private final ServicoDePlanejamento servico;
    private final IdentidadeDoUsuarioAtual usuarioAtual;

    public ControladorDeBlocosDeEstudo(ServicoDePlanejamento servico,
            IdentidadeDoUsuarioAtual usuarioAtual) {
        this.servico = servico;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping("/planos-semanais/{plano}/blocos")
    public ResponseEntity<RespostaDeBlocoDeEstudo> adicionar(
            @PathVariable UUID plano,
            @Valid @RequestBody RequisicaoDeBlocoDeEstudo requisicao,
            Authentication autenticacao) {
        var bloco = servico.adicionarBloco(usuarioAtual.obter(autenticacao),
                plano, requisicao.paraDados());
        return ResponseEntity.created(
                        URI.create("/api/v1/blocos-de-estudo/" + bloco.identificador()))
                .body(RespostaDeBlocoDeEstudo.de(bloco));
    }

    @PutMapping("/blocos-de-estudo/{identificador}")
    public RespostaDeBlocoDeEstudo alterar(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeBlocoDeEstudo requisicao,
            Authentication autenticacao) {
        return RespostaDeBlocoDeEstudo.de(servico.alterarBloco(
                usuarioAtual.obter(autenticacao), identificador,
                requisicao.paraDados()));
    }

    @DeleteMapping("/blocos-de-estudo/{identificador}")
    public ResponseEntity<Void> excluir(@PathVariable UUID identificador,
            Authentication autenticacao) {
        servico.excluirBloco(usuarioAtual.obter(autenticacao), identificador);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/planos-semanais/{plano}/ordem-dos-blocos")
    public RespostaDePlanoSemanal reordenar(
            @PathVariable UUID plano,
            @Valid @RequestBody RequisicaoDeOrdenacaoDosBlocos requisicao,
            Authentication autenticacao) {
        return RespostaDePlanoSemanal.de(servico.reordenarBlocos(
                usuarioAtual.obter(autenticacao), plano, requisicao.data(),
                requisicao.identificadoresOrdenados()));
    }
}
