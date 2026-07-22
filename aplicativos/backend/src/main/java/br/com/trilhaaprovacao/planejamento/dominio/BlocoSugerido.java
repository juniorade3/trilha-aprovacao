package br.com.trilhaaprovacao.planejamento.dominio;

import br.com.trilhaaprovacao.priorizacao.dominio.FaixaDePriorizacao;
import br.com.trilhaaprovacao.priorizacao.dominio.GrupoDePriorizacao;
import java.util.List;
import java.util.UUID;

public record BlocoSugerido(
        UUID identificadorDaMateria,
        UUID identificadorDoTopico,
        String nomeDaMateria,
        String nomeDoTopico,
        String titulo,
        TipoDeAtividade tipoDeAtividade,
        GrupoDePriorizacao grupoDaPriorizacao,
        FaixaDePriorizacao faixaDaPriorizacao,
        int duracaoEmMinutos,
        List<JustificativaDaGeracao> justificativas) {

    public BlocoSugerido {
        justificativas = List.copyOf(justificativas);
        if (identificadorDoTopico != null && identificadorDaMateria == null) {
            throw new IllegalArgumentException("Topico sugerido exige uma materia.");
        }
        if (duracaoEmMinutos < 1) throw new IllegalArgumentException("Duracao invalida.");
    }
}
