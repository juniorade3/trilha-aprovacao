package br.com.trilhaaprovacao.trilhas.infraestrutura;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDeTarefasPublicadasDaTrilha
        extends JpaRepository<TarefaPublicadaPersistida, UUID> {
    List<TarefaPublicadaPersistida> findByIdentificadorDaDisciplinaOrderByNumeroAsc(UUID disciplina);
}
