package br.com.trilhaaprovacao.conteudoprogramatico.infraestrutura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDeItensDoEdital
        extends JpaRepository<ItemDoEditalPersistido, UUID> {
    List<ItemDoEditalPersistido>
            findByIdentificadorDaMateriaDaProvaOrderByOrdemAscCriadoEmAsc(UUID materiaDaProva);

    Optional<ItemDoEditalPersistido>
            findByIdentificadorAndIdentificadorDaMateriaDaProva(UUID identificador, UUID materia);

    boolean existsByIdentificadorDoItemPai(UUID itemPai);

    boolean existsByIdentificadorDoEdital(UUID edital);

    boolean existsByIdentificadorDaMateriaDaProva(UUID materiaDaProva);

    @Query("""
            select i from ItemDoEditalPersistido i
            where i.identificador = :identificador
              and exists (
                  select m.identificador
                  from MateriaDaProvaPersistida m, GrupoPersistido g,
                       ProvaPersistida p, CargoPersistido c, ConcursoPersistido co
                  where m.identificador = i.identificadorDaMateriaDaProva
                    and g.identificador = m.identificadorDoGrupoDeConteudo
                    and p.identificador = g.identificadorDaProva
                    and c.identificador = p.identificadorDoCargo
                    and co.identificador = c.identificadorDoConcurso
                    and co.identificadorDoUsuario = :usuario
              )
            """)
    Optional<ItemDoEditalPersistido> encontrarDoUsuario(
            @Param("identificador") UUID identificador,
            @Param("usuario") UUID usuario);
}
