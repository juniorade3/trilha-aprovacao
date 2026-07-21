package br.com.trilhaaprovacao.evidencias.infraestrutura;

import br.com.trilhaaprovacao.evidencias.dominio.DadosDoPadraoDeErro;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDeOcorrenciasDePadroes
        extends JpaRepository<OcorrenciaDePadraoDeErroPersistida, UUID> {
    @Query("""
            select new br.com.trilhaaprovacao.evidencias.dominio.DadosDoPadraoDeErro(
                p.descricao, o.quantidadeDeOcorrencias)
            from OcorrenciaDePadraoDeErroPersistida o, PadraoDeErroPersistido p
            where o.identificadorDoPadrao = p.identificador
              and o.identificadorDaEvidencia = :evidencia
            order by p.descricao
            """)
    List<DadosDoPadraoDeErro> listarDaEvidencia(@Param("evidencia") UUID evidencia);
}
