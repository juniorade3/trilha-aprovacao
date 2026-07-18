package br.com.trilhaaprovacao.concursos.dominio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record MateriaDaProva(
        UUID identificador,
        UUID identificadorDoGrupoDeConteudo,
        UUID identificadorDaMateria,
        int ordem,
        BigDecimal peso,
        Integer quantidadeDeQuestoes,
        BigDecimal pontuacaoMaxima,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public MateriaDaProva {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(identificadorDoGrupoDeConteudo);
        Objects.requireNonNull(identificadorDaMateria);
        ValidacaoDoConcurso.ordemPositiva(ordem);
        ValidacaoDoConcurso.decimalPositivo(peso, "Peso");
        ValidacaoDoConcurso.inteiroPositivo(quantidadeDeQuestoes, "Quantidade de questoes");
        ValidacaoDoConcurso.decimalPositivo(pontuacaoMaxima, "Pontuacao maxima");
        Objects.requireNonNull(criadoEm);
        Objects.requireNonNull(atualizadoEm);
    }

    public static MateriaDaProva criar(UUID grupo, UUID materia, int ordem,
            BigDecimal peso, Integer questoes, BigDecimal pontuacaoMaxima) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new MateriaDaProva(UUID.randomUUID(), grupo, materia, ordem, peso,
                questoes, pontuacaoMaxima, agora, agora, 0);
    }

    public MateriaDaProva alterar(int novaOrdem, BigDecimal novoPeso,
            Integer novasQuestoes, BigDecimal novaPontuacaoMaxima) {
        return new MateriaDaProva(identificador, identificadorDoGrupoDeConteudo,
                identificadorDaMateria, novaOrdem, novoPeso, novasQuestoes,
                novaPontuacaoMaxima, criadoEm, OffsetDateTime.now(), versao);
    }
}
