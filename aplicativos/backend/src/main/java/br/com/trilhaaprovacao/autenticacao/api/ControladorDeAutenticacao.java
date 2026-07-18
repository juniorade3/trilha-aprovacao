package br.com.trilhaaprovacao.autenticacao.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.ServicoDeUsuarios;
import br.com.trilhaaprovacao.autenticacao.aplicacao.UsuarioDaAplicacao;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/autenticacao")
@Tag(name = "Autenticação")
public class ControladorDeAutenticacao {
    private final ServicoDeUsuarios servicoDeUsuarios;
    private final AuthenticationManager gerenciadorDeAutenticacao;
    private final SecurityContextRepository repositorioDeContexto;

    public ControladorDeAutenticacao(ServicoDeUsuarios servicoDeUsuarios,
            AuthenticationManager gerenciadorDeAutenticacao, SecurityContextRepository repositorioDeContexto) {
        this.servicoDeUsuarios = servicoDeUsuarios;
        this.gerenciadorDeAutenticacao = gerenciadorDeAutenticacao;
        this.repositorioDeContexto = repositorioDeContexto;
    }

    @PostMapping("/cadastro")
    public ResponseEntity<RespostaDeUsuario> cadastrar(@Valid @RequestBody RequisicaoDeCadastro requisicao) {
        UsuarioDaAplicacao usuario = servicoDeUsuarios.cadastrar(
                requisicao.nome(), requisicao.email(), requisicao.senha());
        return ResponseEntity.created(URI.create("/api/v1/autenticacao/usuarios/" + usuario.identificador()))
                .body(RespostaDeUsuario.de(usuario));
    }

    @PostMapping("/login")
    public RespostaDeSessao login(@Valid @RequestBody RequisicaoDeLogin requisicao, HttpServletRequest pedido, HttpServletResponse resposta) {
        Authentication autenticacao = gerenciadorDeAutenticacao.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(requisicao.email(), requisicao.senha()));
        var sessaoAnterior = pedido.getSession(false);
        if (sessaoAnterior != null) {
            sessaoAnterior.invalidate();
        }
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacao);
        SecurityContextHolder.setContext(contexto);
        repositorioDeContexto.saveContext(contexto, pedido, resposta);
        return sessaoDaAutenticacao(autenticacao);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest pedido, HttpServletResponse resposta) {
        var sessao = pedido.getSession(false);
        if (sessao != null) {
            sessao.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessao")
    public RespostaDeSessao sessao(Authentication autenticacao) {
        return sessaoDaAutenticacao(autenticacao);
    }

    @GetMapping("/csrf")
    public RespostaDeCsrf csrf(CsrfToken token) {
        return new RespostaDeCsrf(token.getToken(), token.getHeaderName(), token.getParameterName());
    }

    private RespostaDeSessao sessaoDaAutenticacao(Authentication autenticacao) {
        UsuarioDaAplicacao usuario =
                servicoDeUsuarios.consultarPorEmail(autenticacao.getName());
        return new RespostaDeSessao(true, RespostaDeUsuario.de(usuario));
    }
}
