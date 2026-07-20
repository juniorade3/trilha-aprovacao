package br.com.trilhaaprovacao.planejamento.api;

import br.com.trilhaaprovacao.planejamento.dominio.BlocoPreservadoNaGeracao;
import br.com.trilhaaprovacao.planejamento.dominio.BlocoSugerido;
import br.com.trilhaaprovacao.planejamento.dominio.CapacidadeDoDia;
import br.com.trilhaaprovacao.planejamento.dominio.DiaDaPreviaDaGeracao;
import br.com.trilhaaprovacao.planejamento.dominio.JustificativaDaGeracao;
import br.com.trilhaaprovacao.planejamento.dominio.PreviaDaGeracaoDaSemana;
import br.com.trilhaaprovacao.planejamento.dominio.TipoDeAtividade;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RespostaDaPreviaDaGeracao(
        UUID identificadorDoPlano,
        List<Dia> dias,
        List<Justificativa> avisos,
        boolean aplicada) {

    public static RespostaDaPreviaDaGeracao de(PreviaDaGeracaoDaSemana previa) {
        return new RespostaDaPreviaDaGeracao(previa.identificadorDoPlano(),
                previa.dias().stream().map(Dia::de).toList(),
                previa.avisos().stream().map(Justificativa::de).toList(), false);
    }

    public record Dia(
            LocalDate data,
            Capacidade capacidade,
            List<BlocoPreservado> blocosPreservados,
            List<Sugestao> blocosSugeridos,
            List<Justificativa> avisos) {
        static Dia de(DiaDaPreviaDaGeracao dia) {
            return new Dia(dia.data(), Capacidade.de(dia.capacidade()),
                    dia.blocosPreservados().stream().map(BlocoPreservado::de).toList(),
                    dia.blocosSugeridos().stream().map(Sugestao::de).toList(),
                    dia.avisos().stream().map(Justificativa::de).toList());
        }
    }

    public record Capacidade(int minutosDisponiveis, int minutosPreservados,
            int minutosSugeridos, int minutosLivres) {
        static Capacidade de(CapacidadeDoDia capacidade) {
            return new Capacidade(capacidade.minutosDisponiveis(),
                    capacidade.minutosPreservados(), capacidade.minutosSugeridos(),
                    capacidade.minutosLivres());
        }
    }

    public record BlocoPreservado(UUID identificador, UUID identificadorDaMateria,
            String nomeDaMateria, String titulo, TipoDeAtividade tipoDeAtividade,
            int duracaoEmMinutos, int ordem) {
        static BlocoPreservado de(BlocoPreservadoNaGeracao bloco) {
            return new BlocoPreservado(bloco.identificador(), bloco.identificadorDaMateria(),
                    bloco.nomeDaMateria(), bloco.titulo(), bloco.tipoDeAtividade(),
                    bloco.duracaoEmMinutos(), bloco.ordem());
        }
    }

    public record Sugestao(UUID identificadorDaMateria, String nomeDaMateria,
            String titulo, TipoDeAtividade tipoDeAtividade, int duracaoEmMinutos,
            List<Justificativa> justificativas) {
        static Sugestao de(BlocoSugerido bloco) {
            return new Sugestao(bloco.identificadorDaMateria(), bloco.nomeDaMateria(),
                    bloco.titulo(), bloco.tipoDeAtividade(), bloco.duracaoEmMinutos(),
                    bloco.justificativas().stream().map(Justificativa::de).toList());
        }
    }

    public record Justificativa(String codigo, String mensagem) {
        static Justificativa de(JustificativaDaGeracao justificativa) {
            return new Justificativa(justificativa.codigo(), justificativa.mensagem());
        }
    }
}
