package br.com.trilhaaprovacao.estudos.api;

import br.com.trilhaaprovacao.estudos.dominio.RegistroDeEstudo;
import br.com.trilhaaprovacao.estudos.dominio.SituacaoDoRegistroDeEstudo;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaDeRegistroDeEstudo(
        UUID identificador,
        UUID identificadorDoTopico,
        String nomeDoTopico,
        UUID identificadorDoMaterial,
        String tituloDoMaterial,
        UUID identificadorDoRegistroDeOrigem,
        OffsetDateTime dataHora,
        int duracaoEmMinutos,
        String observacao,
        SituacaoDoRegistroDeEstudo situacao,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    static RespostaDeRegistroDeEstudo de(
            RegistroDeEstudo registro, String nomeDoTopico, String tituloDoMaterial) {
        return new RespostaDeRegistroDeEstudo(registro.identificador(),
                registro.identificadorDoTopico(), nomeDoTopico,
                registro.identificadorDoMaterial(), tituloDoMaterial,
                registro.identificadorDoRegistroDeOrigem(), registro.dataHora(),
                registro.duracaoEmMinutos(), registro.observacao(), registro.situacao(),
                registro.criadoEm(), registro.atualizadoEm(), registro.versao());
    }
}
