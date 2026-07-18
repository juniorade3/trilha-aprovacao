package br.com.trilhaaprovacao.concursos.infraestrutura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDeEditais extends JpaRepository<EditalPersistido, UUID> {
    List<EditalPersistido> findByIdentificadorDoConcursoOrderByPrincipalDescCriadoEmAsc(UUID concurso);

    Optional<EditalPersistido> findByIdentificadorDoConcursoAndPrincipalTrue(UUID concurso);

    boolean existsByIdentificadorDoConcurso(UUID concurso);

    @Query("""
            select e from EditalPersistido e
            where e.identificador = :identificador
              and exists (
                  select c.identificador from ConcursoPersistido c
                  where c.identificador = e.identificadorDoConcurso
                    and c.identificadorDoUsuario = :usuario
              )
            """)
    Optional<EditalPersistido> encontrarDoUsuario(
            @Param("identificador") UUID identificador,
            @Param("usuario") UUID usuario);
}
