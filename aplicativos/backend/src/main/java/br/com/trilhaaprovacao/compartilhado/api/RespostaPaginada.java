package br.com.trilhaaprovacao.compartilhado.api;

import java.util.List;

public record RespostaPaginada<T>(
        List<T> itens,
        int pagina,
        int tamanho,
        long totalDeItens,
        int totalDePaginas) {
}
