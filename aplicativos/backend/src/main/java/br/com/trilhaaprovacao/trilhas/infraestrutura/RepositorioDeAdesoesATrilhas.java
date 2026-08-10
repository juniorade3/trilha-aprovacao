package br.com.trilhaaprovacao.trilhas.infraestrutura;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDeAdesoesATrilhas extends JpaRepository<AdesaoATrilhaPersistida, UUID> {
    Optional<AdesaoATrilhaPersistida> findByIdentificadorDoUsuarioAndIdentificadorDaTrilha(
            UUID usuario, UUID trilha);
}
