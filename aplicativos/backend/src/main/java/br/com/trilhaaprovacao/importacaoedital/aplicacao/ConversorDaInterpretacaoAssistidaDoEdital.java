package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.concursos.dominio.CaraterDaProva;
import br.com.trilhaaprovacao.concursos.dominio.NivelDeEscolaridade;
import br.com.trilhaaprovacao.concursos.dominio.TipoDeProva;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.DadoDecimalInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.DadoInteiroInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.DadoTextualInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.EvidenciaInterpretada;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.GrupoInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.ItemInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.MateriaInterpretada;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.ProvaInterpretada;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.TopicoInterpretado;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.CargoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ConcursoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.EditalExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.GrupoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ItemExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.MateriaExtraida;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ProvaExtraida;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.TopicoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ProblemaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.ProvenienciaDoDado;
import br.com.trilhaaprovacao.importacaoedital.dominio.SeveridadeDoProblemaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.ValorExtraido;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

final class ConversorDaInterpretacaoAssistidaDoEdital {
    static final String INCERTEZA_DE_EVIDENCIAS_NAO_VERIFICADAS =
            "A interpretação assistida contém evidências não verificadas; "
                    + "revise os campos indicados.";
    private static final BigDecimal CONFIANCA_COM_EVIDENCIA =
            new BigDecimal("0.9000");
    private static final BigDecimal CONFIANCA_INFERIDA =
            new BigDecimal("0.5000");
    private final NormalizadorDoTextoDoEdital normalizador =
            new NormalizadorDoTextoDoEdital();

