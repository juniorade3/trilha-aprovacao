package br.com.trilhaaprovacao.planejamento.aplicacao;

import br.com.trilhaaprovacao.planejamento.dominio.TipoDeAtividade;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record DadosDoBlocoDeEstudo(
        UUID identificadorDaMateria,
        UUID identificadorDoTopico,
        String titulo,
        TipoDeAtividade tipoDeAtividade,
        LocalDate data,
        int duracaoPrevistaEmMinutos,
        int ordem,
        LocalTime horarioPrevisto,
        String observacao) {
}
