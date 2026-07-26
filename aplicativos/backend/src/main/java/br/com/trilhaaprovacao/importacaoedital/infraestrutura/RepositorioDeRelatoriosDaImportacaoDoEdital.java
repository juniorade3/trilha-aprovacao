package br.com.trilhaaprovacao.importacaoedital.infraestrutura;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDeRelatoriosDaImportacaoDoEdital
        extends JpaRepository<RelatorioDaImportacaoDoEditalPersistido, UUID> {
    Optional<RelatorioDaImportacaoDoEditalPersistido>
            findByIdentificadorAndIdentificadorDoUsuario(
                    UUID identificador, UUID usuario);

    Optional<RelatorioDaImportacaoDoEditalPersistido>
            findByIdentificadorDaImportacaoAndIdentificadorDoUsuario(
                    UUID importacao, UUID usuario);
}
