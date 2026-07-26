package br.com.trilhaaprovacao.importacaoedital.infraestrutura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDeProvenienciasDaImportacaoDoEdital
        extends JpaRepository<ProvenienciaDaImportacaoDoEditalPersistida, UUID> {
    Optional<ProvenienciaDaImportacaoDoEditalPersistida>
            findByIdentificadorAndIdentificadorDoUsuario(
                    UUID identificador, UUID usuario);

    List<ProvenienciaDaImportacaoDoEditalPersistida>
            findByIdentificadorDaImportacaoAndIdentificadorDoUsuarioOrderByCriadoEmAsc(
                    UUID importacao, UUID usuario);
}
