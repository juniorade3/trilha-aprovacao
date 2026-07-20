package br.com.trilhaaprovacao.planejamento.aplicacao;

import br.com.trilhaaprovacao.planejamento.dominio.PrioridadeDaMateriaNoPlano;
import java.util.UUID;

public record PrioridadeDeMateriaInformada(
        UUID identificadorDaMateria,
        PrioridadeDaMateriaNoPlano prioridade) {
}
