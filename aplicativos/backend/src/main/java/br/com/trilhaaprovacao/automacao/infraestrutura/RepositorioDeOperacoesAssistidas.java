package br.com.trilhaaprovacao.automacao.infraestrutura;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDeOperacoesAssistidas
        extends JpaRepository<OperacaoAssistidaPersistida, UUID> {
    Page<OperacaoAssistidaPersistida> findByIdentificadorDoUsuario(
            UUID usuario, Pageable pagina);
    Optional<OperacaoAssistidaPersistida> findByIdentificadorAndIdentificadorDoUsuario(
            UUID identificador, UUID usuario);
    Optional<OperacaoAssistidaPersistida>
            findByIdentificadorDoUsuarioAndChaveDeIdempotencia(
                    UUID usuario, String chaveDeIdempotencia);

    @Query(value = """
            select 1
            from pg_advisory_xact_lock(
                hashtextextended(cast(:usuario as text) || ':' || :chave, 0)
            )
            """, nativeQuery = true)
    Integer bloquearChaveDeIdempotencia(
            @Param("usuario") UUID usuario, @Param("chave") String chave);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from OperacaoAssistidaPersistida o
            where o.identificador = :identificador
              and o.identificadorDoUsuario = :usuario
            """)
    Optional<OperacaoAssistidaPersistida>
            encontrarParaAtualizacao(
                    @Param("identificador") UUID identificador,
                    @Param("usuario") UUID usuario);
}
