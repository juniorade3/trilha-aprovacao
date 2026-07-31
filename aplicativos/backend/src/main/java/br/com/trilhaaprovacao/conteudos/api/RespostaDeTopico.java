package br.com.trilhaaprovacao.conteudos.api;

import br.com.trilhaaprovacao.conteudos.dominio.TopicoDaMateria;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDeTopico(
        UUID identificador,
        UUID identificadorDaMateria,
        UUID identificadorDoTopicoPai,
        String numeroOficial,
        String nome,
        String descricao,
        int ordem,
        boolean arquivado,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    static RespostaDeTopico de(TopicoDaMateria topico) {
        return new RespostaDeTopico(topico.identificador(), topico.identificadorDaMateria(),
                topico.identificadorDoTopicoPai(), topico.numeroOficial(),
                topico.nome(), topico.descricao(), topico.ordem(),
                topico.arquivado(), topico.criadoEm(), topico.atualizadoEm(), topico.versao());
    }
}
