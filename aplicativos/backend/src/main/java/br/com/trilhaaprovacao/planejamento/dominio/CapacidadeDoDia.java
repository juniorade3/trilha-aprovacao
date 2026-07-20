package br.com.trilhaaprovacao.planejamento.dominio;

public record CapacidadeDoDia(
        int minutosDisponiveis,
        int minutosPreservados,
        int minutosSugeridos,
        int minutosLivres) {

    public CapacidadeDoDia {
        if (minutosDisponiveis < 0 || minutosPreservados < 0
                || minutosSugeridos < 0 || minutosLivres < 0) {
            throw new IllegalArgumentException("Capacidade invalida.");
        }
    }
}
