package br.com.trilhaaprovacao.concursos.dominio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record GrupoDeConteudo(
        UUID identificador,
        UUID identificadorDaProva,
        String nome,
        String nomeNormalizado,
        int ordem,
        Integer quantidadeDeQuestoes,
        BigDecimal pontuacaoMaxima,
        BigDecimal pontuacaoMinima,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public GrupoDeConteudo {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(identificadorDaProva);
        nome = ValidacaoDoConcurso.textoObrigatorio(nome, "Nome");
        nomeNormalizado = ValidacaoDoConcurso.chave(nome);
        ValidacaoDoConcurso.ordemPositiva(ordem);
        ValidacaoDoConcurso.inteiroPositivo(quantidadeDeQuestoes, "Quantidade de questoes");
        ValidacaoDoConcurso.pontuacaoCoerente(pontuacaoMinima, pontuacaoMaxima);
        Objects.requireNonNull(criadoEm);
        Objects.requireNonNull(atualizadoEm);
    }

    public static GrupoDeConteudo criar(UUID prova, String nome, int ordem,
            Integer questoes, BigDecimal maxima, BigDecimal minima) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new GrupoDeConteudo(UUID.randomUUID(), prova, nome, null, ordem,
                questoes, maxima, minima, agora, agora, 0);
    }

    public GrupoDeConteudo alterar(String novoNome, int novaOrdem,
            Integer novasQuestoes, BigDecimal novaMaxima, BigDecimal novaMinima) {
        return new GrupoDeConteudo(identificador, identificadorDaProva, novoNome, null,
                novaOrdem, novasQuestoes, novaMaxima, novaMinima,
                criadoEm, OffsetDateTime.now(), versao);
    }
}