    ResultadoDaConversao converter(ExtracaoEstruturadaDoEdital atual,
            ArvoreInterpretadaDoEdital arvore, String chaveDoCargoAlvo,
            String textoLocal) {
        Objects.requireNonNull(atual);
        Objects.requireNonNull(arvore);
        VerificadorDeEvidencia evidencias = new VerificadorDeEvidencia(
                textoLocal, atual.fonte().paginas());
        CargoExtraido anterior = chaveDoCargoAlvo == null ? null
                : atual.cargos().stream().filter(cargo ->
                        chaveDoCargoAlvo.equals(cargo.chave()))
                        .findFirst().orElse(null);
        int ordemDoCargo = anterior == null
                ? proximaOrdem(atual.cargos().stream()
                        .map(CargoExtraido::ordem).toList())
                : anterior.ordem();
        String chaveDoCargo = anterior == null
                ? chave("cargo", ordemDoCargo,
                        texto(arvore.cargo().nome()))
                : anterior.chave();

        CargoExtraido cargo = new CargoExtraido(chaveDoCargo,
                evidencias.texto(arvore.cargo().nome(), "cargo",
                        chaveDoCargo, "nome"),
                evidencias.texto(arvore.cargo().area(), "cargo",
                        chaveDoCargo, "area"),
                evidencias.texto(arvore.cargo().especialidade(), "cargo",
                        chaveDoCargo, "especialidade"),
                evidencias.enumeracao(arvore.cargo().nivelDeEscolaridade(),
                        this::escolaridade, "cargo", chaveDoCargo,
                        "nivelDeEscolaridade"),
                ordemDoCargo);

        List<ProvaExtraida> provas = new ArrayList<>();
        List<MateriaExtraida> materias = new ArrayList<>();
        int ordemDaProva = 0;
        for (ProvaInterpretada interpretada : arvore.cargo().provas()) {
            ordemDaProva++;
            String chaveDaProva = chave("prova", ordemDaProva,
                    texto(interpretada.nome()));
            List<GrupoInterpretado> gruposInterpretados =
                    new ArrayList<>(interpretada.grupos());
            if (!interpretada.materiasSemGrupo().isEmpty()
                    || gruposInterpretados.isEmpty()) {
                gruposInterpretados.add(new GrupoInterpretado(
                        dado("Conteúdos programáticos"), null, null, null,
                        interpretada.materiasSemGrupo()));
            }
            List<GrupoExtraido> grupos = new ArrayList<>();
            int ordemDoGrupo = 0;
            for (GrupoInterpretado grupoInterpretado : gruposInterpretados) {
                ordemDoGrupo++;
                String chaveDoGrupo = chave("grupo", ordemDoGrupo,
                        texto(grupoInterpretado.nome()));
                GrupoExtraido grupo = new GrupoExtraido(chaveDoGrupo,
                        evidencias.texto(grupoInterpretado.nome(), "grupo",
                                chaveDoGrupo, "nome"),
                        ordemDoGrupo,
                        evidencias.inteiro(
                                grupoInterpretado.quantidadeDeQuestoes(),
                                "grupo", chaveDoGrupo,
                                "quantidadeDeQuestoes"),
                        evidencias.decimal(grupoInterpretado.pontuacaoMaxima(),
                                "grupo", chaveDoGrupo, "pontuacaoMaxima"),
                        evidencias.decimal(grupoInterpretado.pontuacaoMinima(),
                                "grupo", chaveDoGrupo, "pontuacaoMinima"));
                grupos.add(grupo);
                int ordemDaMateria = 0;
                for (MateriaInterpretada materiaInterpretada
                        : grupoInterpretado.materias()) {
                    ordemDaMateria++;
                    materias.add(materia(materiaInterpretada, chaveDoCargo,
                            chaveDaProva, chaveDoGrupo, ordemDaMateria,
                            evidencias));
                }
            }
            provas.add(new ProvaExtraida(chaveDaProva, chaveDoCargo,
                    evidencias.texto(interpretada.nome(), "prova",
                            chaveDaProva, "nome"),
                    evidencias.enumeracao(interpretada.tipo(), this::tipo,
                            "prova", chaveDaProva, "tipo"),
                    evidencias.enumeracao(interpretada.carater(),
                            this::carater, "prova", chaveDaProva, "carater"),
                    ordemDaProva,
                    evidencias.enumeracao(interpretada.dataHora(),
                            this::dataHora, "prova", chaveDaProva,
                            "dataHora"),
                    evidencias.inteiro(interpretada.duracaoEmMinutos(),
                            "prova", chaveDaProva, "duracaoEmMinutos"),
                    evidencias.inteiro(interpretada.quantidadeDeQuestoes(),
                            "prova", chaveDaProva,
                            "quantidadeDeQuestoes"),
                    evidencias.decimal(interpretada.pontuacaoMaxima(),
                            "prova", chaveDaProva, "pontuacaoMaxima"),
                    evidencias.decimal(interpretada.pontuacaoMinima(),
                            "prova", chaveDaProva, "pontuacaoMinima"),
                    grupos));
        }

        List<CargoExtraido> cargos = substituir(atual.cargos(),
                CargoExtraido::chave, chaveDoCargo, cargo);
        List<ProvaExtraida> todasAsProvas = new ArrayList<>(atual.provas()
                .stream().filter(prova -> !chaveDoCargo.equals(
                        prova.chaveDoCargo())).toList());
        todasAsProvas.addAll(provas);
        List<MateriaExtraida> todasAsMaterias =
                new ArrayList<>(atual.materias().stream()
                        .filter(materia -> !chaveDoCargo.equals(
                                materia.chaveDoCargo())).toList());
        todasAsMaterias.addAll(materias);

        ConcursoExtraido concurso = new ConcursoExtraido(
                evidencias.preferir(arvore.concurso().nome(),
                        atual.concurso() == null ? null
                                : atual.concurso().nome(),
                        "concurso", "concurso", "nome"),
                evidencias.preferir(arvore.concurso().descricao(),
                        atual.concurso() == null ? null
                                : atual.concurso().descricao(),
                        "concurso", "concurso", "descricao"),
                evidencias.preferir(arvore.concurso().orgao(),
                        atual.concurso() == null ? null
                                : atual.concurso().orgao(),
                        "concurso", "concurso", "orgao"),
                evidencias.preferir(arvore.concurso().banca(),
                        atual.concurso() == null ? null
                                : atual.concurso().banca(),
                        "concurso", "concurso", "banca"),
                atual.concurso() == null ? ausente()
                        : atual.concurso().dataPrevista());
        EditalExtraido edital = new EditalExtraido(
                evidencias.preferir(arvore.edital().titulo(),
                        atual.edital() == null ? null : atual.edital().titulo(),
                        "edital", "edital", "titulo"),
                evidencias.preferir(arvore.edital().numero(),
                        atual.edital() == null ? null : atual.edital().numero(),
                        "edital", "edital", "numero"),
                evidencias.preferir(arvore.edital().ano(),
                        atual.edital() == null ? null : atual.edital().ano(),
                        "edital", "edital", "ano"),
                evidencias.preferir(arvore.edital().descricao(),
                        atual.edital() == null ? null
                                : atual.edital().descricao(),
                        "edital", "edital", "descricao"),
                atual.edital() == null ? ausente()
                        : atual.edital().dataDePublicacao());
        List<String> incertezas = new ArrayList<>(atual.incertezas());
        if (!evidencias.problemas.isEmpty()) {
            incertezas.add(INCERTEZA_DE_EVIDENCIAS_NAO_VERIFICADAS);
        }
        ExtracaoEstruturadaDoEdital convertida =
                new ExtracaoEstruturadaDoEdital(
                        atual.versaoDoContrato(), atual.fonte(), concurso,
                        edital, cargos, todasAsProvas, todasAsMaterias,
                        atual.avisos(), incertezas);
        return new ResultadoDaConversao(convertida,
                List.copyOf(evidencias.problemas), chaveDoCargo);
    }

