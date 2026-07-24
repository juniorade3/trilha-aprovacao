package br.com.trilhaaprovacao.estudos.infraestrutura;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDeMateriaisDeEstudo
        extends JpaRepository<MaterialDeEstudoPersistido, UUID> {
    Optional<MaterialDeEstudoPersistido> findByIdentificadorAndIdentificadorDoUsuario(
            UUID identificador, UUID usuario);
    Page<MaterialDeEstudoPersistido>
            findByIdentificadorDoUsuarioAndTituloContainingIgnoreCase(
                    UUID usuario, String pesquisa, Pageable pagina);
    Page<MaterialDeEstudoPersistido>
            findByIdentificadorDoUsuarioAndArquivadoAndTituloContainingIgnoreCase(
                    UUID usuario, boolean arquivado, String pesquisa, Pageable pagina);

    @Query("""
            SELECT m
            FROM MaterialDeEstudoPersistido m
            WHERE m.identificadorDoUsuario = :usuario
              AND (:incluirArquivados = true OR m.arquivado = false)
              AND LOWER(m.titulo) LIKE LOWER(CONCAT('%', :pesquisa, '%'))
              AND EXISTS (
                  SELECT c.identificador
                  FROM CoberturaDeTopicoPersistida c
                  WHERE c.identificadorDoMaterial = m.identificador
                    AND c.identificadorDoTopico = :topico
              )
            """)
    Page<MaterialDeEstudoPersistido> listarPorTopico(
            @Param("usuario") UUID usuario,
            @Param("topico") UUID topico,
            @Param("pesquisa") String pesquisa,
            @Param("incluirArquivados") boolean incluirArquivados,
            Pageable pagina);
}
