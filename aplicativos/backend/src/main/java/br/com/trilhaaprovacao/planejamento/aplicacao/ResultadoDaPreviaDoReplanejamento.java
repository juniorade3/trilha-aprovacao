package br.com.trilhaaprovacao.planejamento.aplicacao;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ResultadoDaPreviaDoReplanejamento(
        UUID identificadorDoPlano,
        LocalDate dataDeReferencia,
        LocalDate dataFinal,
        String assinaturaDaPrevia,
        Resumo resumo,
        List<Capacidade> capacidadesPorDia,
        List<BlocoPreservado> blocosPreservados,
        List<Pendencia> pendencias) {

    public record Resumo(int quantidadeDePendencias, int quantidadeDeFragmentos,
            int minutosPendentes, int minutosAlocados, int minutosNaoAlocados,
            int confirmacoesExigidas) { }
    public record Capacidade(LocalDate data, int minutosDisponiveis,
            int minutosOcupados, int minutosAlocados, int minutosRestantes,
            int quantidadeDeMaterias) { }
    public record BlocoPreservado(UUID identificador, String titulo, LocalDate data,
            int duracaoEmMinutos, String estado) { }
    public record Pendencia(UUID identificadorDoBloco, UUID identificadorDaMateria,
            UUID identificadorDoTopico, String titulo, LocalDate dataOriginal,
            int ordemOriginal, int minutosPrevistos, int minutosExecutados,
            int minutosPendentes, int quantidadeDeReagendamentos, String prioridade,
            String motivo, String decisao, boolean exigeConfirmacao,
            int minutosNaoAlocados, String justificativa, List<Fragmento> fragmentos,
            List<String> sugestoesManuais) { }
    public record Fragmento(LocalDate data, int duracaoEmMinutos, int sequencia) { }
}
