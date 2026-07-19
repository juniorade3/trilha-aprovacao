package br.com.trilhaaprovacao.planejamento.infraestrutura;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDePlanosSemanais
        extends JpaRepository<PlanoSemanalPersistido, UUID> {
    Optional<PlanoSemanalPersistido> findByIdentificadorDoUsuarioAndDataInicial(
            UUID usuario, LocalDate dataInicial);

    Optional<PlanoSemanalPersistido> findByIdentificadorAndIdentificadorDoUsuario(
            UUID identificador, UUID usuario);

    boolean existsByIdentificadorDoUsuarioAndDataInicial(
            UUID usuario, LocalDate dataInicial);
}
