package br.com.trilhaaprovacao.concursos.infraestrutura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDeCargos extends JpaRepository<CargoPersistido, UUID> {
    List<CargoPersistido> findByIdentificadorDoConcursoOrderByOrdemAscNomeAsc(UUID concurso);

    Optional<CargoPersistido> findByIdentificadorDoConcursoAndSelecionadoTrue(UUID concurso);

    boolean existsByIdentificadorDoConcurso(UUID concurso);

    boolean existsByIdentificadorDoConcursoAndNomeNormalizado(UUID concurso, String nome);

    boolean existsByIdentificadorDoConcursoAndNomeNormalizadoAndIdentificadorNot(
            UUID concurso, String nome, UUID identificador);

    @Query("""
            select c from CargoPersistido c
            where c.identificador = :identificador
              and exists (
                  select co.identificador from ConcursoPersistido co
                  where co.identificador = c.identificadorDoConcurso
                    and co.identificadorDoUsuario = :usuario
              )
            """)
    Optional<CargoPersistido> encontrarDoUsuario(
            @Param("identificador") UUID identificador,
            @Param("usuario") UUID usuario);
}
