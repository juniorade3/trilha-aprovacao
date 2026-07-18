package br.com.trilhaaprovacao.conteudos.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MateriaETopicoTest {
    @Test
    void deveNormalizarDadosDaMateriaSemPerderONomeDeExibicao() {
        Materia materia = Materia.criar(UUID.randomUUID(),
                "  Direito Constitucional  ", "  Direitos e garantias  ", "#0e8f87");

        assertThat(materia.nome()).isEqualTo("Direito Constitucional");
        assertThat(materia.nomeNormalizado()).isEqualTo("direito constitucional");
        assertThat(materia.descricao()).isEqualTo("Direitos e garantias");
        assertThat(materia.cor()).isEqualTo("#0E8F87");
    }

    @Test
    void deveImpedirAlteracaoDeMateriaArquivada() {
        Materia materia = Materia.criar(UUID.randomUUID(), "Direito", null, null);
        materia.definirArquivamento(true);

        assertThatThrownBy(() -> materia.alterar("Outro nome", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("arquivada");
    }

    @Test
    void deveValidarPaiEOrdemDoTopico() {
        UUID materia = UUID.randomUUID();
        TopicoDaMateria topico = TopicoDaMateria.criar(materia, null, "Direitos", null, 1);

        assertThatThrownBy(() -> topico.alterar("Direitos", null, topico.identificador(), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pai de si");
        assertThatThrownBy(() -> TopicoDaMateria.criar(materia, null, "Direitos", null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positiva");
    }
}
