package br.com.trilhaaprovacao.estudos.infraestrutura;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDeRequisicoesIdempotentesDeEstudo
        extends JpaRepository<RequisicaoIdempotenteDeEstudoPersistida, UUID> {

    Optional<RequisicaoIdempotenteDeEstudoPersistida>
            findByIdentificadorDoUsuarioAndChaveDeIdempotencia(
                    UUID usuario, String chave);

    @Query(value = """
            select 1
            from pg_advisory_xact_lock(
                hashtextextended(
                    'registro-de-estudo:' || cast(:usuario as text)
                        || ':' || :chave,
                    0
                )
            )
            """, nativeQuery = true)
    Integer bloquearChaveDeIdempotencia(
            @Param("usuario") UUID usuario, @Param("chave") String chave);
}
