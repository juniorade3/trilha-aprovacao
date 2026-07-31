package br.com.trilhaaprovacao.importacaoedital.aplicacao;

/**
 * Referência estável de um campo cuja sugestão assistida foi conferida
 * explicitamente pelo usuário.
 */
public record ConfirmacaoDeCampoDaExtracao(
        String tipoDoRecurso,
        String chaveDoRecurso,
        String campo) {

    public ConfirmacaoDeCampoDaExtracao {
        tipoDoRecurso = normalizar(tipoDoRecurso, 40);
        chaveDoRecurso = normalizar(chaveDoRecurso, 160);
        campo = normalizar(campo, 80);
    }

    private static String normalizar(String valor, int limite) {
        if (valor == null || valor.isBlank() || valor.strip().length() > limite) {
            throw new IllegalArgumentException(
                    "Referencia de confirmacao de campo invalida.");
        }
        return valor.strip();
    }
}
