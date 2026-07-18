package br.com.trilhaaprovacao.dashboard.aplicacao;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ResultadoDoDashboard(
        ResumoDoConcursoAtivo concursoAtivo,
        LocalDate dataDaProximaProva,
        Long diasAteAProva,
        int tempoEstudadoNaSemanaEmMinutos,
        int quantidadeDeMaterias,
        int quantidadeDeTopicosExigidos,
        int quantidadeDeTopicosComEstudo,
        int quantidadeDeItensMapeados,
        int quantidadeDeItensSemMapeamento,
        List<AtividadeRecente> atividadeRecente,
        List<AlertaDoDashboard> alertas) {

    public record ResumoDoConcursoAtivo(
            UUID identificador,
            String nome,
            String orgao,
            String banca,
            String situacao,
            UUID identificadorDoCargoSelecionado,
            String nomeDoCargoSelecionado) {
    }

    public record AtividadeRecente(
            UUID identificador,
            UUID identificadorDoTopico,
            String nomeDoTopico,
            String tituloDoMaterial,
            OffsetDateTime dataHora,
            int duracaoEmMinutos) {
    }

    public record AlertaDoDashboard(
            String codigo,
            String titulo,
            String mensagem,
            String nivel) {
    }
}
