package br.com.trilhaaprovacao.trilhas.aplicacao;

import java.util.UUID;

public record ResumoDaTrilhaPublicada(
        UUID identificador,
        String codigo,
        String nome,
        String versaoPublicada,
        String descricao,
        int quantidadeDeDisciplinas,
        int quantidadeDeTarefas,
        int quantidadeDeTarefasConcluidas,
        boolean aderida) {
}