    private MateriaExtraida materia(MateriaInterpretada interpretada,
            String chaveDoCargo, String chaveDaProva, String chaveDoGrupo,
            int ordem, VerificadorDeEvidencia evidencias) {
        String chaveDaMateria = chave("materia", ordem,
                texto(interpretada.nome()));
        Map<String, String> topicosPorNumero = new LinkedHashMap<>();
        int ordemDoTopico = 0;
        for (TopicoInterpretado topico : interpretada.topicos()) {
            ordemDoTopico++;
            String chave = chave("topico", ordemDoTopico,
                    texto(topico.nome()));
            String numero = texto(topico.numeroOficial());
            if (numero != null && topico.numeroOficial() != null
                    && evidencias.verificavel(
                            topico.numeroOficial().evidencia())) {
                topicosPorNumero.putIfAbsent(numero, chave);
            }
        }
        List<TopicoExtraido> topicos = new ArrayList<>();
        ordemDoTopico = 0;
        for (TopicoInterpretado topico : interpretada.topicos()) {
            ordemDoTopico++;
            String chave = topicosPorNumero.getOrDefault(
                    texto(topico.numeroOficial()),
                    chave("topico", ordemDoTopico, texto(topico.nome())));
            topicos.add(new TopicoExtraido(chave,
                    evidencias.associacao(topico.numeroDoPai(),
                            topicosPorNumero, "topico", chave,
                            "chaveDoPai"),
                    evidencias.texto(topico.numeroOficial(), "topico", chave,
                            "numeroOficial"),
                    evidencias.texto(topico.nome(), "topico", chave, "nome"),
                    evidencias.texto(topico.descricao(), "topico", chave,
                            "descricao"),
                    ordemDoTopico));
        }
        List<ItemExtraido> itens = new ArrayList<>();
        int ordemDoItem = 0;
        for (ItemInterpretado item : interpretada.itens()) {
            ordemDoItem++;
            String chave = chave("item", ordemDoItem,
                    texto(item.descricaoLiteral()));
            String descricao = texto(item.descricaoLiteral());
            itens.add(new ItemExtraido(chave, null,
                    evidencias.texto(item.numeroOficial(), "itemDoEdital",
                            chave, "numeroOficial"),
                    evidencias.texto(item.descricaoLiteral(), "itemDoEdital",
                            chave, "descricaoLiteral"),
                    normalizador.normalizarNome(descricao), ordemDoItem,
                    evidencias.associacao(item.numeroDoTopico(),
                            topicosPorNumero, "itemDoEdital", chave,
                            "chaveDoTopicoSugerido")));
        }
        return new MateriaExtraida(chaveDaMateria, chaveDoCargo,
                chaveDaProva, chaveDoGrupo,
                evidencias.texto(interpretada.nome(), "materia",
                        chaveDaMateria, "nome"),
                evidencias.texto(interpretada.descricao(), "materia",
                        chaveDaMateria, "descricao"),
                ordem,
                evidencias.decimal(interpretada.peso(), "materia",
                        chaveDaMateria, "peso"),
                evidencias.inteiro(interpretada.quantidadeDeQuestoes(),
                        "materia", chaveDaMateria, "quantidadeDeQuestoes"),
                evidencias.decimal(interpretada.pontuacaoMaxima(), "materia",
                        chaveDaMateria, "pontuacaoMaxima"),
                topicos, itens);
    }

