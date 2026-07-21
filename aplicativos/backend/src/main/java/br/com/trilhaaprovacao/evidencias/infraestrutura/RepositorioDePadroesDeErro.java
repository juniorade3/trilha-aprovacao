package br.com.trilhaaprovacao.evidencias.infraestrutura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDePadroesDeErro extends JpaRepository<PadraoDeErroPersistido, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO padroes_de_erro (
                identificador, usuario_id, topico_id, descricao,
                descricao_normalizada, criado_em, atualizado_em, versao)
            VALUES (
                :identificador, :usuario, :topico, :descricao,
                :normalizada, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            ON CONFLICT (usuario_id, topico_id, descricao_normalizada) DO NOTHING
            """, nativeQuery = true)
    int inserirSeAusente(
            @Param("identificador") UUID identificador,
            @Param("usuario") UUID usuario,
            @Param("topico") UUID topico,
            @Param("descricao") String descricao,
            @Param("normalizada") String descricaoNormalizada);

    Optional<PadraoDeErroPersistido>
            findByIdentificadorDoUsuarioAndIdentificadorDoTopicoAndDescricaoNormalizada(
                    UUID usuario, UUID topico, String descricaoNormalizada);

    List<PadraoDeErroPersistido>
            findTop20ByIdentificadorDoUsuarioAndIdentificadorDoTopicoAndDescricaoContainingIgnoreCaseOrderByDescricaoAsc(
                    UUID usuario, UUID topico, String pesquisa);
}
