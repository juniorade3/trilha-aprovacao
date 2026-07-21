package br.com.trilhaaprovacao.priorizacao.api;

import br.com.trilhaaprovacao.priorizacao.aplicacao.ResultadoDaPriorizacaoDeTopicos;
import br.com.trilhaaprovacao.priorizacao.dominio.AcaoSugerida;
import br.com.trilhaaprovacao.priorizacao.dominio.FaixaDePriorizacao;
import br.com.trilhaaprovacao.priorizacao.dominio.GrupoDePriorizacao;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Ranking consultivo e deterministico dos topicos exigidos no contexto oficial.")
public record RespostaDePriorizacaoDeTopicos(
        Contexto contexto,
        Resumo resumo,
        List<ItemSemMapeamento> itensSemMapeamento,
        List<MateriaPriorizada> materias) {

    public static RespostaDePriorizacaoDeTopicos de(
            ResultadoDaPriorizacaoDeTopicos resultado) {
        return new RespostaDePriorizacaoDeTopicos(
                new Contexto(referencia(resultado.contexto().concurso()),
                        referencia(resultado.contexto().cargo()),
                        referencia(resultado.contexto().edital()),
                        resultado.contexto().dataReferencia(),
                        resultado.contexto().inicioJanelaRecente()),
                new Resumo(resultado.resumo().itensOficiais(),
                        resultado.resumo().itensSemMapeamento(),
                        resultado.resumo().topicosExigidos(),
                        resultado.resumo().lacunas(), resultado.resumo().fraquezas(),
                        resultado.resumo().consolidados()),
                resultado.itensSemMapeamento().stream()
                        .map(item -> new ItemSemMapeamento(item.identificador(),
                                item.descricao(), item.identificadorDaMateria(),
                                item.nomeDaMateria(), item.ordem()))
                        .toList(),
                resultado.materias().stream().map(RespostaDePriorizacaoDeTopicos::materia)
                        .toList());
    }

    private static ReferenciaOficial referencia(
            ResultadoDaPriorizacaoDeTopicos.ReferenciaOficial referencia) {
        return new ReferenciaOficial(referencia.identificador(), referencia.nome());
    }

    private static MateriaPriorizada materia(
            ResultadoDaPriorizacaoDeTopicos.MateriaPriorizada materia) {
        return new MateriaPriorizada(materia.identificador(), materia.nome(),
                materia.topicos().stream().map(RespostaDePriorizacaoDeTopicos::topico)
                        .toList());
    }

    private static TopicoPriorizado topico(
            ResultadoDaPriorizacaoDeTopicos.TopicoPriorizado topico) {
        var indicadores = topico.indicadores();
        return new TopicoPriorizado(topico.identificador(), topico.nome(),
                topico.grupo(), topico.faixa(), topico.posicaoNoGrupo(),
                topico.acaoSugerida(), topico.possuiMaterial(),
                topico.quantidadeDeItensOficiais(),
                new Indicadores(indicadores.estudos(), indicadores.evidencias(),
                        indicadores.questoesRecentes(), indicadores.acertosRecentes(),
                        indicadores.errosRecentes(), indicadores.percentual(),
                        indicadores.ultimaRecordacao(), indicadores.ultimaDificuldade(),
                        indicadores.ultimaEvidencia(),
                        indicadores.quantidadeDePadroesRepetidos(),
                        indicadores.ultimaOcorrenciaDePadraoRepetido()),
                topico.justificativas());
    }

    @Schema(name = "ContextoDaPriorizacao")
    public record Contexto(
            ReferenciaOficial concurso,
            ReferenciaOficial cargo,
            ReferenciaOficial edital,
            LocalDate dataReferencia,
            LocalDate inicioJanelaRecente) {
    }

    @Schema(name = "ReferenciaOficialDaPriorizacao")
    public record ReferenciaOficial(UUID identificador, String nome) {
    }

    @Schema(name = "ResumoDaPriorizacao")
    public record Resumo(
            long itensOficiais,
            long itensSemMapeamento,
            long topicosExigidos,
            long lacunas,
            long fraquezas,
            long consolidados) {
    }

    @Schema(name = "ItemSemMapeamentoDaPriorizacao")
    public record ItemSemMapeamento(
            UUID id,
            String descricao,
            UUID idMateria,
            String nomeMateria,
            int ordem) {
    }

    @Schema(name = "MateriaPriorizadaDaPriorizacao")
    public record MateriaPriorizada(
            UUID id,
            String nome,
            List<TopicoPriorizado> topicos) {
    }

    @Schema(name = "TopicoPriorizadoDaPriorizacao")
    public record TopicoPriorizado(
            UUID id,
            String nome,
            GrupoDePriorizacao grupo,
            FaixaDePriorizacao faixa,
            int posicaoNoGrupo,
            AcaoSugerida acaoSugerida,
            boolean possuiMaterial,
            long quantidadeItensOficiais,
            Indicadores indicadores,
            List<String> justificativas) {
    }

    @Schema(name = "IndicadoresDaPriorizacao")
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
            long quantidadePadroesRepetidos,
            LocalDate ultimaOcorrenciaPadraoRepetido) {
    }
}
