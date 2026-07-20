package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.planejamento.aplicacao.MateriaParaGeracao;
import br.com.trilhaaprovacao.planejamento.dominio.PrioridadeDaMateriaNoPlano;
import java.util.UUID;

public record RespostaDeMateriaParaGeracao(
        UUID identificadorDaMateria,
        String nome,
        int ordemEstavel,
        PrioridadeDaMateriaNoPlano prioridade) {

    static RespostaDeMateriaParaGeracao de(MateriaParaGeracao materia) {
        return new RespostaDeMateriaParaGeracao(materia.identificadorDaMateria(),
                materia.nome(), materia.ordemEstavel(), materia.prioridade());
    }
}
