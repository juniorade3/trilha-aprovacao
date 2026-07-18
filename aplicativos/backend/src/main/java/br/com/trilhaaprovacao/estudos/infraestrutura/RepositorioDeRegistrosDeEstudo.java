package br.com.trilhaaprovacao.estudos.infraestrutura;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDeRegistrosDeEstudo
        extends JpaRepository<RegistroDeEstudoPersistido, UUID> {
    @Query("""
            select r from RegistroDeEstudoPersistido r
            where exists (
                select t.identificador from TopicoPersistido t, MateriaPersistida m
                where t.identificador = r.identificadorDoTopico
                  and m.identificador = t.identificadorDaMateria
                  and m.identificadorDoUsuario = :usuario
            )
            """)
    Page<RegistroDeEstudoPersistido> listarDoUsuario(
            @Param("usuario") UUID usuario, Pageable pagina);

    @Query("""
            select r from RegistroDeEstudoPersistido r
            where r.identificador = :identificador
              and exists (
                select t.identificador from TopicoPersistido t, MateriaPersistida m
                where t.identificador = r.identificadorDoTopico
                  and m.identificador = t.identificadorDaMateria
                  and m.identificadorDoUsuario = :usuario
              )
            """)
    Optional<RegistroDeEstudoPersistido> encontrarDoUsuario(
            @Param("identificador") UUID identificador,
            @Param("usuario") UUID usuario);

    boolean existsByIdentificadorDoMaterial(UUID material);
    boolean existsByIdentificadorDoTopico(UUID topico);
}
