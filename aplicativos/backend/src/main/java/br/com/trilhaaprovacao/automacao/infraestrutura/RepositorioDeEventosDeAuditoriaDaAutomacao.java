package br.com.trilhaaprovacao.automacao.infraestrutura;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDeEventosDeAuditoriaDaAutomacao extends
        JpaRepository<EventoDeAuditoriaDaAutomacaoPersistido, UUID> {
}
