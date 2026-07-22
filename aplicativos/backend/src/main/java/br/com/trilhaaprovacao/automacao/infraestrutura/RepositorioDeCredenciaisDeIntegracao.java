package br.com.trilhaaprovacao.automacao.infraestrutura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDeCredenciaisDeIntegracao
        extends JpaRepository<CredencialDeIntegracaoPersistida, UUID> {
    Optional<CredencialDeIntegracaoPersistida>
            findFirstByIdentificadorDoVinculoAndRevogadoEmIsNullOrderByCriadoEmDesc(
                    UUID vinculo);
    List<CredencialDeIntegracaoPersistida>
            findByIdentificadorDoVinculoAndRevogadoEmIsNull(UUID vinculo);
    Optional<CredencialDeIntegracaoPersistida> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE credenciais_de_integracao
               SET ultimo_uso_em = GREATEST(
                     COALESCE(ultimo_uso_em, :agora), :agora),
                   versao = versao + 1
             WHERE identificador = :identificador
               AND revogado_em IS NULL
               AND expira_em > :agora
            """, nativeQuery = true)
    int registrarUso(@Param("identificador") UUID identificador,
            @Param("agora") java.time.OffsetDateTime agora);
}
