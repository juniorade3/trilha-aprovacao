package br.com.trilhaaprovacao.trilhas.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.trilhas.aplicacao.ServicoDeTrilhasPublicadas;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trilhas")
@Tag(name = "Trilhas publicadas")
public class ControladorDeTrilhasPublicadas {
    private final ServicoDeTrilhasPublicadas trilhas;
    private final IdentidadeDoUsuarioAtual usuarioAtual;

    public ControladorDeTrilhasPublicadas(ServicoDeTrilhasPublicadas trilhas,
            IdentidadeDoUsuarioAtual usuarioAtual) {
        this.trilhas = trilhas;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping
    public List<RespostaDeTrilhaPublicada> listar(Authentication autenticacao) {
        return trilhas.listar(usuario(autenticacao)).stream()
                .map(RespostaDeTrilhaPublicada::de).toList();
    }

    @GetMapping("/{identificador}")
    public RespostaDetalhadaDeTrilhaPublicada detalhar(
            @PathVariable UUID identificador, Authentication autenticacao) {
        return RespostaDetalhadaDeTrilhaPublicada.de(
                trilhas.detalhar(usuario(autenticacao), identificador));
    }

    @PostMapping("/{identificador}/adesao")
    public RespostaDeTrilhaPublicada aderir(
            @PathVariable UUID identificador, Authentication autenticacao) {
        return RespostaDeTrilhaPublicada.de(trilhas.aderir(usuario(autenticacao), identificador));
    }

    @PutMapping("/{trilha}/tarefas/{tarefa}/acompanhamento")
    public RespostaDeTarefaDaTrilha atualizarAcompanhamento(
            @PathVariable UUID trilha,
            @PathVariable UUID tarefa,
            @Valid @RequestBody RequisicaoDeAcompanhamentoDaTarefa requisicao,
            Authentication autenticacao) {
        return RespostaDeTarefaDaTrilha.de(trilhas.atualizarAcompanhamento(usuario(autenticacao),
                trilha, tarefa, requisicao.situacao(), requisicao.observacao()));
    }

    private UUID usuario(Authentication autenticacao) {
        return usuarioAtual.obter(autenticacao);
    }
}
