package br.com.trilhaaprovacao.planejamento.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReplanejadorComRevisaoEspecificaTest {
    private static final UUID PLANO = uuid(1);
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 7, 20);
    private static final LocalDate QUARTA = SEGUNDA.plusDays(2);
    private static final OffsetDateTime AGORA =
            OffsetDateTime.parse("2026-07-22T10:00:00-03:00");

    @Test
    void revisaoComMateriaConsomeCapacidadeSemContarComoQuartaMateria() {
        UUID materiaUm = uuid(101);
        UUID materiaDois = uuid(102);
        UUID materiaTres = uuid(103);
        UUID materiaDaRevisao = uuid(104);
        BlocoDeEstudo principalUm = futuro(10, materiaUm, TipoDeAtividade.TEORIA, 25);
        BlocoDeEstudo principalDois = futuro(11, materiaDois, TipoDeAtividade.TEORIA, 25);
        BlocoDeEstudo principalTres = futuro(12, materiaTres, TipoDeAtividade.TEORIA, 25);
        BlocoDeEstudo revisaoPendente = passado(13, materiaDaRevisao,
                TipoDeAtividade.REVISAO, 20);
        DisponibilidadeDoDia capacidade = new DisponibilidadeDoDia(uuid(900), PLANO,
                QUARTA, 95, AGORA, AGORA, 0);

        var previa = new ReplanejadorDeterministicoDePlano().replanejar(QUARTA,
                List.of(capacidade),
                List.of(principalUm, principalDois, principalTres, revisaoPendente),
                Map.of(), Map.of(), Set.of(), Set.of());

        assertThat(previa.propostas()).singleElement().satisfies(proposta -> {
            assertThat(proposta.decisao())
                    .isEqualTo(ReplanejadorDeterministicoDePlano.Decisao.ADIAR);
            assertThat(proposta.fragmentos()).singleElement()
                    .extracting(ReplanejadorDeterministicoDePlano.Fragmento::minutos)
                    .isEqualTo(20);
        });
        assertThat(previa.capacidades()).singleElement().satisfies(dia -> {
            assertThat(dia.minutosOcupados()).isEqualTo(95);
            assertThat(dia.materias()).containsExactlyInAnyOrder(
                    materiaUm, materiaDois, materiaTres);
        });
    }

    private BlocoDeEstudo futuro(int id, UUID materia, TipoDeAtividade tipo,
            int minutos) {
        return bloco(id, materia, tipo, QUARTA, minutos);
    }

    private BlocoDeEstudo passado(int id, UUID materia, TipoDeAtividade tipo,
            int minutos) {
        return bloco(id, materia, tipo, SEGUNDA, minutos);
    }

    private BlocoDeEstudo bloco(int id, UUID materia, TipoDeAtividade tipo,
            LocalDate data, int minutos) {
        return new BlocoDeEstudo(uuid(id), PLANO, materia, uuid(500 + id),
                "Bloco " + id, tipo, data, minutos, 1, null, null,
                OrigemDoBlocoDeEstudo.MANUAL, null, null,
                EstadoDoBlocoDeEstudo.PLANEJADO, 0, null, AGORA, AGORA, 0);
    }

    private static UUID uuid(int fim) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(fim));
    }
}
