package br.com.trilhaaprovacao.planejamento.infraestrutura;

import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDeDisponibilidadesDoDia
        extends JpaRepository<DisponibilidadeDoDiaPersistida, UUID> {
    List<DisponibilidadeDoDiaPersistida> findByIdentificadorDoPlanoOrderByDataAsc(
            UUID plano);

    Optional<DisponibilidadeDoDiaPersistida> findByIdentificadorDoPlanoAndData(
            UUID plano, LocalDate data);
}
