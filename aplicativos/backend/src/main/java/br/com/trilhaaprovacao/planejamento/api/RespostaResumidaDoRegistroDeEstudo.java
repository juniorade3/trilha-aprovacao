package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.estudos.dominio.RegistroDeEstudo;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RespostaResumidaDoRegistroDeEstudo(
        UUID identificador,
        UUID identificadorDoTopico,
        OffsetDateTime dataHora,
        int duracaoEmMinutos) {
    static RespostaResumidaDoRegistroDeEstudo de(RegistroDeEstudo estudo) {
        if (estudo == null) return null;
        return new RespostaResumidaDoRegistroDeEstudo(estudo.identificador(),
                estudo.identificadorDoTopico(), estudo.dataHora(),
                estudo.duracaoEmMinutos());
    }
}
