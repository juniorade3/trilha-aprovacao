package br.com.trilhaaprovacao.planejamento.dominio;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Regras compartilhadas de capacidade usadas pela geracao e pelo replanejamento. */
public final class DistribuidorDeterministicoDeCapacidade {
    public static final int DURACAO_MINIMA = 25;
    public static final int LIMITE_DE_MATERIAS = 3;

    private DistribuidorDeterministicoDeCapacidade() {
    }

    public static final class Dia {
        private final LocalDate data;
        private final int capacidade;
        private int minutosOcupados;
        private final Set<UUID> materias;

        public Dia(LocalDate data, int capacidade, int minutosOcupados,
                Set<UUID> materias) {
            this.data = data;
            this.capacidade = capacidade;
            this.minutosOcupados = Math.max(0, minutosOcupados);
            this.materias = new LinkedHashSet<>(materias);
        }

        public LocalDate data() { return data; }
        public int capacidade() { return capacidade; }
        public int minutosOcupados() { return minutosOcupados; }
        public int minutosLivres() { return Math.max(0, capacidade - minutosOcupados); }
        public Set<UUID> materias() { return Set.copyOf(materias); }

        public boolean comporta(UUID materia, int minutos) {
            return comporta(materia, minutos, true);
        }

        public boolean comporta(UUID materia, int minutos,
                boolean contaParaLimiteDeMaterias) {
            return minutos > 0 && minutos <= minutosLivres()
                    && (!contaParaLimiteDeMaterias || materia == null
                    || materias.contains(materia)
                    || materias.size() < LIMITE_DE_MATERIAS);
        }

        public void alocar(UUID materia, int minutos) {
            alocar(materia, minutos, true);
        }

        public void alocar(UUID materia, int minutos,
                boolean contaParaLimiteDeMaterias) {
            if (!comporta(materia, minutos, contaParaLimiteDeMaterias)) {
                throw new IllegalArgumentException("A alocacao excede a capacidade do dia.");
            }
            minutosOcupados += minutos;
            if (contaParaLimiteDeMaterias && materia != null) materias.add(materia);
        }
    }
}
