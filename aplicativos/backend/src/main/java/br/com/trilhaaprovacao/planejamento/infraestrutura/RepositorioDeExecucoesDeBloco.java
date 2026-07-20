package br.com.trilhaaprovacao.planejamento.infraestrutura;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDeExecucoesDeBloco
        extends JpaRepository<ExecucaoDoBlocoPersistida, UUID> {
    Optional<ExecucaoDoBlocoPersistida> findByIdentificadorDoBloco(UUID bloco);

    Optional<ExecucaoDoBlocoPersistida>
            findByIdentificadorDoUsuarioAndEncerradaEmIsNull(UUID usuario);
}
