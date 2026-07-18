package br.com.trilhaaprovacao.conteudos.infraestrutura;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioJpaDeTopicos extends JpaRepository<TopicoPersistido, UUID> {
    Page<TopicoPersistido> findByIdentificadorDaMateriaAndArquivadoAndNomeContainingIgnoreCase(
            UUID identificadorDaMateria, boolean arquivado, String nome, Pageable pagina);

    Page<TopicoPersistido> findByIdentificadorDaMateriaAndNomeContainingIgnoreCase(
            UUID identificadorDaMateria, String nome, Pageable pagina);

    Optional<TopicoPersistido> findByIdentificadorAndIdentificadorDaMateria(
            UUID identificador, UUID identificadorDaMateria);

    @Query("""
            select t from TopicoPersistido t
            where t.identificador = :identificador
              and exists (
                  select m.identificador from MateriaPersistida m
                  where m.identificador = t.identificadorDaMateria
                    and m.identificadorDoUsuario = :usuario
              )
            """)
    Optional<TopicoPersistido> encontrarDoUsuario(
            @Param("identificador") UUID identificador,
            @Param("usuario") UUID identificadorDoUsuario);

    @Query("""
            select count(t) > 0 from TopicoPersistido t
            where t.identificadorDaMateria = :materia
              and ((:pai is null and t.identificadorDoTopicoPai is null) or t.identificadorDoTopicoPai = :pai)
              and t.nomeNormalizado = :nome
              and (:ignorado is null or t.identificador <> :ignorado)
            """)
    boolean existeIrmaoComNome(
            @Param("materia") UUID identificadorDaMateria,
            @Param("pai") UUID identificadorDoTopicoPai,
            @Param("nome") String nomeNormalizado,
            @Param("ignorado") UUID identificadorIgnorado);

    boolean existsByIdentificadorDoTopicoPai(UUID identificadorDoTopicoPai);

    boolean existsByIdentificadorDaMateria(UUID identificadorDaMateria);
}
