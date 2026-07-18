package br.com.trilhaaprovacao.concursos.infraestrutura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDeGrupos extends JpaRepository<GrupoPersistido, UUID> {
    List<GrupoPersistido> findByIdentificadorDaProvaOrderByOrdemAscNomeAsc(UUID prova);

    boolean existsByIdentificadorDaProva(UUID prova);

    boolean existsByIdentificadorDaProvaAndNomeNormalizado(UUID prova, String nome);

    boolean existsByIdentificadorDaProvaAndNomeNormalizadoAndIdentificadorNot(
            UUID prova, String nome, UUID identificador);

    @Query("""
            select g from GrupoPersistido g
            where g.identificador = :identificador
              and exists (
                  select p.identificador
                  from ProvaPersistida p, CargoPersistido c, ConcursoPersistido co
                  where p.identificador = g.identificadorDaProva
                    and c.identificador = p.identificadorDoCargo
                    and co.identificador = c.identificadorDoConcurso
                    and co.identificadorDoUsuario = :usuario
              )
            """)
    Optional<GrupoPersistido> encontrarDoUsuario(
            @Param("identificador") UUID identificador,
            @Param("usuario") UUID usuario);
}
