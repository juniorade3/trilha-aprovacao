package br.com.trilhaaprovacao.importacaoedital.infraestrutura;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDeImportacoesDeEdital
        extends JpaRepository<ImportacaoDeEditalPersistida, UUID> {
    Optional<ImportacaoDeEditalPersistida>
            findByIdentificadorAndIdentificadorDoUsuario(
                    UUID identificador, UUID usuario);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ImportacaoDeEditalPersistida>
            findFirstByIdentificadorDoUsuarioAndSha256OrderByCriadoEmDesc(
                    UUID usuario, String sha256);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i from ImportacaoDeEditalPersistida i
            where i.identificador = :identificador
              and i.identificadorDoUsuario = :usuario
            """)
    Optional<ImportacaoDeEditalPersistida> encontrarParaAtualizacao(
            @Param("identificador") UUID identificador,
            @Param("usuario") UUID usuario);

    @Query("""
            select i from ImportacaoDeEditalPersistida i
            where i.identificadorDoUsuario = :usuario
              and i.sha256 = :sha256
              and i.chaveDoCargoSelecionado = :cargo
              and i.versaoAtualDaExtracao = :versaoDaExtracao
              and i.identificador <> :ignorar
              and i.estado not in (
                  br.com.trilhaaprovacao.importacaoedital.dominio.EstadoDaImportacaoDeEdital.FALHOU,
                  br.com.trilhaaprovacao.importacaoedital.dominio.EstadoDaImportacaoDeEdital.CANCELADA
              )
            order by i.criadoEm desc
            """)
    List<ImportacaoDeEditalPersistida> encontrarLoteIdempotente(
            @Param("usuario") UUID usuario,
            @Param("sha256") String sha256,
            @Param("cargo") String cargo,
            @Param("versaoDaExtracao") int versaoDaExtracao,
            @Param("ignorar") UUID ignorar);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i from ImportacaoDeEditalPersistida i
            where i.reterConteudoAte <= :agora
              and (i.conteudoOriginal is not null or i.textoExtraido is not null)
            order by i.reterConteudoAte
            """)
    List<ImportacaoDeEditalPersistida> encontrarConteudosExpirados(
            @Param("agora") OffsetDateTime agora, Pageable limite);
}
