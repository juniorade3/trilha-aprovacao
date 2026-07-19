package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.planejamento.aplicacao.ResumoDoDiaPlanejado;
import java.time.LocalDate;

public record RespostaDeResumoDoDiaPlanejado(
        LocalDate data,
        int minutosDisponiveis,
        int minutosPlanejados,
        int saldoEmMinutos,
        boolean possuiExcesso) {

    public static RespostaDeResumoDoDiaPlanejado de(ResumoDoDiaPlanejado resumo) {
        return new RespostaDeResumoDoDiaPlanejado(resumo.data(), resumo.minutosDisponiveis(),
                resumo.minutosPlanejados(), resumo.saldoEmMinutos(), resumo.possuiExcesso());
    }
}
