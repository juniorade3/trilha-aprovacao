package br.com.trilhaaprovacao.concursos.infraestrutura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDeMateriasDaProva
        extends JpaRepository<MateriaDaProvaPersistida, UUID> {
    List<MateriaDaProvaPersistida> findByIdentificadorDoGrupoDeConteudoOrderByOrdemAsc(
            UUID grupo);

    boolean existsByIdentificadorDoGrupoDeConteudo(UUID grupo);

    boolean existsByIdentificadorDoGrupoDeConteudoAndIdentificadorDaMateria(
            UUID grupo, UUID materia);

    @Query("""
            select m from MateriaDaProvaPersistida m
            where m.identificador = :identificador
              and exists (
                  select g.identificador
                  from GrupoPersistido g, ProvaPersistida p, CargoPersistido c, ConcursoPersistido co
                  where g.identificador = m.identificadorDoGrupoDeConteudo
                    and p.identificador = g.identificadorDaProva
                    and c.identificador = p.identificadorDoCargo
                    and co.identificador = c.identificadorDoConcurso
                    and co.identificadorDoUsuario = :usuario
              )
            """)
    Optional<MateriaDaProvaPersistida> encontrarDoUsuario(
            @Param("identificador") UUID identificador,
            @Param("usuario") UUID usuario);
}
