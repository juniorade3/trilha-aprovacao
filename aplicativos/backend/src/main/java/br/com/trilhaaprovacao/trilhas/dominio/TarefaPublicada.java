package br.com.trilhaaprovacao.trilhas.dominio;

import br.com.trilhaaprovacao.planejamento.dominio.TipoDeAtividade;
import java.util.UUID;

public record TarefaPublicada(UUID identificador, UUID identificadorDaDisciplina,
        int numero, String titulo, String aula, TipoDeAtividade tipoDeAtividade,
        String enderecoDoMaterial, String orientacao) {
}
