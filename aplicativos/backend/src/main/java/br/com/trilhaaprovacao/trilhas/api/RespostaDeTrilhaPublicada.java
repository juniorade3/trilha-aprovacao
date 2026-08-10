package br.com.trilhaaprovacao.trilhas.api;

import br.com.trilhaaprovacao.trilhas.aplicacao.ResumoDaTrilhaPublicada;
import java.util.UUID;

public record RespostaDeTrilhaPublicada(
        UUID identificador,
        String codigo,
        String nome,
        String versaoPublicada,
        String descricao,
        int quantidadeDeDisciplinas,
        int quantidadeDeTarefas,
        int quantidadeDeTarefasConcluidas,
        boolean aderida) {
    static RespostaDeTrilhaPublicada de(ResumoDaTrilhaPublicada resumo) {
        return new RespostaDeTrilhaPublicada(resumo.identificador(), resumo.codigo(), resumo.nome(),
                resumo.versaoPublicada(), resumo.descricao(), resumo.quantidadeDeDisciplinas(),
                resumo.quantidadeDeTarefas(), resumo.quantidadeDeTarefasConcluidas(), resumo.aderida());
    }
}
