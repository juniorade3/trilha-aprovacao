package br.com.trilhaaprovacao.automacao.aplicacao;

import br.com.trilhaaprovacao.automacao.dominio.EventoDeAuditoriaDaAutomacao;
import br.com.trilhaaprovacao.automacao.infraestrutura.EventoDeAuditoriaDaAutomacaoPersistido;
import br.com.trilhaaprovacao.automacao.infraestrutura.IdentidadeDaIntegracaoMcp;
import br.com.trilhaaprovacao.automacao.infraestrutura.RepositorioDeEventosDeAuditoriaDaAutomacao;
import br.com.trilhaaprovacao.automacao.infraestrutura.ServicoDeSegredosDaAutomacao;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoDeAuditoriaMcp {
    private final RepositorioDeEventosDeAuditoriaDaAutomacao eventos;
    private final ServicoDeSegredosDaAutomacao segredos;

    public ServicoDeAuditoriaMcp(
            RepositorioDeEventosDeAuditoriaDaAutomacao eventos,
            ServicoDeSegredosDaAutomacao segredos) {
        this.eventos = eventos;
        this.segredos = segredos;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(IdentidadeDaIntegracaoMcp identidade,
            String ferramenta, String entradaCanonica, String saidaCanonica,
            String resultado, UUID correlacao) {
        String hashDaEntrada = entradaCanonica == null
                ? null : segredos.hash("entrada-mcp\n" + entradaCanonica);
        String hashDaSaida = saidaCanonica == null
                ? null : segredos.hash("saida-mcp\n" + saidaCanonica);
        EventoDeAuditoriaDaAutomacao evento =
                EventoDeAuditoriaDaAutomacao.criar(
                        identidade.identificadorDoUsuario(),
                        identidade.identificadorDoVinculo(), null,
                        "IA_TELEGRAM", ferramenta, "FERRAMENTA_MCP_CONSULTADA",
                        hashDaEntrada, hashDaSaida, "MCP", resultado,
                        correlacao, "{}", OffsetDateTime.now(ZoneOffset.UTC));
        eventos.save(new EventoDeAuditoriaDaAutomacaoPersistido(evento));
    }
}
