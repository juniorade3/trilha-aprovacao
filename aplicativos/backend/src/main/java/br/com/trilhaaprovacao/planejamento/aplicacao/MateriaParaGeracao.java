package br.com.trilhaaprovacao.planejamento.aplicacao;

import br.com.trilhaaprovacao.planejamento.dominio.PrioridadeDaMateriaNoPlano;
import java.util.UUID;

public record MateriaParaGeracao(
        UUID identificadorDaMateria,
        String nome,
        int ordemEstavel,
        PrioridadeDaMateriaNoPlano prioridade) {
}
