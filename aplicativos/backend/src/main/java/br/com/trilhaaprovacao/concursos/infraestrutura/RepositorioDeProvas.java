package br.com.trilhaaprovacao.concursos.infraestrutura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDeProvas extends JpaRepository<ProvaPersistida, UUID> {
    List<ProvaPersistida> findByIdentificadorDoCargoOrderByOrdemAscNomeAsc(UUID cargo);

    boolean existsByIdentificadorDoCargo(UUID cargo);

    boolean existsByIdentificadorDoCargoAndNomeNormalizado(UUID cargo, String nome);

    boolean existsByIdentificadorDoCargoAndNomeNormalizadoAndIdentificadorNot(
            UUID cargo, String nome, UUID identificador);

    @Query("""
            select p from ProvaPersistida p
            where p.identificador = :identificador
              and exists (
                  select c.identificador from CargoPersistido c, ConcursoPersistido co
                  where c.identificador = p.identificadorDoCargo
                    and co.identificador = c.identificadorDoConcurso
                    and co.identificadorDoUsuario = :usuario
              )
            """)
    Optional<ProvaPersistida> encontrarDoUsuario(
            @Param("identificador") UUID identificador,
            @Param("usuario") UUID usuario);
}
