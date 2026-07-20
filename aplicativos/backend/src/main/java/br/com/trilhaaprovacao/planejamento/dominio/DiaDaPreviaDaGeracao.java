package br.com.trilhaaprovacao.planejamento.dominio;

import java.time.LocalDate;
import java.util.List;

public record DiaDaPreviaDaGeracao(
        LocalDate data,
        CapacidadeDoDia capacidade,
        List<BlocoPreservadoNaGeracao> blocosPreservados,
        List<BlocoSugerido> blocosSugeridos,
        List<JustificativaDaGeracao> avisos) {

    public DiaDaPreviaDaGeracao {
        blocosPreservados = List.copyOf(blocosPreservados);
        blocosSugeridos = List.copyOf(blocosSugeridos);
        avisos = List.copyOf(avisos);
    }
}
