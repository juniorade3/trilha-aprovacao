package br.com.trilhaaprovacao.planejamento.infraestrutura;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDePrioridadesDeMateriasNoPlano
        extends JpaRepository<PrioridadeDeMateriaNoPlanoPersistida, UUID> {
    List<PrioridadeDeMateriaNoPlanoPersistida> findByIdentificadorDoPlano(
            UUID identificadorDoPlano);
}
