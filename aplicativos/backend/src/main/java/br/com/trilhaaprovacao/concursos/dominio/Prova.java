package br.com.trilhaaprovacao.concursos.dominio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record Prova(
        UUID identificador,
        UUID identificadorDoCargo,
        String nome,
        String nomeNormalizado,
        TipoDeProva tipo,
        CaraterDaProva carater,
        int ordem,
        OffsetDateTime dataHoraPrevista,
        Integer duracaoEmMinutos,
        Integer quantidadeDeQuestoes,
        BigDecimal pontuacaoMaxima,
        BigDecimal pontuacaoMinima,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public Prova {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(identificadorDoCargo);
        nome = ValidacaoDoConcurso.textoObrigatorio(nome, "Nome");
        nomeNormalizado = ValidacaoDoConcurso.chave(nome);
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(carater);
        ValidacaoDoConcurso.ordemPositiva(ordem);
        ValidacaoDoConcurso.inteiroPositivo(duracaoEmMinutos, "Duracao");
        ValidacaoDoConcurso.inteiroPositivo(quantidadeDeQuestoes, "Quantidade de questoes");
        ValidacaoDoConcurso.pontuacaoCoerente(pontuacaoMinima, pontuacaoMaxima);
        Objects.requireNonNull(criadoEm);
        Objects.requireNonNull(atualizadoEm);
    }

    public static Prova criar(UUID cargo, String nome, TipoDeProva tipo, CaraterDaProva carater,
            int ordem, OffsetDateTime dataHoraPrevista, Integer duracao, Integer questoes,
            BigDecimal maxima, BigDecimal minima) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new Prova(UUID.randomUUID(), cargo, nome, null, tipo, carater, ordem,
                dataHoraPrevista, duracao, questoes, maxima, minima, agora, agora, 0);
    }

    public Prova alterar(String novoNome, TipoDeProva novoTipo, CaraterDaProva novoCarater,
            int novaOrdem, OffsetDateTime novaData, Integer novaDuracao,
            Integer novasQuestoes, BigDecimal novaMaxima, BigDecimal novaMinima) {
        return new Prova(identificador, identificadorDoCargo, novoNome, null, novoTipo,
                novoCarater, novaOrdem, novaData, novaDuracao, novasQuestoes,
                novaMaxima, novaMinima, criadoEm, OffsetDateTime.now(), versao);
    }
}
