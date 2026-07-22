package br.com.trilhaaprovacao.automacao.api;

import java.util.List;

public record RespostaPaginadaDeOperacoesAssistidas(
        List<RespostaResumidaDeOperacaoAssistida> itens,
        int pagina,
        int tamanho,
        long totalDeItens,
        int totalDePaginas) {
}
