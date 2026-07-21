package br.com.trilhaaprovacao.priorizacao.aplicacao;

import br.com.trilhaaprovacao.priorizacao.dominio.AcaoSugerida;
import br.com.trilhaaprovacao.priorizacao.dominio.FaixaDePriorizacao;
import br.com.trilhaaprovacao.priorizacao.dominio.GrupoDePriorizacao;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ResultadoDaPriorizacaoDeTopicos(
        Contexto contexto,
        Resumo resumo,
        List<ItemSemMapeamento> itensSemMapeamento,
        List<MateriaPriorizada> materias) {

    public record Contexto(
            ReferenciaOficial concurso,
            ReferenciaOficial cargo,
            ReferenciaOficial edital,
            LocalDate dataReferencia,
            LocalDate inicioJanelaRecente) {
    }

    public record ReferenciaOficial(UUID identificador, String nome) {
    }

    public record Resumo(
            long itensOficiais,
            long itensSemMapeamento,
            long topicosExigidos,
            long lacunas,
            long fraquezas,
            long consolidados) {
    }

    public record ItemSemMapeamento(
            UUID identificador,
            String descricao,
            UUID identificadorDaMateria,
            String nomeDaMateria,
            int ordem) {
    }

    public record MateriaPriorizada(
            UUID identificador,
            String nome,
            List<TopicoPriorizado> topicos) {
    }

    public record TopicoPriorizado(
            UUID identificador,
            String nome,
            GrupoDePriorizacao grupo,
            FaixaDePriorizacao faixa,
            int posicaoNoGrupo,
            AcaoSugerida acaoSugerida,
            boolean possuiMaterial,
            long quantidadeDeItensOficiais,
            Indicadores indicadores,
            List<String> justificativas) {
    }

    public record Indicadores(
            long estudos,
            long evidencias,
            long questoesRecentes,
            long acertosRecentes,
            long errosRecentes,
            BigDecimal percentual,
            Integer ultimaRecordacao,
            Integer ultimaDificuldade,
            OffsetDateTime ultimaEvidencia,
            long quantidadeDePadroesRepetidos,
            LocalDate ultimaOcorrenciaDePadraoRepetido) {
    }
}
