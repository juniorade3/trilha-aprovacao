package br.com.trilhaaprovacao.conteudoprogramatico.infraestrutura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDeMapeamentosDeItens
        extends JpaRepository<MapeamentoDeItemDoEditalPersistido, UUID> {
    List<MapeamentoDeItemDoEditalPersistido>
            findByIdentificadorDoItemDoEditalOrderByCriadoEmAsc(UUID item);

    boolean existsByIdentificadorDoItemDoEdital(UUID item);

    boolean existsByIdentificadorDoTopicoDaMateria(UUID topico);

    boolean existsByIdentificadorDoItemDoEditalAndIdentificadorDoTopicoDaMateria(
            UUID item, UUID topico);

    Optional<MapeamentoDeItemDoEditalPersistido>
            findByIdentificadorDoItemDoEditalAndIdentificadorDoTopicoDaMateria(
                    UUID item, UUID topico);
}
