package br.com.trilhaaprovacao.planejamento.aplicacao;

import br.com.trilhaaprovacao.planejamento.dominio.BlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.EstadoDoBlocoDeEstudo;
import br.com.trilhaaprovacao.planejamento.dominio.PlanoSemanal;
import br.com.trilhaaprovacao.planejamento.infraestrutura.BlocoDeEstudoPersistido;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDeBlocosDeEstudo;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDeDisponibilidadesDoDia;
import br.com.trilhaaprovacao.planejamento.infraestrutura.RepositorioDePlanosSemanais;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaDoPlanejamentoDeHoje {
    private final RepositorioDePlanosSemanais planos;
    private final RepositorioDeDisponibilidadesDoDia disponibilidades;
    private final RepositorioDeBlocosDeEstudo blocos;

    public ConsultaDoPlanejamentoDeHoje(RepositorioDePlanosSemanais planos,
            RepositorioDeDisponibilidadesDoDia disponibilidades,
            RepositorioDeBlocosDeEstudo blocos) {
        this.planos = planos;
        this.disponibilidades = disponibilidades;
        this.blocos = blocos;
    }

    @Transactional(readOnly = true)
    public ResultadoDoPlanejamentoDeHoje consultar(UUID usuario, LocalDate data) {
        LocalDate inicio = data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var encontrado = planos.findByIdentificadorDoUsuarioAndDataInicial(usuario, inicio);
        if (encontrado.isEmpty()) return vazio(EstadoDoPlanejamentoDeHoje.SEM_PLANO, data);

        PlanoSemanal plano = encontrado.get().paraDominio();
        if (!plano.estaAtivo()) {
            EstadoDoPlanejamentoDeHoje estado = switch (plano.estado()) {
                case RASCUNHO -> EstadoDoPlanejamentoDeHoje.PLANO_EM_RASCUNHO;
                case ENCERRADO -> EstadoDoPlanejamentoDeHoje.PLANO_ENCERRADO;
                case CANCELADO -> EstadoDoPlanejamentoDeHoje.PLANO_CANCELADO;
                case ATIVO -> throw new IllegalStateException("Plano ativo esperado.");
            };
            return new ResultadoDoPlanejamentoDeHoje(
                    estado, data,
                    plano.identificador(), plano.dataInicial(), 0, 0, null,
                    List.of(), List.of(), List.of());
        }

        int disponivel = disponibilidades
                .findByIdentificadorDoPlanoAndData(plano.identificador(), data)
                .map(item -> item.paraDominio().minutosDisponiveis()).orElse(0);
        List<BlocoDeEstudo> blocosDoDia = blocos
                .findByIdentificadorDoPlanoAndDataOrderByOrdemAsc(plano.identificador(), data)
                .stream().map(BlocoDeEstudoPersistido::paraDominio).toList();
        List<BlocoDeEstudo> atrasados = blocos
                .findByIdentificadorDoPlanoAndDataBeforeOrderByDataAscOrdemAsc(
                        plano.identificador(), data)
                .stream().map(BlocoDeEstudoPersistido::paraDominio)
                .filter(item -> item.estado() == EstadoDoBlocoDeEstudo.PLANEJADO).toList();
        List<BlocoDeEstudo> planejados = blocosDoDia.stream()
                .filter(item -> item.estado() == EstadoDoBlocoDeEstudo.PLANEJADO).toList();
        List<BlocoDeEstudo> realizados = blocosDoDia.stream()
                .filter(item -> item.estado() == EstadoDoBlocoDeEstudo.CONCLUIDO
                        || item.estado() == EstadoDoBlocoDeEstudo.PARCIALMENTE_CONCLUIDO)
                .toList();
        int minutosPlanejados = blocosDoDia.stream()
                .filter(item -> item.estado() != EstadoDoBlocoDeEstudo.CANCELADO)
                .mapToInt(BlocoDeEstudo::duracaoPrevistaEmMinutos).sum();
        if (planejados.isEmpty() && realizados.isEmpty()) {
            return new ResultadoDoPlanejamentoDeHoje(
                    EstadoDoPlanejamentoDeHoje.DIA_SEM_BLOCOS, data,
                    plano.identificador(), plano.dataInicial(), disponivel,
                    minutosPlanejados, null, List.of(), atrasados, List.of());
        }
        BlocoDeEstudo proximo = planejados.isEmpty() ? null : planejados.getFirst();
        List<BlocoDeEstudo> sequencia = planejados.size() < 2
                ? List.of() : planejados.subList(1, planejados.size());
        return new ResultadoDoPlanejamentoDeHoje(
                EstadoDoPlanejamentoDeHoje.DIA_PLANEJADO, data,
                plano.identificador(), plano.dataInicial(), disponivel,
                minutosPlanejados, proximo, sequencia, atrasados, realizados);
    }

    private ResultadoDoPlanejamentoDeHoje vazio(
            EstadoDoPlanejamentoDeHoje estado, LocalDate data) {
        return new ResultadoDoPlanejamentoDeHoje(
                estado, data, null, null, 0, 0, null,
                List.of(), List.of(), List.of());
    }
}
