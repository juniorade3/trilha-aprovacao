package br.com.trilhaaprovacao.planejamento.infraestrutura;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDeExecucoesDeBloco
        extends JpaRepository<ExecucaoDoBlocoPersistida, UUID> {
    Optional<ExecucaoDoBlocoPersistida> findByIdentificadorDoBloco(UUID bloco);
    Optional<ExecucaoDoBlocoPersistida>
            findByIdentificadorDoUsuarioAndEncerradaEmIsNull(UUID usuario);
    Optional<ExecucaoDoBlocoPersistida>
            findByIdentificadorAndIdentificadorDoUsuario(UUID identificador, UUID usuario);

    @org.springframework.data.jpa.repository.Query("""
            select e from ExecucaoDoBlocoPersistida e, BlocoDeEstudoPersistido b
            where e.identificadorDoBloco = b.identificador
              and b.identificadorDoPlano = :plano
              and e.identificadorDoUsuario = :usuario
            """)
    List<ExecucaoDoBlocoPersistida> encontrarDoPlanoEUsuario(
            @org.springframework.data.repository.query.Param("plano") UUID plano,
            @org.springframework.data.repository.query.Param("usuario") UUID usuario);
}
