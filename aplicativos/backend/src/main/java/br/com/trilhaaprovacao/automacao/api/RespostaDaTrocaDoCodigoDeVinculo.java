package br.com.trilhaaprovacao.automacao.api;

import br.com.trilhaaprovacao.automacao.aplicacao.ResultadoDaTrocaDoCodigo;
import java.time.OffsetDateTime;
import java.util.List;

public record RespostaDaTrocaDoCodigoDeVinculo(
        RespostaDeVinculoDoTelegram vinculo,
        String token,
        String prefixo,
        List<String> escopos,
        OffsetDateTime expiraEm) {

    public static RespostaDaTrocaDoCodigoDeVinculo de(ResultadoDaTrocaDoCodigo troca) {
        return new RespostaDaTrocaDoCodigoDeVinculo(
                RespostaDeVinculoDoTelegram.de(troca.vinculo()),
                troca.credencial().token(), troca.credencial().credencial().prefixo(),
                List.of(troca.credencial().credencial().escopos().split(" ")),
                troca.credencial().credencial().expiraEm());
    }
}
