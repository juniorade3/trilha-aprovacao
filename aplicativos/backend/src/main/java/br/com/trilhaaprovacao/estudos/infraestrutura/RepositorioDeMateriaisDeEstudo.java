package br.com.trilhaaprovacao.estudos.infraestrutura;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
