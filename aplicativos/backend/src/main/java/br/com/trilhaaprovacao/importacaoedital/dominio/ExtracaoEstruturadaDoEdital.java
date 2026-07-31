package br.com.trilhaaprovacao.importacaoedital.dominio;

import br.com.trilhaaprovacao.concursos.dominio.CaraterDaProva;
import br.com.trilhaaprovacao.concursos.dominio.NivelDeEscolaridade;
import br.com.trilhaaprovacao.concursos.dominio.TipoDeProva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ExtracaoEstruturadaDoEdital(
        String versaoDoContrato,
        FonteDoEdital fonte,
        ConcursoExtraido concurso,
        EditalExtraido edital,
        List<CargoExtraido> cargos,
        List<ProvaExtraida> provas,
        List<MateriaExtraida> materias,
        List<String> avisos,
        List<String> incertezas) {

    public ExtracaoEstruturadaDoEdital {
        if (!"1".equals(versaoDoContrato) || fonte == null) {
            throw new IllegalArgumentException("Contrato da extracao invalido.");
        }
        cargos = copiar(cargos);
        provas = copiar(provas);
        materias = copiar(materias);
        avisos = copiar(avisos);
        incertezas = copiar(incertezas);
    }

    private static <T> List<T> copiar(List<T> valores) {
        return valores == null ? List.of() : List.copyOf(valores);
    }

    public record FonteDoEdital(
            String nomeDoArquivo,
            String sha256,
            int paginas) {
    }

    public record ConcursoExtraido(
            ValorExtraido<String> nome,
            ValorExtraido<String> descricao,
            ValorExtraido<String> orgao,
            ValorExtraido<String> banca,
            ValorExtraido<LocalDate> dataPrevista) {
    }

    public record EditalExtraido(
            ValorExtraido<String> titulo,
            ValorExtraido<String> numero,
            ValorExtraido<Integer> ano,
            ValorExtraido<String> descricao,
            ValorExtraido<LocalDate> dataDePublicacao) {
    }

    public record CargoExtraido(
            String chave,
            ValorExtraido<String> nome,
            ValorExtraido<String> area,
            ValorExtraido<String> especialidade,
            ValorExtraido<NivelDeEscolaridade> nivelDeEscolaridade,
            int ordem) {
    }

    public record ProvaExtraida(
            String chave,
            String chaveDoCargo,
            ValorExtraido<String> nome,
            ValorExtraido<TipoDeProva> tipo,
            ValorExtraido<CaraterDaProva> carater,
            int ordem,
            ValorExtraido<OffsetDateTime> dataHora,
            ValorExtraido<Integer> duracaoEmMinutos,
            ValorExtraido<Integer> quantidadeDeQuestoes,
            ValorExtraido<BigDecimal> pontuacaoMaxima,
            ValorExtraido<BigDecimal> pontuacaoMinima,
            List<GrupoExtraido> grupos) {

        public ProvaExtraida {
            grupos = copiar(grupos);
        }
    }

    public record GrupoExtraido(
            String chave,
            ValorExtraido<String> nome,
            int ordem,
            ValorExtraido<Integer> quantidadeDeQuestoes,
            ValorExtraido<BigDecimal> pontuacaoMaxima,
            ValorExtraido<BigDecimal> pontuacaoMinima) {
    }

    public record MateriaExtraida(
            String chave,
            String chaveDoCargo,
            String chaveDaProva,
            String chaveDoGrupo,
            ValorExtraido<String> nome,
            ValorExtraido<String> descricao,
            int ordem,
            ValorExtraido<BigDecimal> peso,
            ValorExtraido<Integer> quantidadeDeQuestoes,
            ValorExtraido<BigDecimal> pontuacaoMaxima,
            List<TopicoExtraido> topicos,
            List<ItemExtraido> itensDoEdital) {

        public MateriaExtraida {
            topicos = copiar(topicos);
            itensDoEdital = copiar(itensDoEdital);
        }
    }

    public record TopicoExtraido(
            String chave,
            String chaveDoPai,
            ValorExtraido<String> numeroOficial,
            ValorExtraido<String> nome,
            ValorExtraido<String> descricao,
            int ordem) {
    }

    public record ItemExtraido(
            String chave,
            String chaveDoPai,
            ValorExtraido<String> numeroOficial,
            ValorExtraido<String> descricaoLiteral,
            String nomeNormalizado,
            int ordem,
            String chaveDoTopicoSugerido) {
    }
}
