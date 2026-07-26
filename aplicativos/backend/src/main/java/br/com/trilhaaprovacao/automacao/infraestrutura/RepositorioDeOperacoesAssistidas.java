package br.com.trilhaaprovacao.automacao.infraestrutura;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
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
    @Query("""
            select o from OperacaoAssistidaPersistida o
            where o.identificadorDoVinculo in :vinculos
              and o.estado in :estados
              and (
                o.codigoDeConfirmacaoHash = :codigoHash
                or o.codigoDeConfirmacaoAnteriorHash = :codigoHash
              )
            order by o.criadoEm desc
            """)
    List<OperacaoAssistidaPersistida> encontrarPorVinculosECodigoDeConfirmacao(
            @Param("vinculos") Collection<UUID> vinculos,
            @Param("codigoHash") String codigoHash,
            @Param("estados")
            Collection<br.com.trilhaaprovacao.automacao.dominio.EstadoDaOperacaoAssistida> estados);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from OperacaoAssistidaPersistida o
            where o.nivelDeConfirmacao = 'REFORCADA'
              and o.etapaDaConfirmacao = 1
              and o.codigoDeConfirmacaoAnteriorHash is null
            order by o.criadoEm asc
            """)
    List<OperacaoAssistidaPersistida>
            encontrarHashesAnterioresAusentes(Pageable limite);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OperacaoAssistidaPersistida>
            findTop100ByEstadoInAndExpiraEmLessThanEqualOrderByCriadoEmAsc(
                    Collection<br.com.trilhaaprovacao.automacao.dominio.EstadoDaOperacaoAssistida> estados,
                    java.time.OffsetDateTime agora);

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
