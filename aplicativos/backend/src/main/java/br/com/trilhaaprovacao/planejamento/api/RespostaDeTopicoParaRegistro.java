package br.com.trilhaaprovacao.planejamento.api;
import br.com.trilhaaprovacao.conteudos.dominio.TopicoDaMateria;
import java.util.UUID;
public record RespostaDeTopicoParaRegistro(UUID identificador, String nome) {
    static RespostaDeTopicoParaRegistro de(TopicoDaMateria topico) {
        return new RespostaDeTopicoParaRegistro(topico.identificador(), topico.nome());
    }
}
