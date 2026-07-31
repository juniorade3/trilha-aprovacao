package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ProblemaDaImportacao;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Separa resultados reproduzíveis do validador de fatos externos que precisam
 * sobreviver ao versionamento da extração.
 */
public final class PoliticaDosProblemasPersistentesDaImportacao {
    public static final String EVIDENCIA_ASSISTIDA_NAO_VERIFICADA =
            "EVIDENCIA_ASSISTIDA_NAO_VERIFICADA";

    private PoliticaDosProblemasPersistentesDaImportacao() {
    }

    public static boolean devePersistirEntreVersoes(
            ProblemaDaImportacao problema) {
        if (problema == null) return false;
        return "fonte".equals(problema.tipoDoRecurso())
                || "fonte".equals(problema.caminho())
                || EVIDENCIA_ASSISTIDA_NAO_VERIFICADA.equals(
                        problema.codigo());
    }

    public static List<ProblemaDaImportacao> aplicaveisAoCargo(
            ExtracaoEstruturadaDoEdital extracao, String chaveDoCargo,
            List<ProblemaDaImportacao> problemas) {
        if (problemas == null || problemas.isEmpty()) return List.of();
        if (extracao == null || chaveDoCargo == null
                || chaveDoCargo.isBlank()) {
            return problemas.stream()
                    .filter(PoliticaDosProblemasPersistentesDaImportacao
                            ::devePersistirEntreVersoes)
                    .toList();
        }
        Set<String> chaves = chavesDaArvore(extracao, chaveDoCargo);
        return problemas.stream()
                .filter(PoliticaDosProblemasPersistentesDaImportacao
                        ::devePersistirEntreVersoes)
                .filter(problema -> ehGlobal(problema)
                        || chaves.contains(problema.chaveDoRecurso()))
                .toList();
    }

    private static Set<String> chavesDaArvore(
            ExtracaoEstruturadaDoEdital extracao, String cargo) {
        Set<String> chaves = new LinkedHashSet<>();
        chaves.add(cargo);
        extracao.provas().stream()
                .filter(prova -> cargo.equals(prova.chaveDoCargo()))
                .forEach(prova -> {
                    chaves.add(prova.chave());
                    prova.grupos().forEach(grupo ->
                            chaves.add(grupo.chave()));
                });
        extracao.materias().stream()
                .filter(materia -> cargo.equals(materia.chaveDoCargo()))
                .forEach(materia -> {
                    chaves.add(materia.chave());
                    materia.topicos().forEach(topico ->
                            chaves.add(topico.chave()));
                    materia.itensDoEdital().forEach(item ->
                            chaves.add(item.chave()));
                });
        return Set.copyOf(chaves);
    }

    private static boolean ehGlobal(ProblemaDaImportacao problema) {
        return Set.of("fonte", "concurso", "edital", "extracao")
                .contains(problema.tipoDoRecurso());
    }
}
