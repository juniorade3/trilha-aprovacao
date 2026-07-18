package br.com.trilhaaprovacao.dashboard.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.dashboard.aplicacao.ConsultaDoDashboard;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
public class ControladorDoDashboard {
    private final ConsultaDoDashboard consulta;
    private final IdentidadeDoUsuarioAtual usuarioAtual;

    public ControladorDoDashboard(
            ConsultaDoDashboard consulta, IdentidadeDoUsuarioAtual usuarioAtual) {
        this.consulta = consulta;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping
    public RespostaDoDashboard consultar(Authentication autenticacao) {
        return RespostaDoDashboard.de(
                consulta.consultar(usuarioAtual.obter(autenticacao)));
    }
}
