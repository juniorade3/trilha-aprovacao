package br.com.trilhaaprovacao.concursos.infraestrutura;

import br.com.trilhaaprovacao.concursos.dominio.SituacaoDoConcurso;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDeConcursos extends JpaRepository<ConcursoPersistido, UUID> {
    Optional<ConcursoPersistido> findByIdentificadorAndIdentificadorDoUsuario(
            UUID identificador, UUID identificadorDoUsuario);

    Optional<ConcursoPersistido> findByIdentificadorDoUsuarioAndAtivoTrue(UUID identificadorDoUsuario);

    Page<ConcursoPersistido> findByIdentificadorDoUsuarioAndNomeContainingIgnoreCase(
            UUID usuario, String pesquisa, Pageable pagina);

    Page<ConcursoPersistido> findByIdentificadorDoUsuarioAndSituacaoNotAndNomeContainingIgnoreCase(
            UUID usuario, SituacaoDoConcurso situacao, String pesquisa, Pageable pagina);

}