    private NivelDeEscolaridade escolaridade(String valor) {
        String normalizado = normalizarEnum(valor);
        if (normalizado == null) return null;
        if (normalizado.contains("FUNDAMENTAL")) {
            return NivelDeEscolaridade.FUNDAMENTAL;
        }
        if (normalizado.contains("MEDIO")) return NivelDeEscolaridade.MEDIO;
        if (normalizado.contains("TECNICO")) {
            return NivelDeEscolaridade.TECNICO;
        }
        if (normalizado.contains("SUPERIOR")
                || normalizado.contains("GRADUACAO")) {
            return NivelDeEscolaridade.SUPERIOR;
        }
        if (normalizado.contains("NAO_INFORMADO")) {
            return NivelDeEscolaridade.NAO_INFORMADO;
        }
        return null;
    }

    private TipoDeProva tipo(String valor) {
        String normalizado = normalizarEnum(valor);
        if (normalizado == null) return null;
        for (TipoDeProva candidato : TipoDeProva.values()) {
            if (normalizado.contains(candidato.name())) return candidato;
        }
        return TipoDeProva.OUTRA;
    }

    private CaraterDaProva carater(String valor) {
        String normalizado = normalizarEnum(valor);
        if (normalizado == null) return null;
        boolean eliminatorio = normalizado.contains("ELIMINATORIO");
        boolean classificatorio = normalizado.contains("CLASSIFICATORIO");
        if (eliminatorio && classificatorio) {
            return CaraterDaProva.ELIMINATORIO_E_CLASSIFICATORIO;
        }
        if (eliminatorio) return CaraterDaProva.ELIMINATORIO;
        if (classificatorio) return CaraterDaProva.CLASSIFICATORIO;
        if (normalizado.contains("NAO_INFORMADO")) {
            return CaraterDaProva.NAO_INFORMADO;
        }
        return null;
    }

    private OffsetDateTime dataHora(String valor) {
        if (valor == null) return null;
        try {
            return OffsetDateTime.parse(valor);
        } catch (DateTimeParseException ignorada) {
            return null;
        }
    }

    private String chave(String tipo, int ordem, String nome) {
        return normalizador.criarChave(
                "ia-" + tipo + "-" + UUID.randomUUID(), ordem, nome);
    }

    private static String texto(DadoTextualInterpretado dado) {
        return dado == null ? null : dado.valor();
    }

    private static DadoTextualInterpretado dado(String valor) {
        return new DadoTextualInterpretado(valor,
                new EvidenciaInterpretada(null, null));
    }

    private static String normalizarEnum(String valor) {
        if (valor == null || valor.isBlank()) return null;
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_");
    }

    private static int proximaOrdem(List<Integer> ordens) {
        return ordens.stream().filter(Objects::nonNull)
                .mapToInt(Integer::intValue).max().orElse(0) + 1;
    }

    private static <T> List<T> substituir(List<T> atuais,
            Function<T, String> chave, String alvo, T substituto) {
        List<T> resultado = new ArrayList<>();
        boolean substituiu = false;
        for (T item : atuais) {
            if (alvo.equals(chave.apply(item))) {
                if (!substituiu) resultado.add(substituto);
                substituiu = true;
            } else {
                resultado.add(item);
            }
        }
        if (!substituiu) resultado.add(substituto);
        return List.copyOf(resultado);
    }

    private static <T> ValorExtraido<T> ausente() {
        return new ValorExtraido<>(null, BigDecimal.ZERO, null, false);
    }

    record ResultadoDaConversao(
            ExtracaoEstruturadaDoEdital extracao,
            List<ProblemaDaImportacao> problemasAdicionais,
            String chaveDoCargo) {
    }

    private static final class VerificadorDeEvidencia {
        private final String[] paginas;
        private final int quantidadeDePaginas;
        private final List<ProblemaDaImportacao> problemas =
                new ArrayList<>();

        private VerificadorDeEvidencia(String textoLocal,
                int quantidadeDePaginas) {
            paginas = textoLocal == null ? new String[0]
                    : textoLocal.split("\\f", -1);
            this.quantidadeDePaginas = quantidadeDePaginas;
        }

        private ValorExtraido<String> texto(DadoTextualInterpretado dado,
                String tipo, String chave, String campo) {
            if (dado == null || dado.valor() == null) return ausente();
            return valor(dado.valor(), dado.evidencia(), tipo, chave, campo);
        }

