package br.com.trilhaaprovacao.automacao.infraestrutura;

import br.com.trilhaaprovacao.automacao.dominio.CanalDeIntegracao;
import br.com.trilhaaprovacao.automacao.dominio.EstadoDoVinculoDeCanal;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDeVinculosDeCanal
        extends JpaRepository<VinculoDeCanalPersistido, UUID> {
    Optional<VinculoDeCanalPersistido>
            findFirstByIdentificadorDoUsuarioAndCanalAndEstadoInOrderByCriadoEmDesc(
                    UUID usuario, CanalDeIntegracao canal,
                    Collection<EstadoDoVinculoDeCanal> estados);

    Optional<VinculoDeCanalPersistido>
            findByIdentificadorAndIdentificadorDoUsuario(UUID identificador, UUID usuario);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select v from VinculoDeCanalPersistido v
            where v.identificador = :identificador
              and v.identificadorDoUsuario = :usuario
            """)
    Optional<VinculoDeCanalPersistido> encontrarParaAtualizacao(
            @Param("identificador") UUID identificador,
            @Param("usuario") UUID usuario);

    @Query(value = """
            select 1
            from pg_advisory_xact_lock(
                hashtextextended('vinculo-telegram:' || cast(:usuario as text), 0)
            )
            """, nativeQuery = true)
    Integer bloquearGeracaoParaUsuario(@Param("usuario") UUID usuario);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VinculoDeCanalPersistido> findByCodigoDeVinculoHashAndEstado(
            String codigoHash, EstadoDoVinculoDeCanal estado);

    boolean existsByCanalAndIdentificadorDoBotAndIdentificadorExternoAndEstado(
            CanalDeIntegracao canal, long bot, long identificadorExterno,
            EstadoDoVinculoDeCanal estado);
}
