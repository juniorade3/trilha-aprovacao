package br.com.trilhaaprovacao.compartilhado.api;

import java.util.List;

public record RespostaDeErro(String codigo, String mensagem, String identificadorDeCorrelacao, List<String> detalhes) {
}
