package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.planejamento.dominio.BlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.EstadoDoBlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.TipoDeAtividade;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDeBlocoDeEstudo(
        UUID identificador,
        UUID identificadorDoPlano,
        UUID identificadorDaMateria,
        UUID identificadorDoTopico,
        String titulo,
        TipoDeAtividade tipoDeAtividade,
        LocalDate data,
        int duracaoPrevistaEmMinutos,
        int ordem,
        LocalTime horarioPrevisto,
        String observacao,
        EstadoDoBlocoDeEstudo estado,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public static RespostaDeBlocoDeEstudo de(BlocoDeEstudo bloco) {
        return new RespostaDeBlocoDeEstudo(bloco.identificador(),
                bloco.identificadorDoPlano(), bloco.identificadorDaMateria(),
                bloco.identificadorDoTopico(), bloco.titulo(),
                bloco.tipoDeAtividade(), bloco.data(),
                bloco.duracaoPrevistaEmMinutos(), bloco.ordem(),
                bloco.horarioPrevisto(), bloco.observacao(), bloco.estado(),
                bloco.criadoEm(), bloco.atualizadoEm(), bloco.versao());
    }
}
