package br.com.trilhaaprovacao.estudos.infraestrutura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
