package br.com.trilhaaprovacao.evidencias.aplicacao;

import br.com.trilhaaprovacao.evidencias.dominio.ResultadoDaRevisao;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DiagnosticoDeTopico(
        UUID identificadorDoTopico,
        String nomeDoTopico,
        UUID identificadorDaMateria,
        String nomeDaMateria,
        boolean exigidoNoConcursoAtivo,
        long quantidadeDeEvidencias,
        TotaisDeQuestoes totaisHistoricos,
        TotaisDeQuestoes totaisDosUltimosTrintaDias,
        BigDecimal percentualRecenteDeAcertos,
        Integer ultimaRecordacao,
        BigDecimal mediaRecenteDeRecordacao,
        Integer ultimaDificuldade,
        BigDecimal mediaRecenteDeDificuldade,
        ResultadoDaRevisao resultadoDaUltimaRevisao,
        OffsetDateTime ultimaEvidenciaEm,
        List<PadraoRepetido> padroesDeErroRepetidos) {

    public record TotaisDeQuestoes(long questoes, long acertos, long erros) {
    }

    public record PadraoRepetido(
            UUID identificador, String descricao,
            long quantidadeDeEvidencias, long quantidadeDeOcorrencias) {
    }
}
