package br.com.trilhaaprovacao.conteudos.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.compartilhado.api.RespostaPaginada;
import br.com.trilhaaprovacao.conteudos.aplicacao.ConsultaDeUsoDaMateria;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeMaterias;
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
@RequestMapping("/api/v1/materias")
@Tag(name = "Matérias e tópicos")
public class ControladorDeMaterias {
    private final ServicoDeMaterias materias;
    private final ConsultaDeUsoDaMateria consultaDeUso;
    private final IdentidadeDoUsuarioAtual usuarioAtual;

    public ControladorDeMaterias(
            ServicoDeMaterias materias,
            ConsultaDeUsoDaMateria consultaDeUso,
            IdentidadeDoUsuarioAtual usuarioAtual) {
        this.materias = materias;
        this.consultaDeUso = consultaDeUso;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping
    public ResponseEntity<RespostaDeMateria> criar(
            @Valid @RequestBody RequisicaoDeMateria requisicao, Authentication autenticacao) {
        var materia = materias.criar(usuarioAtual.obter(autenticacao),
                requisicao.nome(), requisicao.descricao(), requisicao.cor());
        return ResponseEntity.created(URI.create("/api/v1/materias/" + materia.identificador()))
                .body(RespostaDeMateria.de(materia));
    }

    @GetMapping
    public RespostaPaginada<RespostaDeMateria> listar(
            @RequestParam(defaultValue = "") String pesquisa,
            @RequestParam(defaultValue = "false") boolean incluirArquivadas,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            Authentication autenticacao) {
        var resultado = materias.listar(usuarioAtual.obter(autenticacao), pesquisa,
                incluirArquivadas, pagina, tamanho);
        return new RespostaPaginada<>(resultado.map(RespostaDeMateria::de).getContent(),
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements(), resultado.getTotalPages());
    }

    @GetMapping("/{identificador}")
    public RespostaDeMateria obter(@PathVariable UUID identificador, Authentication autenticacao) {
        return RespostaDeMateria.de(materias.obter(usuarioAtual.obter(autenticacao), identificador));
    }

    @GetMapping("/{identificador}/uso")
    public RespostaDeUsoDaMateria consultarUso(
            @PathVariable UUID identificador, Authentication autenticacao) {
        return RespostaDeUsoDaMateria.de(consultaDeUso.consultar(
                usuarioAtual.obter(autenticacao), identificador));
    }

    @PutMapping("/{identificador}")
    public RespostaDeMateria alterar(@PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeMateria requisicao, Authentication autenticacao) {
        return RespostaDeMateria.de(materias.alterar(usuarioAtual.obter(autenticacao), identificador,
                requisicao.nome(), requisicao.descricao(), requisicao.cor()));
    }

    @PostMapping("/{identificador}/arquivamento")
    public RespostaDeMateria definirArquivamento(@PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeArquivamento requisicao, Authentication autenticacao) {
        return RespostaDeMateria.de(materias.definirArquivamento(
                usuarioAtual.obter(autenticacao), identificador, requisicao.arquivada()));
    }

    @DeleteMapping("/{identificador}")
    public ResponseEntity<Void> excluir(@PathVariable UUID identificador, Authentication autenticacao) {
        materias.excluir(usuarioAtual.obter(autenticacao), identificador);
        return ResponseEntity.noContent().build();
    }
}
