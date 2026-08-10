package br.com.trilhaaprovacao.trilhas.infraestrutura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDeAcompanhamentosDeTarefas
        extends JpaRepository<AcompanhamentoDaTarefaPersistido, UUID> {
    List<AcompanhamentoDaTarefaPersistido> findByIdentificadorDaAdesao(UUID adesao);

    Optional<AcompanhamentoDaTarefaPersistido> findByIdentificadorDaAdesaoAndIdentificadorDaTarefa(
            UUID adesao, UUID tarefa);
}
