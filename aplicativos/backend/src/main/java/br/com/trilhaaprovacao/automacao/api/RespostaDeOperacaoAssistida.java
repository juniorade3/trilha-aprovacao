package br.com.trilhaaprovacao.automacao.api;

import br.com.trilhaaprovacao.automacao.dominio.EstadoDaOperacaoAssistida;
import br.com.trilhaaprovacao.automacao.dominio.OperacaoAssistida;
import java.time.OffsetDateTime;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public record RespostaDeOperacaoAssistida(
        UUID identificador,
        String tipo,
        EstadoDaOperacaoAssistida estado,
        String resumo,
        JsonNode proposta,
        String assinatura,
        JsonNode versoesConsultadas,
        OffsetDateTime expiraEm,
        OffsetDateTime confirmadaEm,
        OffsetDateTime aplicadaEm,
        OffsetDateTime canceladaEm,
        String falha,
        JsonNode resultado,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm) {

    public static RespostaDeOperacaoAssistida de(
            OperacaoAssistida operacao, ObjectMapper mapeador) {
        return new RespostaDeOperacaoAssistida(operacao.identificador(),
                operacao.tipo(), operacao.estado(), operacao.resumo(),
                ler(mapeador, operacao.propostaCanonica()), operacao.assinatura(),
                ler(mapeador, operacao.versoesConsultadas()), operacao.expiraEm(),
                operacao.confirmadaEm(), operacao.aplicadaEm(),
                operacao.canceladaEm(), operacao.falha(),
                ler(mapeador, operacao.resultado()), operacao.criadoEm(),
                operacao.atualizadoEm());
    }

    private static JsonNode ler(ObjectMapper mapeador, String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapeador.readTree(json);
        } catch (Exception excecao) {
            throw new IllegalStateException("JSON persistido invalido.", excecao);
        }
    }
}
