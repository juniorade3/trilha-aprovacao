package br.com.trilhaaprovacao.automacao.api;

import br.com.trilhaaprovacao.automacao.dominio.EstadoDaOperacaoAssistida;
import br.com.trilhaaprovacao.automacao.dominio.OperacaoAssistida;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaResumidaDeOperacaoAssistida(
        UUID identificador,
        String tipo,
        EstadoDaOperacaoAssistida estado,
        String resumo,
        OffsetDateTime expiraEm,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm) {

    public static RespostaResumidaDeOperacaoAssistida de(OperacaoAssistida operacao) {
        return new RespostaResumidaDeOperacaoAssistida(operacao.identificador(),
                operacao.tipo(), operacao.estado(), operacao.resumo(),
                operacao.expiraEm(), operacao.criadoEm(), operacao.atualizadoEm());
    }
}
