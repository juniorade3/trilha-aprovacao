package br.com.trilhaaprovacao.automacao.infraestrutura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDeCredenciaisDeIntegracao
        extends JpaRepository<CredencialDeIntegracaoPersistida, UUID> {
    Optional<CredencialDeIntegracaoPersistida>
            findFirstByIdentificadorDoVinculoAndRevogadoEmIsNullOrderByCriadoEmDesc(
                    UUID vinculo);
    List<CredencialDeIntegracaoPersistida>
            findByIdentificadorDoVinculoAndRevogadoEmIsNull(UUID vinculo);
}
