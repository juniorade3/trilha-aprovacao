package br.com.trilhaaprovacao.planejamento.infraestrutura;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDePlanosSemanais
        extends JpaRepository<PlanoSemanalPersistido, UUID> {
    Optional<PlanoSemanalPersistido> findByIdentificadorDoUsuarioAndDataInicial(
            UUID usuario, LocalDate dataInicial);

    Optional<PlanoSemanalPersistido> findByIdentificadorAndIdentificadorDoUsuario(
            UUID identificador, UUID usuario);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from PlanoSemanalPersistido p
            where p.identificador = :identificador
              and p.identificadorDoUsuario = :usuario
            """)
    Optional<PlanoSemanalPersistido> encontrarParaAtualizacao(
            @Param("identificador") UUID identificador,
            @Param("usuario") UUID usuario);

    boolean existsByIdentificadorDoUsuarioAndDataInicial(
            UUID usuario, LocalDate dataInicial);
}
