package br.com.trilhaaprovacao.conteudos.infraestrutura;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioJpaDeMaterias extends JpaRepository<MateriaPersistida, UUID> {
    Optional<MateriaPersistida> findByIdentificadorAndIdentificadorDoUsuario(
            UUID identificador, UUID identificadorDoUsuario);

    List<MateriaPersistida> findByIdentificadorDoUsuarioAndIdentificadorIn(
            UUID identificadorDoUsuario, Collection<UUID> identificadores);

    boolean existsByIdentificadorDoUsuarioAndNomeNormalizado(
            UUID identificadorDoUsuario, String nomeNormalizado);

    boolean existsByIdentificadorDoUsuarioAndNomeNormalizadoAndIdentificadorNot(
            UUID identificadorDoUsuario, String nomeNormalizado, UUID identificador);

    Page<MateriaPersistida> findByIdentificadorDoUsuarioAndArquivadaAndNomeContainingIgnoreCase(
            UUID identificadorDoUsuario, boolean arquivada, String nome, Pageable pagina);

    Page<MateriaPersistida> findByIdentificadorDoUsuarioAndNomeContainingIgnoreCase(
            UUID identificadorDoUsuario, String nome, Pageable pagina);
}
