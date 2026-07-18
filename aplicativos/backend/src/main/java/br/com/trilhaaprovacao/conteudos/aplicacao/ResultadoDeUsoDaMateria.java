package br.com.trilhaaprovacao.conteudos.aplicacao;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ResultadoDeUsoDaMateria(
        List<MaterialRelacionado> materiais,
        List<EstudoRecente> estudosRecentes,
        List<ConcursoRelacionado> concursos) {

    public record MaterialRelacionado(UUID identificador, String titulo, String tipo) {
    }

    public record EstudoRecente(
            UUID identificador,
            String nomeDoTopico,
            OffsetDateTime dataHora,
            int duracaoEmMinutos) {
    }

    public record ConcursoRelacionado(UUID identificador, String nome, boolean ativo) {
    }
}
