package br.com.trilhaaprovacao.automacao.aplicacao;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ResultadoDaConsultaMcp(
        String versaoDoContrato,
        UUID identificadorDaCorrelacao,
        OffsetDateTime geradoEm,
        Map<String, Object> dados,
        List<Aviso> avisos) {

    public ResultadoDaConsultaMcp {
        dados = Collections.unmodifiableMap(new LinkedHashMap<>(dados));
        avisos = List.copyOf(avisos);
    }

    public record Aviso(String codigo, String mensagem) {
    }
}
