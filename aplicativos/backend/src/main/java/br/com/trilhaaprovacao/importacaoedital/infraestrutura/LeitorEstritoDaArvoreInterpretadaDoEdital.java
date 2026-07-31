package br.com.trilhaaprovacao.importacaoedital.infraestrutura;

import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class LeitorEstritoDaArvoreInterpretadaDoEdital {
    private LeitorEstritoDaArvoreInterpretadaDoEdital() {
    }

    static ArvoreInterpretadaDoEdital ler(ObjectMapper json, String conteudo)
            throws JacksonException {
        JsonNode raiz;
        try (JsonParser analisador = json.createParser(conteudo)) {
            raiz = json.readTree(analisador);
            if (raiz == null || analisador.nextToken() != null) {
                throw new IllegalArgumentException(
                        "A resposta deve conter um unico objeto JSON.");
            }
        }
        Map<String, Object> esquema =
                EsquemaDaInterpretacaoAssistidaDoEdital.criar();
        validar(raiz, esquema, esquema, "$");
        ArvoreInterpretadaDoEdital arvore = json.treeToValue(
                raiz, ArvoreInterpretadaDoEdital.class);
        if (arvore.cargo().nome() == null
                || arvore.cargo().nome().valor() == null
                || arvore.cargo().nome().valor().isBlank()) {
            throw new IllegalArgumentException(
                    "A interpretacao nao localizou o cargo alvo.");
        }
        return arvore;
    }

    @SuppressWarnings("unchecked")
    private static void validar(JsonNode valor, Map<String, Object> esquema,
            Map<String, Object> raiz, String caminho) {
        Object referencia = esquema.get("$ref");
        if (referencia instanceof String nome) {
            String prefixo = "#/$defs/";
            if (!nome.startsWith(prefixo)) {
                throw invalido(caminho);
            }
            Object resolvido = ((Map<String, Object>) raiz.get("$defs"))
                    .get(nome.substring(prefixo.length()));
            if (!(resolvido instanceof Map<?, ?>)) {
                throw invalido(caminho);
            }
            validar(valor, (Map<String, Object>) resolvido, raiz, caminho);
            return;
        }

        Object tipo = esquema.get("type");
        if (valor.isNull()) {
            if (tipo instanceof List<?> tipos && tipos.contains("null")) {
                return;
            }
            throw invalido(caminho);
        }
        String tipoEfetivo = tipo instanceof List<?> tipos
                ? tipos.stream().map(Object::toString)
                        .filter(item -> !"null".equals(item))
                        .findFirst().orElse("")
                : String.valueOf(tipo);
        switch (tipoEfetivo) {
            case "object" -> validarObjeto(valor, esquema, raiz, caminho);
            case "array" -> validarLista(valor, esquema, raiz, caminho);
            case "string" -> exigir(valor.isTextual(), caminho);
            case "integer" -> exigir(valor.isIntegralNumber(), caminho);
            case "number" -> exigir(valor.isNumber(), caminho);
            default -> throw invalido(caminho);
        }
    }

    @SuppressWarnings("unchecked")
    private static void validarObjeto(JsonNode valor,
            Map<String, Object> esquema, Map<String, Object> raiz,
            String caminho) {
        exigir(valor.isObject(), caminho);
        Map<String, Object> propriedades = (Map<String, Object>)
                esquema.getOrDefault("properties", Map.of());
        List<String> obrigatorias = (List<String>)
                esquema.getOrDefault("required", List.of());
        valor.propertyNames().forEach(nome -> {
            if (!propriedades.containsKey(nome)) {
                throw invalido(caminho + "." + nome);
            }
        });
        for (String nome : obrigatorias) {
            if (!valor.has(nome)) {
                throw invalido(caminho + "." + nome);
            }
        }
        for (Map.Entry<String, Object> propriedade
                : propriedades.entrySet()) {
            if (!valor.has(propriedade.getKey())) continue;
            validar(valor.get(propriedade.getKey()),
                    (Map<String, Object>) propriedade.getValue(), raiz,
                    caminho + "." + propriedade.getKey());
        }
    }

    @SuppressWarnings("unchecked")
    private static void validarLista(JsonNode valor,
            Map<String, Object> esquema, Map<String, Object> raiz,
            String caminho) {
        exigir(valor.isArray(), caminho);
        Map<String, Object> itens = (Map<String, Object>) esquema.get("items");
        for (int indice = 0; indice < valor.size(); indice++) {
            validar(valor.get(indice), itens, raiz,
                    caminho + "[" + indice + "]");
        }
    }

    private static void exigir(boolean condicao, String caminho) {
        if (!condicao) throw invalido(caminho);
    }

    private static IllegalArgumentException invalido(String caminho) {
        return new IllegalArgumentException(
                "Resposta estruturada invalida em " + caminho + ".");
    }
}
