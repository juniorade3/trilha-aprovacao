package br.com.trilhaaprovacao.concursos.api;

import br.com.trilhaaprovacao.concursos.dominio.CaraterDaProva;
import br.com.trilhaaprovacao.concursos.dominio.Prova;
import br.com.trilhaaprovacao.concursos.dominio.TipoDeProva;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDeProva(
        UUID identificador,
        UUID identificadorDoCargo,
        String nome,
        TipoDeProva tipo,
        CaraterDaProva carater,
        int ordem,
        OffsetDateTime dataHoraPrevista,
        Integer duracaoEmMinutos,
        Integer quantidadeDeQuestoes,
        BigDecimal pontuacaoMaxima,
        BigDecimal pontuacaoMinima,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    static RespostaDeProva de(Prova prova) {
        return new RespostaDeProva(prova.identificador(), prova.identificadorDoCargo(),
                prova.nome(), prova.tipo(), prova.carater(), prova.ordem(),
                prova.dataHoraPrevista(), prova.duracaoEmMinutos(),
                prova.quantidadeDeQuestoes(), prova.pontuacaoMaxima(),
                prova.pontuacaoMinima(), prova.criadoEm(), prova.atualizadoEm(), prova.versao());
    }
}
