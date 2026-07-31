package br.com.trilhaaprovacao.automacao.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeAuditoriaMcp;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeCadastroAssistidoDeConcursos;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeConsultasMcp;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeImportacaoCompletaDoEditalMcp;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeOperacoesCriticasMcp;
import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDePreparacoesMcp;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CatalogoDeFerramentasMcpSchemaTest {
    @Test
    void deveExporNaEvidenciaOsMesmosLimitesDoDominio() {
        CatalogoDeFerramentasMcp catalogo = new CatalogoDeFerramentasMcp(
                mock(ServicoDeConsultasMcp.class),
                mock(ServicoDePreparacoesMcp.class),
                mock(ServicoDeCadastroAssistidoDeConcursos.class),
                mock(ServicoDeOperacoesCriticasMcp.class),
                mock(ServicoDeImportacaoCompletaDoEditalMcp.class),
                mock(ServicoDeAuditoriaMcp.class),
                mock(ObjectMapper.class));

        McpSchema.Tool ferramenta = catalogo.ferramentas().stream()
                .map(especificacao -> especificacao.tool())
                .filter(item -> item.name().equals("preparar_registro_de_estudo"))
                .findFirst()
                .orElseThrow();
        Map<String, Object> propriedades = mapa(
                ferramenta.inputSchema().get("properties"));
        Map<String, Object> evidencia = mapa(propriedades.get("evidencia"));
        Map<String, Object> propriedadesDaEvidencia =
                mapa(evidencia.get("properties"));

        assertThat(mapa(propriedadesDaEvidencia.get("quantidadeDeQuestoes")))
                .containsEntry("minimum", 1);

        Map<String, Object> padroes =
                mapa(propriedadesDaEvidencia.get("padroesDeErro"));
        Map<String, Object> padrao = mapa(padroes.get("items"));
        Map<String, Object> propriedadesDoPadrao =
                mapa(padrao.get("properties"));
        assertThat(mapa(propriedadesDoPadrao.get("descricao")))
                .containsEntry("maxLength", 200);
    }

    private Map<String, Object> mapa(Object valor) {
        assertThat(valor).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultado = (Map<String, Object>) valor;
        return resultado;
    }
}
