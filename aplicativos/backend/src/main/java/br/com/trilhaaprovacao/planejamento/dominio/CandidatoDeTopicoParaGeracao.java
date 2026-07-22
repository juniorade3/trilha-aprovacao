package br.com.trilhaaprovacao.planejamento.dominio;

import br.com.trilhaaprovacao.priorizacao.dominio.FaixaDePriorizacao;
import br.com.trilhaaprovacao.priorizacao.dominio.GrupoDePriorizacao;
import java.util.Objects;
import java.util.UUID;

/** Topico oficial ja classificado e ordenado pelo ranking consultivo. */
public record CandidatoDeTopicoParaGeracao(
        UUID identificadorDaMateria,
        UUID identificadorDoTopico,
        String nomeDaMateria,
        String nomeDoTopico,
        String nomeNormalizado,
        int ordemOficial,
        int posicaoNoGrupo,
        GrupoDePriorizacao grupoDaPriorizacao,
        FaixaDePriorizacao faixaDaPriorizacao,
        boolean jaFoiEstudado) {

    public CandidatoDeTopicoParaGeracao {
        Objects.requireNonNull(identificadorDaMateria);
        Objects.requireNonNull(identificadorDoTopico);
        Objects.requireNonNull(nomeDaMateria);
        Objects.requireNonNull(nomeDoTopico);
        Objects.requireNonNull(nomeNormalizado);
        Objects.requireNonNull(grupoDaPriorizacao);
        Objects.requireNonNull(faixaDaPriorizacao);
        if (ordemOficial < 1 || posicaoNoGrupo < 1) {
            throw new IllegalArgumentException("Ordem do topico invalida.");
        }
        if (faixaDaPriorizacao.grupo() != grupoDaPriorizacao) {
            throw new IllegalArgumentException("Faixa e grupo do topico sao incompativeis.");
        }
    }
}
