package br.com.trilhaaprovacao.concursos.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EstruturaDeConcursosTest {
    @Test
    void deveArquivarDesativarEBloquearAlteracoesDoConcurso() {
        Concurso concurso = Concurso.criar(UUID.randomUUID(), "Receita Federal", null,
                null, null, SituacaoDoConcurso.PLANEJADO, LocalDate.now())
                .definirAtivacao(true)
                .definirArquivamento(true);

        assertEquals(SituacaoDoConcurso.ARQUIVADO, concurso.situacao());
        assertFalse(concurso.ativo());
        assertThrows(IllegalStateException.class,
                () -> concurso.alterar("Outro", null, null, null,
                        SituacaoDoConcurso.PLANEJADO, null));
    }

    @Test
    void deveValidarUrlEValoresDaEstrutura() {
        assertThrows(IllegalArgumentException.class,
                () -> Edital.criar(UUID.randomUUID(), "Edital", null, null,
                        null, null, "arquivo-local"));
        assertThrows(IllegalArgumentException.class,
                () -> Prova.criar(UUID.randomUUID(), "Objetiva", TipoDeProva.OBJETIVA,
                        CaraterDaProva.ELIMINATORIO, 1, null, 240, 100,
                        new BigDecimal("90"), new BigDecimal("100")));
        assertThrows(IllegalArgumentException.class,
                () -> GrupoDeConteudo.criar(UUID.randomUUID(), "Basicos", 0,
                        null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> MateriaDaProva.criar(UUID.randomUUID(), UUID.randomUUID(),
                        1, BigDecimal.ZERO, null, null));
    }

    @Test
    void deveNormalizarNomesParaAsRegrasDeDuplicidade() {
        CargoDoConcurso cargo = CargoDoConcurso.criar(UUID.randomUUID(),
                "  Auditor Fiscal  ", null, null, NivelDeEscolaridade.SUPERIOR, 1);
        Prova prova = Prova.criar(UUID.randomUUID(), "  Prova Objetiva  ",
                TipoDeProva.OBJETIVA, CaraterDaProva.CLASSIFICATORIO,
                1, null, null, null, null, null);

        assertEquals("Auditor Fiscal", cargo.nome());
        assertEquals("auditor fiscal", cargo.nomeNormalizado());
        assertEquals("prova objetiva", prova.nomeNormalizado());
    }
}
