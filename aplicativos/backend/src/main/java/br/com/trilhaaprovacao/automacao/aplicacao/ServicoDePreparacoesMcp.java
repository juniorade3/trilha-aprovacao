package br.com.trilhaaprovacao.automacao.aplicacao;

import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeOperacoesAssistidas.OperacaoPreparada;
import br.com.trilhaaprovacao.automacao.infraestrutura.ContextoDaChamadaMcp;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.estudos.dominio.TipoDeEstudo;
import br.com.trilhaaprovacao.evidencias.aplicacao.DadosDaEvidencia;
import br.com.trilhaaprovacao.evidencias.aplicacao.ServicoDeEvidenciasDeAprendizagem;
import br.com.trilhaaprovacao.planejamento.aplicacao.ResultadoDaPreviaDoReplanejamento;
import br.com.trilhaaprovacao.planejamento.aplicacao.ServicoDeGeracaoDeterministica;
import br.com.trilhaaprovacao.planejamento.aplicacao.ServicoDeReplanejamento;
import br.com.trilhaaprovacao.planejamento.dominio.ConfiguracaoDaGeracaoDeterministica;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoDePreparacoesMcp {
    private static final TypeReference<Map<String, Object>> MAPA =
            new TypeReference<>() { };
    private final ServicoDeOperacoesAssistidas operacoes;
    private final ServicoDeGeracaoDeterministica geracao;
    private final ServicoDeReplanejamento replanejamento;
    private final ServicoDeCadastroAssistidoDeConcursos cadastroDeConcursos;
    private final ServicoDeOperacoesCriticasMcp operacoesCriticas;
    private final ServicoDeEvidenciasDeAprendizagem evidencias;
    private final JdbcTemplate banco;
    private final ObjectMapper mapeador;

    public ServicoDePreparacoesMcp(ServicoDeOperacoesAssistidas operacoes,
            ServicoDeGeracaoDeterministica geracao,
            ServicoDeReplanejamento replanejamento,
            ServicoDeCadastroAssistidoDeConcursos cadastroDeConcursos,
            ServicoDeOperacoesCriticasMcp operacoesCriticas,
            ServicoDeEvidenciasDeAprendizagem evidencias,
            JdbcTemplate banco,
            ObjectMapper mapeador) {
        this.operacoes = operacoes;
        this.geracao = geracao;
        this.replanejamento = replanejamento;
        this.cadastroDeConcursos = cadastroDeConcursos;
        this.operacoesCriticas = operacoesCriticas;
        this.evidencias = evidencias;
        this.banco = banco;
        this.mapeador = mapeador;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ResultadoDaConsultaMcp preparar(String tipo,
            ContextoDaChamadaMcp contexto, Map<String, Object> argumentos) {
        UUID usuario = contexto.identidade().identificadorDoUsuario();
        Map<String, Object> proposta = new LinkedHashMap<>(argumentos);
        validarEvidencia(tipo, proposta);
        Map<String, Object> versoes = switch (tipo) {
            case "REGISTRO_DE_ESTUDO" -> versoesDoTopico(usuario,
                    uuid(argumentos, "identificadorDoTopico"),
                    uuidOpcional(argumentos, "identificadorDoMaterial"));
            case "CONCLUSAO_DO_BLOCO", "INTERRUPCAO_DO_BLOCO" ->
                    versoesDoBloco(usuario, uuid(argumentos,
                            "identificadorDoBloco"));
            case "CORRECAO_DO_ESTUDO" -> versoesDoEstudo(usuario,
                    uuid(argumentos, "identificadorDoEstudo"));
            case "GERACAO_DO_PLANO" -> previaDaGeracao(usuario,
                    argumentos, proposta);
            case "REPLANEJAMENTO" -> previaDoReplanejamento(usuario,
                    argumentos, proposta);
            case "ALTERACAO_DE_DISPONIBILIDADE", "ALTERACAO_DE_PRIORIDADES" ->
                    versoesDoPlano(usuario, uuid(argumentos,
                            "identificadorDoPlano"));
            case "CADASTRO_DO_CONCURSO", "CATALOGO_DE_CONTEUDOS",
                    "CONTEUDO_PROGRAMATICO", "MAPEAMENTOS_DO_EDITAL" ->
                    cadastroDeConcursos.versoesAtuais(usuario);
            case "ATIVACAO_DO_CONCURSO", "ARQUIVAMENTO_DO_CONCURSO",
                    "CANCELAMENTO_DO_CONCURSO" ->
                    operacoesCriticas.versoesAtuais(usuario,
                            uuid(argumentos, "identificadorDoConcurso"));
            default -> throw new IllegalArgumentException(
                    "Tipo de preparacao desconhecido.");
        };
        String chave = contexto.identificadorDoEventoExterno() == null
                ? contexto.identificadorDaCorrelacao().toString()
                : contexto.identificadorDoEventoExterno();
        OperacaoPreparada preparada = operacoes.prepararParaConfirmacao(
                usuario, contexto.identidade().identificadorDoVinculo(), tipo,
                resumo(tipo, proposta), json(proposta), json(versoes),
                "mcp:" + tipo + ":" + chave);
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("identificadorDaOperacao",
                preparada.operacao().identificador());
        dados.put("tipo", preparada.operacao().tipo());
        dados.put("estado", preparada.operacao().estado().name());
        dados.put("resumo", preparada.operacao().resumo());
        dados.put("proposta", proposta);
        dados.put("codigoDeConfirmacao",
                preparada.codigoDeConfirmacao());
        dados.put("fraseDeConfirmacao",
                preparada.codigoDeConfirmacao() == null ? null
                        : "/confirmar " + preparada.codigoDeConfirmacao());
        dados.put("expiraEm", preparada.operacao().expiraEm());
        dados.put("resultado", objetoJsonOpcional(
                preparada.operacao().resultado()));
        return new ResultadoDaConsultaMcp("1",
                contexto.identificadorDaCorrelacao(),
                OffsetDateTime.now(ZoneOffset.UTC), dados, List.of());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> versoesAtuais(String tipo, UUID usuario,
            Map<String, Object> proposta) {
        Map<String, Object> copia = new LinkedHashMap<>(proposta);
        copia.remove("previa");
        copia.remove("assinaturaDaPrevia");
        return switch (tipo) {
            case "REGISTRO_DE_ESTUDO" -> versoesDoTopico(usuario,
                    uuid(proposta, "identificadorDoTopico"),
                    uuidOpcional(proposta, "identificadorDoMaterial"), false);
            case "CONCLUSAO_DO_BLOCO", "INTERRUPCAO_DO_BLOCO" ->
                    versoesDoBloco(usuario, uuid(proposta,
                            "identificadorDoBloco"));
            case "CORRECAO_DO_ESTUDO" -> versoesDoEstudo(usuario,
                    uuid(proposta, "identificadorDoEstudo"));
            case "GERACAO_DO_PLANO" -> previaDaGeracao(usuario, copia,
                    new LinkedHashMap<>(copia));
            case "REPLANEJAMENTO" -> previaDoReplanejamento(usuario, copia,
                    new LinkedHashMap<>(copia));
            case "ALTERACAO_DE_DISPONIBILIDADE", "ALTERACAO_DE_PRIORIDADES" ->
                    versoesDoPlano(usuario, uuid(proposta,
                            "identificadorDoPlano"));
            case "CADASTRO_DO_CONCURSO", "CATALOGO_DE_CONTEUDOS",
                    "CONTEUDO_PROGRAMATICO", "MAPEAMENTOS_DO_EDITAL" ->
                    cadastroDeConcursos.versoesAtuais(usuario);
            case "ATIVACAO_DO_CONCURSO", "ARQUIVAMENTO_DO_CONCURSO",
                    "CANCELAMENTO_DO_CONCURSO" ->
                    operacoesCriticas.versoesAtuais(usuario,
                            uuid(proposta, "identificadorDoConcurso"));
            default -> throw new IllegalArgumentException(
                    "Tipo de preparacao desconhecido.");
        };
    }

    private Map<String, Object> previaDaGeracao(UUID usuario,
            Map<String, Object> argumentos, Map<String, Object> proposta) {
        UUID plano = uuid(argumentos, "identificadorDoPlano");
        LocalDate referencia = LocalDate.parse(texto(
                argumentos, "dataDeReferencia"));
        int duracao = inteiro(argumentos,
                "duracaoDoBlocoPrincipalEmMinutos");
        var previa = geracao.gerarPrevia(usuario, plano, referencia,
                new ConfiguracaoDaGeracaoDeterministica(duracao));
        proposta.put("assinaturaDaPrevia", previa.assinaturaDaPrevia());
        proposta.put("previa", mapeador.convertValue(
                previa.previa(), MAPA));
        return Map.of("plano", plano, "assinaturaDaPrevia",
                previa.assinaturaDaPrevia());
    }

    private Map<String, Object> previaDoReplanejamento(UUID usuario,
            Map<String, Object> argumentos, Map<String, Object> proposta) {
        UUID plano = uuid(argumentos, "identificadorDoPlano");
        LocalDate referencia = LocalDate.parse(texto(
                argumentos, "dataDeReferencia"));
        Set<UUID> ignoradas = uuids(argumentos,
                "identificadoresDasPendenciasIgnoradas");
        ResultadoDaPreviaDoReplanejamento previa = replanejamento.gerarPrevia(
                usuario, plano, referencia, ignoradas);
        proposta.put("assinaturaDaPrevia", previa.assinaturaDaPrevia());
        proposta.put("previa", mapeador.convertValue(previa, MAPA));
        return Map.of("plano", plano, "assinaturaDaPrevia",
                previa.assinaturaDaPrevia());
    }

    private Map<String, Object> versoesDoPlano(UUID usuario, UUID plano) {
        return linhaOu404("""
                SELECT p.identificador, p.versao, p.estado,
                       COALESCE(MAX(d.versao), 0) AS versao_das_disponibilidades,
                       COALESCE(MAX(b.versao), 0) AS versao_dos_blocos
                  FROM planos_semanais p
                  LEFT JOIN disponibilidades_do_dia d ON d.plano_id = p.identificador
                  LEFT JOIN blocos_de_estudo b ON b.plano_id = p.identificador
                 WHERE p.identificador = ? AND p.usuario_id = ?
                 GROUP BY p.identificador
                """, "PLANO_SEMANAL_NAO_ENCONTRADO", plano, usuario);
    }

    private Map<String, Object> versoesDoBloco(UUID usuario, UUID bloco) {
        return linhaOu404("""
                SELECT b.identificador, b.versao, b.estado, p.versao AS versao_do_plano,
                       e.identificador AS identificador_da_execucao,
                       e.versao AS versao_da_execucao
                  FROM blocos_de_estudo b
                  JOIN planos_semanais p ON p.identificador = b.plano_id
                  LEFT JOIN execucoes_de_bloco e ON e.bloco_id = b.identificador
                 WHERE b.identificador = ? AND p.usuario_id = ?
                """, "BLOCO_DE_ESTUDO_NAO_ENCONTRADO", bloco, usuario);
    }

    private Map<String, Object> versoesDoEstudo(UUID usuario, UUID estudo) {
        return linhaOu404("""
                SELECT r.identificador, r.versao, r.situacao,
                       t.versao AS versao_do_topico, m.versao AS versao_da_materia
                  FROM registros_de_estudo r
                  JOIN topicos_da_materia t ON t.identificador = r.topico_id
                  JOIN materias m ON m.identificador = t.materia_id
                 WHERE r.identificador = ? AND m.usuario_id = ?
                """, "REGISTRO_DE_ESTUDO_NAO_ENCONTRADO", estudo, usuario);
    }

    private Map<String, Object> versoesDoTopico(UUID usuario, UUID topico,
            UUID material) {
        return versoesDoTopico(usuario, topico, material, true);
    }

    private Map<String, Object> versoesDoTopico(UUID usuario, UUID topico,
            UUID material, boolean exigirCobertura) {
        Map<String, Object> versoes = new LinkedHashMap<>(linhaOu404("""
                SELECT t.identificador, t.versao, t.arquivado,
                       m.versao AS versao_da_materia, m.arquivada
                  FROM topicos_da_materia t
                  JOIN materias m ON m.identificador = t.materia_id
                 WHERE t.identificador = ? AND m.usuario_id = ?
                """, "TOPICO_NAO_ENCONTRADO", topico, usuario));
        if (Boolean.TRUE.equals(versoes.get("arquivado"))
                || Boolean.TRUE.equals(versoes.get("arquivada"))) {
            throw new RegraDeDominio("TOPICO_ARQUIVADO",
                    "Use um topico e uma materia ativos.");
        }
        if (material != null) {
            Map<String, Object> versaoDoMaterial = linhaOu404("""
                    SELECT identificador, versao, arquivado
                      FROM materiais_de_estudo
                     WHERE identificador = ? AND usuario_id = ?
                    """, "MATERIAL_NAO_ENCONTRADO", material, usuario);
            if (Boolean.TRUE.equals(versaoDoMaterial.get("arquivado"))) {
                throw new RegraDeDominio("MATERIAL_ARQUIVADO",
                        "Use um material ativo.");
            }
            versoes.put("material", versaoDoMaterial);
            List<Map<String, Object>> coberturas = banco.queryForList("""
                    SELECT identificador, criado_em
                      FROM coberturas_de_topicos_por_material
                     WHERE material_id = ? AND topico_id = ?
                    """, material, topico);
            if (coberturas.isEmpty()) {
                if (exigirCobertura) {
                    throw new RegraDeDominio("MATERIAL_NAO_COBRE_TOPICO",
                            "O material informado nao cobre o topico.");
                }
                versoes.put("coberturaDoTopico",
                        Map.of("presente", false));
            } else {
                Map<String, Object> cobertura = new LinkedHashMap<>();
                cobertura.put("presente", true);
                coberturas.getFirst().forEach((chave, valor) ->
                        cobertura.put(paraCamelCase(chave), valor));
                versoes.put("coberturaDoTopico", cobertura);
            }
        }
        return versoes;
    }

    private Map<String, Object> linhaOu404(String sql, String codigo,
            Object... parametros) {
        List<Map<String, Object>> linhas = banco.queryForList(sql, parametros);
        if (linhas.isEmpty()) {
            throw new RecursoNaoEncontrado(codigo,
                    "O recurso informado nao foi encontrado.");
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        linhas.getFirst().forEach((chave, valor) -> resultado.put(
                paraCamelCase(chave), valor));
        return resultado;
    }

    private String resumo(String tipo, Map<String, Object> proposta) {
        return switch (tipo) {
            case "REGISTRO_DE_ESTUDO" -> "Registrar estudo de "
                    + proposta.get("duracaoEmMinutos") + " minutos.";
            case "CONCLUSAO_DO_BLOCO" -> "Concluir o bloco informado.";
            case "INTERRUPCAO_DO_BLOCO" -> "Interromper o bloco informado.";
            case "CORRECAO_DO_ESTUDO" -> "Corrigir o estudo informado preservando o anterior.";
            case "GERACAO_DO_PLANO" -> "Gerar o plano semanal conforme a previa exibida.";
            case "REPLANEJAMENTO" -> "Replanejar as pendencias conforme a previa exibida.";
            case "ALTERACAO_DE_DISPONIBILIDADE" -> "Alterar a disponibilidade semanal.";
            case "ALTERACAO_DE_PRIORIDADES" -> "Alterar as prioridades das materias.";
            default -> tipo;
        };
    }

    private String json(Object valor) {
        try { return mapeador.writeValueAsString(valor); }
        catch (Exception excecao) { throw new IllegalArgumentException(excecao); }
    }

    private Object objetoJsonOpcional(String valor) {
        if (valor == null) return null;
        try { return mapeador.readValue(valor, Object.class); }
        catch (Exception excecao) { throw new IllegalStateException(excecao); }
    }

    private void validarEvidencia(String tipo, Map<String, Object> proposta) {
        if (!Set.of("REGISTRO_DE_ESTUDO", "CORRECAO_DO_ESTUDO",
                "CONCLUSAO_DO_BLOCO", "INTERRUPCAO_DO_BLOCO").contains(tipo)) {
            return;
        }
        DadosDaEvidencia dados;
        try {
            Object valor = proposta.get("evidencia");
            dados = valor == null ? null
                    : mapeador.convertValue(valor, DadosDaEvidencia.class);
        } catch (IllegalArgumentException excecao) {
            throw new RegraDeDominio("EVIDENCIA_INVALIDA",
                    "Os dados da evidencia sao invalidos.");
        }
        boolean exigeResultado = Set.of(
                "REGISTRO_DE_ESTUDO", "CORRECAO_DO_ESTUDO").contains(tipo);
        TipoDeEstudo tipoDeEstudo = exigeResultado
                ? TipoDeEstudo.valueOf(texto(proposta, "tipoDeEstudo"))
                : null;
        evidencias.validar(tipoDeEstudo, dados, exigeResultado);
    }

    private UUID uuid(Map<String, Object> mapa, String chave) {
        return UUID.fromString(texto(mapa, chave));
    }

    private UUID uuidOpcional(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave);
        return valor == null ? null : UUID.fromString(valor.toString());
    }

    private String texto(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave);
        if (valor == null || valor.toString().isBlank()) {
            throw new IllegalArgumentException(chave + " e obrigatorio.");
        }
        return valor.toString();
    }

    private int inteiro(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave);
        if (valor instanceof Number numero) return numero.intValue();
        return Integer.parseInt(texto(mapa, chave));
    }

    private Set<UUID> uuids(Map<String, Object> mapa, String chave) {
        Object valor = mapa.get(chave);
        if (!(valor instanceof List<?> lista)) return Set.of();
        return lista.stream().map(Object::toString).map(UUID::fromString)
                .collect(Collectors.toSet());
    }

    private String paraCamelCase(String valor) {
        StringBuilder resultado = new StringBuilder();
        boolean maiuscula = false;
        for (char caractere : valor.toCharArray()) {
            if (caractere == '_') { maiuscula = true; continue; }
            resultado.append(maiuscula ? Character.toUpperCase(caractere) : caractere);
            maiuscula = false;
        }
        return resultado.toString();
    }
}
