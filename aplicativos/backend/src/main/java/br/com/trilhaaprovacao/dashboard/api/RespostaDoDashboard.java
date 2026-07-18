package br.com.trilhaaprovacao.dashboard.api;

import br.com.trilhaaprovacao.dashboard.aplicacao.ResultadoDoDashboard;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RespostaDoDashboard(
        ConcursoAtivo concursoAtivo,
        LocalDate dataDaProximaProva,
        Long diasAteAProva,
        int tempoEstudadoNaSemanaEmMinutos,
        int quantidadeDeMaterias,
        int quantidadeDeTopicosExigidos,
        int quantidadeDeTopicosComEstudo,
        int quantidadeDeItensMapeados,
        int quantidadeDeItensSemMapeamento,
        List<Atividade> atividadeRecente,
        List<Alerta> alertas) {

    static RespostaDoDashboard de(ResultadoDoDashboard resultado) {
        ConcursoAtivo concurso = resultado.concursoAtivo() == null
                ? null
                : new ConcursoAtivo(resultado.concursoAtivo().identificador(),
                        resultado.concursoAtivo().nome(),
                        resultado.concursoAtivo().orgao(),
                        resultado.concursoAtivo().banca(),
                        resultado.concursoAtivo().situacao(),
                        resultado.concursoAtivo().identificadorDoCargoSelecionado(),
                        resultado.concursoAtivo().nomeDoCargoSelecionado());
        List<Atividade> atividades = resultado.atividadeRecente().stream()
                .map(item -> new Atividade(item.identificador(),
                        item.identificadorDoTopico(), item.nomeDoTopico(),
                        item.tituloDoMaterial(), item.dataHora(),
                        item.duracaoEmMinutos()))
                .toList();
        List<Alerta> alertas = resultado.alertas().stream()
                .map(item -> new Alerta(item.codigo(), item.titulo(),
                        item.mensagem(), item.nivel()))
                .toList();
        return new RespostaDoDashboard(concurso, resultado.dataDaProximaProva(),
                resultado.diasAteAProva(),
                resultado.tempoEstudadoNaSemanaEmMinutos(),
                resultado.quantidadeDeMaterias(),
                resultado.quantidadeDeTopicosExigidos(),
                resultado.quantidadeDeTopicosComEstudo(),
                resultado.quantidadeDeItensMapeados(),
                resultado.quantidadeDeItensSemMapeamento(),
                atividades, alertas);
    }

    public record ConcursoAtivo(
            UUID identificador,
            String nome,
            String orgao,
            String banca,
            String situacao,
            UUID identificadorDoCargoSelecionado,
            String nomeDoCargoSelecionado) {
    }

    public record Atividade(
            UUID identificador,
            UUID identificadorDoTopico,
            String nomeDoTopico,
            String tituloDoMaterial,
            OffsetDateTime dataHora,
            int duracaoEmMinutos) {
    }

    public record Alerta(
            String codigo,
            String titulo,
            String mensagem,
            String nivel) {
    }
}
