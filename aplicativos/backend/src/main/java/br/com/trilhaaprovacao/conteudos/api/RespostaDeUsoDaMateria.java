package br.com.trilhaaprovacao.conteudos.api;

import br.com.trilhaaprovacao.conteudos.aplicacao.ResultadoDeUsoDaMateria;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RespostaDeUsoDaMateria(
        List<Material> materiais,
        List<Estudo> estudosRecentes,
        List<Concurso> concursos) {

    static RespostaDeUsoDaMateria de(ResultadoDeUsoDaMateria resultado) {
        return new RespostaDeUsoDaMateria(
                resultado.materiais().stream()
                        .map(item -> new Material(
                                item.identificador(), item.titulo(), item.tipo()))
                        .toList(),
                resultado.estudosRecentes().stream()
                        .map(item -> new Estudo(
                                item.identificador(), item.nomeDoTopico(),
                                item.dataHora(), item.duracaoEmMinutos()))
                        .toList(),
                resultado.concursos().stream()
                        .map(item -> new Concurso(
                                item.identificador(), item.nome(), item.ativo()))
                        .toList());
    }

    public record Material(UUID identificador, String titulo, String tipo) {
    }

    public record Estudo(
            UUID identificador,
            String nomeDoTopico,
            OffsetDateTime dataHora,
            int duracaoEmMinutos) {
    }

    public record Concurso(UUID identificador, String nome, boolean ativo) {
    }
}
