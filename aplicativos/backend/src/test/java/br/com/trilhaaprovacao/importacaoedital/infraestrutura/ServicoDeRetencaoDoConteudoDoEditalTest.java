package br.com.trilhaaprovacao.importacaoedital.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.trilhaaprovacao.importacaoedital.dominio.ImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.TipoDaFonteDoEdital;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class ServicoDeRetencaoDoConteudoDoEditalTest {
    @Test
    void descartaBrutoETextoDepoisDaRetencao() {
        OffsetDateTime agora = OffsetDateTime.now();
        byte[] bruto = {1, 2, 3};
        var dominio = ImportacaoDeEdital.receber(UUID.randomUUID(),
                TipoDaFonteDoEdital.TEXTO, "edital.txt", "text/plain",
                "b".repeat(64), bruto.length, agora.minusDays(2));
        var persistida = new ImportacaoDeEditalPersistida(dominio, bruto,
                agora.minusDays(1));
        persistida.registrarConteudoExtraido(TipoDaFonteDoEdital.TEXTO, 1,
                "conteudo");
        var repositorio = mock(RepositorioDeImportacoesDeEdital.class);
        when(repositorio.encontrarConteudosExpirados(
                ArgumentMatchers.eq(agora), ArgumentMatchers.any()))
                .thenReturn(List.of(persistida));

        int quantidade = new ServicoDeRetencaoDoConteudoDoEdital(repositorio)
                .executar(agora, 10);

        assertThat(quantidade).isOne();
        assertThat(persistida.conteudoOriginal()).isNull();
        assertThat(persistida.textoExtraido()).isNull();
    }
}
