package br.com.trilhaaprovacao.autenticacao.infraestrutura;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDeUsuarios extends JpaRepository<UsuarioPersistido, UUID> {
    Optional<UsuarioPersistido> findByEmail(String email);
    boolean existsByEmail(String email);
}
