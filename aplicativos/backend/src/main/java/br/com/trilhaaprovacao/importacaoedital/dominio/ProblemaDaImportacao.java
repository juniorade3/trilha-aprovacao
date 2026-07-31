package br.com.trilhaaprovacao.importacaoedital.dominio;

public record ProblemaDaImportacao(
        SeveridadeDoProblemaDaImportacao severidade,
        String codigo,
        String mensagem,
        String caminho,
        String tipoDoRecurso,
        String chaveDoRecurso,
        String campo) {

    public ProblemaDaImportacao(SeveridadeDoProblemaDaImportacao severidade,
            String codigo, String mensagem, String caminho) {
        this(severidade, codigo, mensagem, caminho,
                tipoGenericoDoRecurso(caminho),
                chaveGenericaDoRecurso(caminho),
                campoGenerico(caminho));
    }

    public ProblemaDaImportacao {
        if (severidade == null || codigo == null || codigo.isBlank()
                || mensagem == null || mensagem.isBlank()) {
            throw new IllegalArgumentException("Problema da importacao invalido.");
        }
        codigo = codigo.strip();
        mensagem = mensagem.strip();
        caminho = caminho == null || caminho.isBlank() ? null : caminho.strip();
        tipoDoRecurso = normalizar(tipoDoRecurso);
        chaveDoRecurso = normalizar(chaveDoRecurso);
        campo = normalizar(campo);
    }

    public ProblemaDaImportacao comReferencia(String novoTipoDoRecurso,
            String novaChaveDoRecurso, String novoCampo) {
        return new ProblemaDaImportacao(severidade, codigo, mensagem, caminho,
                novoTipoDoRecurso, novaChaveDoRecurso, novoCampo);
    }

    private static String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static String tipoGenericoDoRecurso(String caminho) {
        if (caminho == null || caminho.isBlank()) return "extracao";
        String raiz = caminho.strip().split("[.\\[]", 2)[0];
        return switch (raiz) {
            case "fonte" -> "fonte";
            case "concurso" -> "concurso";
            case "edital" -> "edital";
            case "cargos" -> "cargo";
            case "provas" -> "prova";
            case "materias" -> "materia";
            default -> "extracao";
        };
    }

    private static String chaveGenericaDoRecurso(String caminho) {
        if (caminho == null || caminho.isBlank()) return "extracao";
        String raiz = caminho.strip().split("[.\\[]", 2)[0];
        return switch (raiz) {
            case "fonte", "concurso", "edital" -> raiz;
            default -> null;
        };
    }

    private static String campoGenerico(String caminho) {
        if (caminho == null || caminho.isBlank()) return null;
        int separador = caminho.indexOf('.');
        return separador < 0 ? caminho : caminho.substring(separador + 1);
    }
}
