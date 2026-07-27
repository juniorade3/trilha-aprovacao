package br.com.trilhaaprovacao.importacaoedital.api;

import br.com.trilhaaprovacao.importacaoedital.aplicacao.ConsultaDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.AvaliacaoDoCargo;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.PoliticaDosProblemasPersistentesDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.PreparadorDaImportacaoCompletaDoEdital.ContagensDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.PreparadorDaImportacaoCompletaDoEdital.ItemDaPreviaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.PreparadorDaImportacaoCompletaDoEdital.PreviaDaImportacaoCompleta;
import br.com.trilhaaprovacao.importacaoedital.dominio.EstadoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ModoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.PoliticaDeReutilizacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.ProblemaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.SeveridadeDoProblemaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.TipoDaFonteDoEdital;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ValidadorDaExtracaoDoEdital;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RespostaDaImportacaoDeEdital(
        UUID identificador,
        EstadoDaImportacaoDeEdital estado,
        TipoDaFonteDoEdital tipoDaFonte,
        String nomeDoArquivo,
        String tipoMime,
        String sha256,
        long tamanhoEmBytes,
        ModoDaImportacaoDeEdital modo,
        UUID identificadorDoConcursoExistente,
        PoliticaDeReutilizacao politicaDeReutilizacao,
        int versaoAtualDaExtracao,
        String hashDaExtracaoAtual,
        String chaveDoCargoSelecionado,
        int tentativaDaPreparacao,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        ExtracaoEstruturadaDoEdital extracao,
        List<ProblemaDaImportacao> problemas,
        boolean interpretacaoAssistidaDisponivel,
        List<AvaliacaoDoCargo> avaliacoesDosCargos,
        RespostaDaPrevia previa) {

    public static RespostaDaImportacaoDeEdital de(
            ConsultaDaImportacaoDeEdital consulta) {
        return de(consulta, null, false);
    }

    public static RespostaDaImportacaoDeEdital de(
            ConsultaDaImportacaoDeEdital consulta,
            boolean interpretacaoAssistidaDisponivel) {
        return de(consulta, null, interpretacaoAssistidaDisponivel);
    }

    public static RespostaDaImportacaoDeEdital de(
            ConsultaDaImportacaoDeEdital consulta,
            RespostaDaPrevia previa) {
        return de(consulta, previa, false);
    }

    public static RespostaDaImportacaoDeEdital de(
            ConsultaDaImportacaoDeEdital consulta,
            RespostaDaPrevia previa,
            boolean interpretacaoAssistidaDisponivel) {
        var staging = consulta.staging();
        var importacao = staging.importacao();
        List<ProblemaDaImportacao> problemas = problemasAplicaveis(
                staging.extracao(), staging.problemas(),
                importacao.chaveDoCargoSelecionado());
        List<AvaliacaoDoCargo> avaliacoes = avaliacoes(
                staging.extracao(), staging.problemas());
        return new RespostaDaImportacaoDeEdital(
                importacao.identificador(), importacao.estado(),
                importacao.tipoDaFonte(), importacao.nomeDoArquivo(),
                importacao.tipoMime(), importacao.sha256(),
                importacao.tamanhoEmBytes(), consulta.modo(),
                consulta.identificadorDoConcursoExistente(),
                consulta.politicaDeReutilizacao(),
                importacao.versaoAtualDaExtracao(),
                importacao.hashDaExtracaoAtual(),
                importacao.chaveDoCargoSelecionado(),
                consulta.tentativaDaPreparacao(), importacao.criadoEm(),
                importacao.atualizadoEm(), staging.extracao(),
                problemas, interpretacaoAssistidaDisponivel, avaliacoes,
                previa);
    }

    private static List<AvaliacaoDoCargo> avaliacoes(
            ExtracaoEstruturadaDoEdital extracao,
            List<ProblemaDaImportacao> persistidos) {
        if (extracao == null) return List.of();
        ValidadorDaExtracaoDoEdital validador =
                new ValidadorDaExtracaoDoEdital();
        return validador.avaliarCargos(extracao).stream().map(avaliacao -> {
            List<ProblemaDaImportacao> problemas = unir(
                    avaliacao.problemas(),
                    PoliticaDosProblemasPersistentesDaImportacao
                            .aplicaveisAoCargo(extracao,
                                    avaliacao.chaveDoCargo(), persistidos));
            boolean pronto = problemas.stream().noneMatch(problema ->
                    problema.severidade()
                            == SeveridadeDoProblemaDaImportacao.BLOQUEANTE
                            || problema.severidade()
                            == SeveridadeDoProblemaDaImportacao.EXIGE_DECISAO);
            return new AvaliacaoDoCargo(avaliacao.chaveDoCargo(), pronto,
                    problemas);
        }).toList();
    }

    private static List<ProblemaDaImportacao> problemasAplicaveis(
            ExtracaoEstruturadaDoEdital extracao,
            List<ProblemaDaImportacao> persistidos,
            String chaveDoCargo) {
        if (chaveDoCargo == null || chaveDoCargo.isBlank()
                || extracao == null) {
            return persistidos == null ? List.of() : List.copyOf(persistidos);
        }
        ValidadorDaExtracaoDoEdital validador =
                new ValidadorDaExtracaoDoEdital();
        List<ProblemaDaImportacao> problemas = new ArrayList<>(
                validador.validarParaCargo(extracao, chaveDoCargo));
        problemas.addAll(PoliticaDosProblemasPersistentesDaImportacao
                .aplicaveisAoCargo(extracao, chaveDoCargo, persistidos));
        return unir(problemas, List.of());
    }

    private static List<ProblemaDaImportacao> unir(
            List<ProblemaDaImportacao> primeiros,
            List<ProblemaDaImportacao> segundos) {
        Map<String, ProblemaDaImportacao> unicos = new LinkedHashMap<>();
        java.util.stream.Stream.concat(primeiros.stream(), segundos.stream())
                .forEach(problema -> unicos.putIfAbsent(
                        problema.codigo() + "\0" + problema.tipoDoRecurso()
                                + "\0" + problema.chaveDoRecurso() + "\0"
                                + problema.campo() + "\0"
                                + problema.caminho(),
                        problema));
        return List.copyOf(unicos.values());
    }

    public record RespostaDaPrevia(
            String resumo,
            ContagensDaImportacao contagens,
            List<ItemDaPreviaDaImportacao> itensACriar,
            List<ItemDaPreviaDaImportacao> itensAReutilizar,
            List<ProblemaDaImportacao> conflitos,
            List<String> incertezas,
            List<String> camposAusentes,
            boolean nadaFoiAlterado) {

        public static RespostaDaPrevia de(
                PreviaDaImportacaoCompleta previa) {
            return new RespostaDaPrevia(previa.resumo(), previa.contagens(),
                    previa.itensACriar(), previa.itensAReutilizar(),
                    previa.conflitos(), previa.incertezas(),
                    previa.camposAusentes(), true);
        }
    }

    public record RespostaDaPreparacao(
            RespostaDaImportacaoDeEdital importacao,
            RespostaDaPrevia previa) {
    }

    public record RespostaDoRelatorio(
            UUID identificadorDaImportacao,
            UUID identificadorDoConcurso,
            String situacaoDoConcurso,
            Map<String, Integer> contagens,
            Map<String, List<UUID>> identificadoresCriados,
            List<String> reutilizacoes,
            List<String> pendencias,
            List<String> incertezas,
            int sugestoesDeMapeamento,
            String aplicadoEm) {

        public static RespostaDoRelatorio de(Map<String, Object> relatorio) {
            Map<String, Object> recibo = mapa(relatorio.get("recibo"));
            int sugestoes = inteiro(recibo,
                    "sugestoesDeMapeamentoPendentes");
            List<String> pendencias = new ArrayList<>();
            lista(relatorio.get("problemas")).forEach(item -> {
                Map<String, Object> problema = mapa(item);
                Object mensagem = problema.get("mensagem");
                if (mensagem != null) pendencias.add(mensagem.toString());
            });
            if (sugestoes > 0) {
                pendencias.add(sugestoes
                        + " sugestoes de mapeamento aguardam revisao.");
            }
            return new RespostaDoRelatorio(
                    uuid(recibo, "identificadorDaImportacao"),
                    uuid(recibo, "identificadorDoConcurso"),
                    texto(recibo, "situacaoDoConcurso"),
                    contagens(recibo), identificadores(relatorio),
                    textos(relatorio.get("reutilizacoes")),
                    List.copyOf(pendencias),
                    textos(relatorio.get("incertezas")), sugestoes,
                    String.valueOf(relatorio.get("aplicadoEm")));
        }

        private static Map<String, Integer> contagens(
                Map<String, Object> recibo) {
            Map<String, Integer> resultado = new LinkedHashMap<>();
            List.of("provasCriadas", "gruposCriados", "materiasCriadas",
                    "materiasReutilizadas", "topicosCriados",
                    "topicosReutilizados", "itensCriados",
                    "sugestoesDeMapeamentoPendentes").forEach(chave ->
                            resultado.put(chave, inteiro(recibo, chave)));
            return Map.copyOf(resultado);
        }

        private static Map<String, List<UUID>> identificadores(
                Map<String, Object> relatorio) {
            Object fonte = relatorio.containsKey("identificadoresCriados")
                    ? relatorio.get("identificadoresCriados")
                    : relatorio.get("identificadoresPorChave");
            Map<String, List<UUID>> resultado = new LinkedHashMap<>();
            mapa(fonte).forEach((chave, valor) -> {
                if (valor instanceof List<?> valores) {
                    resultado.put(chave, valores.stream().map(item ->
                            UUID.fromString(item.toString())).toList());
                } else if (valor != null) {
                    resultado.put(chave,
                            List.of(UUID.fromString(valor.toString())));
                }
            });
            return Map.copyOf(resultado);
        }

        private static List<String> textos(Object valor) {
            return lista(valor).stream().map(item -> {
                Map<String, Object> reutilizacao = mapa(item);
                if (reutilizacao.isEmpty()) return item.toString();
                return "%s %s: %s".formatted(
                        reutilizacao.getOrDefault("tipo", "RECURSO"),
                        reutilizacao.getOrDefault("chaveExtraida", ""),
                        reutilizacao.getOrDefault(
                                "identificadorDoRecurso", ""));
            }).toList();
        }

        private static List<?> lista(Object valor) {
            return valor instanceof List<?> lista ? lista : List.of();
        }

        private static Map<String, Object> mapa(Object valor) {
            Map<String, Object> resultado = new LinkedHashMap<>();
            if (valor instanceof Map<?, ?> mapa) {
                mapa.forEach((chave, item) -> resultado.put(
                        chave.toString(), item));
            }
            return resultado;
        }

        private static UUID uuid(Map<String, Object> mapa, String chave) {
            return UUID.fromString(texto(mapa, chave));
        }

        private static String texto(Map<String, Object> mapa, String chave) {
            Object valor = mapa.get(chave);
            if (valor == null) {
                throw new IllegalStateException(
                        "Relatorio da importacao incompleto.");
            }
            return valor.toString();
        }

        private static int inteiro(Map<String, Object> mapa, String chave) {
            Object valor = mapa.get(chave);
            if (valor instanceof Number numero) return numero.intValue();
            return valor == null ? 0 : Integer.parseInt(valor.toString());
        }
    }
}
