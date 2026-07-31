package br.com.trilhaaprovacao.importacaoedital.dominio;

public record ProvenienciaDoDado(
        Integer pagina,
        String secao,
        String trecho) {

    public ProvenienciaDoDado {
        if (pagina != null && pagina < 1) {
            throw new IllegalArgumentException("Pagina da proveniencia deve ser positiva.");
        }
        secao = limitar(secao, 300);
        trecho = limitar(trecho, 1_000);
    }

    private static String limitar(String valor, int limite) {
        if (valor == null || valor.isBlank()) return null;
        String normalizado = valor.strip();
        return normalizado.length() <= limite
                ? normalizado : normalizado.substring(0, limite);
    }
}