        private ValorExtraido<Integer> inteiro(DadoInteiroInterpretado dado,
                String tipo, String chave, String campo) {
            if (dado == null || dado.valor() == null) return ausente();
            return valor(dado.valor(), dado.evidencia(), tipo, chave, campo);
        }

        private ValorExtraido<BigDecimal> decimal(
                DadoDecimalInterpretado dado, String tipo, String chave,
                String campo) {
            if (dado == null || dado.valor() == null) return ausente();
            return valor(dado.valor(), dado.evidencia(), tipo, chave, campo);
        }

        private <T> ValorExtraido<T> enumeracao(
                DadoTextualInterpretado dado, Function<String, T> conversor,
                String tipo, String chave, String campo) {
            if (dado == null || dado.valor() == null) return ausente();
            T convertido = conversor.apply(dado.valor());
            if (convertido == null) {
                marcarRevisao(tipo, chave, campo);
                return ausente();
            }
            return valor(convertido, dado.evidencia(), tipo, chave, campo);
        }

        private ValorExtraido<String> preferir(
                DadoTextualInterpretado candidato,
                ValorExtraido<String> atual, String tipo, String chave,
                String campo) {
            return candidato == null || candidato.valor() == null
                    ? atualOuAusente(atual)
                    : texto(candidato, tipo, chave, campo);
        }

        private ValorExtraido<Integer> preferir(
                DadoInteiroInterpretado candidato,
                ValorExtraido<Integer> atual, String tipo, String chave,
                String campo) {
            return candidato == null || candidato.valor() == null
                    ? atualOuAusente(atual)
                    : valor(candidato.valor(), candidato.evidencia(), tipo,
                            chave, campo);
        }

        private <T> ValorExtraido<T> valor(T valor,
                EvidenciaInterpretada evidencia, String tipo, String chave,
                String campo) {
            if (verificavel(evidencia)) {
                return new ValorExtraido<>(valor, CONFIANCA_COM_EVIDENCIA,
                        new ProvenienciaDoDado(evidencia.pagina(),
                                "Interpretação assistida",
                                evidencia.trecho()), false);
            }
            marcarRevisao(tipo, chave, campo);
            return new ValorExtraido<>(valor, CONFIANCA_INFERIDA,
                    new ProvenienciaDoDado(null,
                            "Interpretação assistida não verificada", null),
                    true);
        }

        private String associacao(DadoTextualInterpretado referencia,
                Map<String, String> recursosPorNumero, String tipo,
                String chave, String campo) {
            if (referencia == null || referencia.valor() == null) return null;
            String recurso = recursosPorNumero.get(referencia.valor());
            if (!verificavel(referencia.evidencia()) || recurso == null) {
                marcarRevisao(tipo, chave, campo);
                return null;
            }
            return recurso;
        }

        private boolean verificavel(EvidenciaInterpretada evidencia) {
            if (evidencia == null || evidencia.pagina() == null
                    || evidencia.trecho() == null
                    || evidencia.pagina() > quantidadeDePaginas
                    || evidencia.pagina() > paginas.length) {
                return false;
            }
            String pagina = normalizarTrecho(paginas[evidencia.pagina() - 1]);
            String trecho = normalizarTrecho(evidencia.trecho());
            return !trecho.isBlank() && pagina.contains(trecho);
        }

        private void marcarRevisao(String tipo, String chave, String campo) {
            ProblemaDaImportacao problema = new ProblemaDaImportacao(
                    SeveridadeDoProblemaDaImportacao.EXIGE_DECISAO,
                    "EVIDENCIA_ASSISTIDA_NAO_VERIFICADA",
                    "Confira o valor sugerido pela interpretação assistida.",
                    null, tipo, chave, campo);
            boolean existente = problemas.stream().anyMatch(item ->
                    Objects.equals(item.tipoDoRecurso(), tipo)
                            && Objects.equals(item.chaveDoRecurso(), chave)
                            && Objects.equals(item.campo(), campo));
            if (!existente) problemas.add(problema);
        }

        private static String normalizarTrecho(String valor) {
            if (valor == null) return "";
            return Normalizer.normalize(valor, Normalizer.Form.NFC)
                    .replaceAll("\\s+", " ").strip()
                    .toLowerCase(Locale.ROOT);
        }

        private static <T> ValorExtraido<T> atualOuAusente(
                ValorExtraido<T> atual) {
            return atual == null ? ausente() : atual;
        }
    }
}
