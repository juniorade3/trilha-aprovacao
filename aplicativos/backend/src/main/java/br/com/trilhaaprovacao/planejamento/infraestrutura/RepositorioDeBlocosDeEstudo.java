package br.com.trilhaaprovacao.planejamento.infraestrutura;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDeBlocosDeEstudo
        extends JpaRepository<BlocoDeEstudoPersistido, UUID> {
    List<BlocoDeEstudoPersistido> findByIdentificadorDoPlanoOrderByDataAscOrdemAsc(
            UUID plano);

    List<BlocoDeEstudoPersistido> findByIdentificadorDoPlanoAndDataOrderByOrdemAsc(
            UUID plano, LocalDate data);

    List<BlocoDeEstudoPersistido> findByIdentificadorDoPlanoAndDataBeforeOrderByDataAscOrdemAsc(
            UUID plano, LocalDate data);

    @Query("""
            select b from BlocoDeEstudoPersistido b, PlanoSemanalPersistido p
            where b.identificador = :identificador
              and p.identificador = b.identificadorDoPlano
              and p.identificadorDoUsuario = :usuario
            """)
    Optional<BlocoDeEstudoPersistido> encontrarDoUsuario(
            @Param("identificador") UUID identificador,
            @Param("usuario") UUID usuario);
}
