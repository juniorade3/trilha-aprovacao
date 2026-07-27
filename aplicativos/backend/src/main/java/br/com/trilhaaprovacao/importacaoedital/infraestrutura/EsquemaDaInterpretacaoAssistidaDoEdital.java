package br.com.trilhaaprovacao.importacaoedital.infraestrutura;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EsquemaDaInterpretacaoAssistidaDoEdital {
    private EsquemaDaInterpretacaoAssistidaDoEdital() {
    }

    static Map<String, Object> criar() {
        Map<String, Object> definicoes = new LinkedHashMap<>();
        definicoes.put("evidencia", objeto(Map.of(
                "pagina", anulavel("integer"),
                "trecho", anulavel("string"))));
        definicoes.put("dado_textual", objeto(Map.of(
                "valor", anulavel("string"),
                "evidencia", referencia("evidencia"))));
        definicoes.put("dado_inteiro", objeto(Map.of(
                "valor", anulavel("integer"),
                "evidencia", referencia("evidencia"))));
        definicoes.put("dado_decimal", objeto(Map.of(
                "valor", anulavel("number"),
                "evidencia", referencia("evidencia"))));

        definicoes.put("concurso", objeto(propriedades(
                "nome", referencia("dado_textual"),
                "descricao", referencia("dado_textual"),
                "orgao", referencia("dado_textual"),
                "banca", referencia("dado_textual"))));
        definicoes.put("edital", objeto(propriedades(
                "titulo", referencia("dado_textual"),
                "numero", referencia("dado_textual"),
                "ano", referencia("dado_inteiro"),
                "descricao", referencia("dado_textual"))));
        definicoes.put("topico", objeto(propriedades(
                "numeroOficial", referencia("dado_textual"),
                "numeroDoPai", referencia("dado_textual"),
                "nome", referencia("dado_textual"),
                "descricao", referencia("dado_textual"))));
        definicoes.put("item", objeto(propriedades(
                "numeroOficial", referencia("dado_textual"),
                "numeroDoTopico", referencia("dado_textual"),
                "descricaoLiteral", referencia("dado_textual"))));
        definicoes.put("materia", objeto(propriedades(
                "nome", referencia("dado_textual"),
                "descricao", referencia("dado_textual"),
                "peso", referencia("dado_decimal"),
                "quantidadeDeQuestoes", referencia("dado_inteiro"),
                "pontuacaoMaxima", referencia("dado_decimal"),
                "topicos", lista("topico"),
                "itens", lista("item"))));
        definicoes.put("grupo", objeto(propriedades(
                "nome", referencia("dado_textual"),
                "quantidadeDeQuestoes", referencia("dado_inteiro"),
                "pontuacaoMaxima", referencia("dado_decimal"),
                "pontuacaoMinima", referencia("dado_decimal"),
                "materias", lista("materia"))));
        definicoes.put("prova", objeto(propriedades(
                "nome", referencia("dado_textual"),
                "tipo", referencia("dado_textual"),
                "carater", referencia("dado_textual"),
                "dataHora", referencia("dado_textual"),
                "duracaoEmMinutos", referencia("dado_inteiro"),
                "quantidadeDeQuestoes", referencia("dado_inteiro"),
                "pontuacaoMaxima", referencia("dado_decimal"),
                "pontuacaoMinima", referencia("dado_decimal"),
                "grupos", lista("grupo"),
                "materiasSemGrupo", lista("materia"))));
        definicoes.put("cargo", objeto(propriedades(
                "nome", referencia("dado_textual"),
                "area", referencia("dado_textual"),
                "especialidade", referencia("dado_textual"),
                "nivelDeEscolaridade", referencia("dado_textual"),
                "provas", lista("prova"))));

        Map<String, Object> raiz = objeto(Map.of(
                "concurso", referencia("concurso"),
                "edital", referencia("edital"),
                "cargo", referencia("cargo")));
        raiz.put("$defs", definicoes);
        return raiz;
    }

    private static Map<String, Object> objeto(
            Map<String, Object> propriedades) {
        Map<String, Object> esquema = new LinkedHashMap<>();
        esquema.put("type", "object");
        esquema.put("additionalProperties", false);
        esquema.put("properties", propriedades);
        esquema.put("required", new ArrayList<>(propriedades.keySet()));
        return esquema;
    }

    private static Map<String, Object> anulavel(String tipo) {
        return Map.of("type", List.of(tipo, "null"));
    }

    private static Map<String, Object> referencia(String nome) {
        return Map.of("$ref", "#/$defs/" + nome);
    }

    private static Map<String, Object> lista(String nomeDosItens) {
        return Map.of(
                "type", "array",
                "items", referencia(nomeDosItens));
    }

    private static Map<String, Object> propriedades(Object... pares) {
        if (pares.length % 2 != 0) {
            throw new IllegalArgumentException("Pares de propriedades invalidos.");
        }
        Map<String, Object> propriedades = new LinkedHashMap<>();
        for (int indice = 0; indice < pares.length; indice += 2) {
            propriedades.put((String) pares[indice], pares[indice + 1]);
        }
        return propriedades;
    }
}
