package br.com.trilhaaprovacao.estudos.infraestrutura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDeCoberturasDeTopicos
        extends JpaRepository<CoberturaDeTopicoPersistida, UUID> {
    List<CoberturaDeTopicoPersistida>
            findByIdentificadorDoMaterialOrderByCriadoEmAsc(UUID material);
    Optional<CoberturaDeTopicoPersistida>
            findByIdentificadorDoMaterialAndIdentificadorDoTopico(UUID material, UUID topico);
    boolean existsByIdentificadorDoMaterialAndIdentificadorDoTopico(
            UUID material, UUID topico);
    boolean existsByIdentificadorDoMaterial(UUID material);
    boolean existsByIdentificadorDoTopico(UUID topico);

    @Query("""
            SELECT c.identificadorDoTopico AS identificadorDoTopico,
                   m.identificador AS identificadorDoMaterial,
                   m.titulo AS tituloDoMaterial
            FROM CoberturaDeTopicoPersistida c
            JOIN MaterialDeEstudoPersistido m
              ON m.identificador = c.identificadorDoMaterial
            WHERE m.identificadorDoUsuario = :usuario
              AND m.arquivado = false
              AND c.identificadorDoTopico IN :topicos
            ORDER BY c.identificadorDoTopico, m.titulo, m.identificador
            """)
    List<ProjecaoDeMaterialRelacionadoAoTopico> listarMateriaisAtivosDosTopicos(
            @Param("usuario") UUID usuario,
            @Param("topicos") List<UUID> topicos);
}
