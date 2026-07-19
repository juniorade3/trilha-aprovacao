package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.planejamento.aplicacao.ConsultaDoPlanejamentoDeHoje;
import br.com.trilhaaprovacao.planejamento.aplicacao.ServicoDePlanejamento;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/planejamento")
@Tag(name = "Planejamento")
public class ControladorDePlanejamento {
    private final ConsultaDoPlanejamentoDeHoje consulta;
    private final ServicoDePlanejamento servico;
    private final IdentidadeDoUsuarioAtual usuarioAtual;

    public ControladorDePlanejamento(ConsultaDoPlanejamentoDeHoje consulta,
            ServicoDePlanejamento servico,
            IdentidadeDoUsuarioAtual usuarioAtual) {
        this.consulta = consulta;
        this.servico = servico;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping("/hoje")
    public RespostaDoPlanejamentoDeHoje consultarHoje(
            @RequestParam LocalDate data, Authentication autenticacao) {
        return RespostaDoPlanejamentoDeHoje.de(
                consulta.consultar(usuarioAtual.obter(autenticacao), data));
    }

    @GetMapping("/execucao-em-andamento")
    public RespostaDaExecucaoDoBloco consultarExecucaoEmAndamento(
            Authentication autenticacao) {
        return RespostaDaExecucaoDoBloco.de(
                servico.obterExecucaoEmAndamento(usuarioAtual.obter(autenticacao)));
    }
}
