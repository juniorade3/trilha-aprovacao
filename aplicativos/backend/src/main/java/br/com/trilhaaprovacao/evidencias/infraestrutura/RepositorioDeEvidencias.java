package br.com.trilhaaprovacao.evidencias.infraestrutura;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDeEvidencias
        extends JpaRepository<EvidenciaDeAprendizagemPersistida, UUID> {
    Optional<EvidenciaDeAprendizagemPersistida> findByIdentificadorDoRegistroDeEstudo(UUID registro);
}
