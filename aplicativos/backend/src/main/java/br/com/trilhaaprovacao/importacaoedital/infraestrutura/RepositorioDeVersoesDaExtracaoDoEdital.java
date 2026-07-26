package br.com.trilhaaprovacao.importacaoedital.infraestrutura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDeVersoesDaExtracaoDoEdital
        extends JpaRepository<VersaoDaExtracaoDoEditalPersistida, UUID> {
    Optional<VersaoDaExtracaoDoEditalPersistida>
            findByIdentificadorAndIdentificadorDoUsuario(
                    UUID identificador, UUID usuario);

    Optional<VersaoDaExtracaoDoEditalPersistida>
            findFirstByIdentificadorDaImportacaoAndIdentificadorDoUsuarioOrderByNumeroDaVersaoDesc(
                    UUID importacao, UUID usuario);

    List<VersaoDaExtracaoDoEditalPersistida>
            findByIdentificadorDaImportacaoAndIdentificadorDoUsuarioOrderByNumeroDaVersaoDesc(
                    UUID importacao, UUID usuario);
}
