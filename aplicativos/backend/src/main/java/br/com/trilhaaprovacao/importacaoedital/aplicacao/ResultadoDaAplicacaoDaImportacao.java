package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import java.util.Map;
import java.util.UUID;

public record ResultadoDaAplicacaoDaImportacao(
        UUID identificadorDaImportacao,
        UUID identificadorDoConcurso,
        UUID identificadorDoEdital,
        UUID identificadorDoCargo,
        String situacaoDoConcurso,
        int provasCriadas,
        int gruposCriados,
        int materiasCriadas,
        int materiasReutilizadas,
        int topicosCriados,
        int topicosReutilizados,
        int itensCriados,
        int sugestoesDeMapeamentoPendentes,
        Map<String, UUID> identificadoresPorChave) {

    public ResultadoDaAplicacaoDaImportacao {
        identificadoresPorChave = Map.copyOf(identificadoresPorChave);
    }

    public Map<String, Object> reciboCompacto(UUID relatorio) {
        return Map.ofEntries(
                Map.entry("identificadorDaImportacao",
                        identificadorDaImportacao),
                Map.entry("identificadorDoConcurso",
                        identificadorDoConcurso),
                Map.entry("identificadorDoEdital", identificadorDoEdital),
                Map.entry("identificadorDoCargo", identificadorDoCargo),
                Map.entry("identificadorDoRelatorio", relatorio),
                Map.entry("provasCriadas", provasCriadas),
                Map.entry("gruposCriados", gruposCriados),
                Map.entry("materiasCriadas", materiasCriadas),
                Map.entry("materiasReutilizadas", materiasReutilizadas),
                Map.entry("topicosCriados", topicosCriados),
                Map.entry("topicosReutilizados", topicosReutilizados),
                Map.entry("itensCriados", itensCriados),
                Map.entry("sugestoesDeMapeamentoPendentes",
                        sugestoesDeMapeamentoPendentes),
                Map.entry("situacaoDoConcurso", situacaoDoConcurso),
                Map.entry("enderecoDeRevisao",
                        "/concursos/" + identificadorDoConcurso
                                + "?foco=conteudo"));
    }
}
