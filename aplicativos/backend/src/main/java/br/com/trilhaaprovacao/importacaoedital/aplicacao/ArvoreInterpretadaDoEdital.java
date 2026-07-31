package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import java.math.BigDecimal;
import java.util.List;

/**
 * Saída sem chaves técnicas. O caso de uso local gera chaves, ordens e
 * associações somente depois de validar as evidências contra a fonte local.
 */
public record ArvoreInterpretadaDoEdital(
        ConcursoInterpretado concurso,
        EditalInterpretado edital,
        CargoInterpretado cargo) {

    public ArvoreInterpretadaDoEdital {
        if (concurso == null || edital == null || cargo == null) {
            throw new IllegalArgumentException(
                    "Concurso, edital e cargo interpretados sao obrigatorios.");
        }
    }

    public record EvidenciaInterpretada(Integer pagina, String trecho) {
        public EvidenciaInterpretada {
            if (pagina != null && pagina < 1) {
                throw new IllegalArgumentException(
                        "Pagina da evidencia deve ser positiva.");
            }
            trecho = limitar(trecho, 1_000);
        }
    }

    public record DadoTextualInterpretado(
            String valor, EvidenciaInterpretada evidencia) {
        public DadoTextualInterpretado {
            valor = limitar(valor, 4_000);
            evidencia = evidencia == null
                    ? new EvidenciaInterpretada(null, null) : evidencia;
        }
    }

    public record DadoInteiroInterpretado(
            Integer valor, EvidenciaInterpretada evidencia) {
        public DadoInteiroInterpretado {
            evidencia = evidencia == null
                    ? new EvidenciaInterpretada(null, null) : evidencia;
        }
    }

    public record DadoDecimalInterpretado(
            BigDecimal valor, EvidenciaInterpretada evidencia) {
        public DadoDecimalInterpretado {
            evidencia = evidencia == null
                    ? new EvidenciaInterpretada(null, null) : evidencia;
        }
    }

    public record ConcursoInterpretado(
            DadoTextualInterpretado nome,
            DadoTextualInterpretado descricao,
            DadoTextualInterpretado orgao,
            DadoTextualInterpretado banca) {
    }

    public record EditalInterpretado(
            DadoTextualInterpretado titulo,
            DadoTextualInterpretado numero,
            DadoInteiroInterpretado ano,
            DadoTextualInterpretado descricao) {
    }

    public record CargoInterpretado(
            DadoTextualInterpretado nome,
            DadoTextualInterpretado area,
            DadoTextualInterpretado especialidade,
            DadoTextualInterpretado nivelDeEscolaridade,
            List<ProvaInterpretada> provas) {

        public CargoInterpretado {
            provas = copiar(provas);
        }
    }

    public record ProvaInterpretada(
            DadoTextualInterpretado nome,
            DadoTextualInterpretado tipo,
            DadoTextualInterpretado carater,
            DadoTextualInterpretado dataHora,
            DadoInteiroInterpretado duracaoEmMinutos,
            DadoInteiroInterpretado quantidadeDeQuestoes,
            DadoDecimalInterpretado pontuacaoMaxima,
            DadoDecimalInterpretado pontuacaoMinima,
            List<GrupoInterpretado> grupos,
            List<MateriaInterpretada> materiasSemGrupo) {

        public ProvaInterpretada {
            grupos = copiar(grupos);
            materiasSemGrupo = copiar(materiasSemGrupo);
        }
    }

    public record GrupoInterpretado(
            DadoTextualInterpretado nome,
            DadoInteiroInterpretado quantidadeDeQuestoes,
            DadoDecimalInterpretado pontuacaoMaxima,
            DadoDecimalInterpretado pontuacaoMinima,
            List<MateriaInterpretada> materias) {

        public GrupoInterpretado {
            materias = copiar(materias);
        }
    }

    public record MateriaInterpretada(
            DadoTextualInterpretado nome,
            DadoTextualInterpretado descricao,
            DadoDecimalInterpretado peso,
            DadoInteiroInterpretado quantidadeDeQuestoes,
            DadoDecimalInterpretado pontuacaoMaxima,
            List<TopicoInterpretado> topicos,
            List<ItemInterpretado> itens) {

        public MateriaInterpretada {
            topicos = copiar(topicos);
            itens = copiar(itens);
        }
    }

    /**
     * numeroDoPai referencia o numero oficial de outro topico da mesma materia.
     * O backend resolve e valida a árvore, sem aceitar chaves do provedor.
     */
    public record TopicoInterpretado(
            DadoTextualInterpretado numeroOficial,
            DadoTextualInterpretado numeroDoPai,
            DadoTextualInterpretado nome,
            DadoTextualInterpretado descricao) {
    }

    public record ItemInterpretado(
            DadoTextualInterpretado numeroOficial,
            DadoTextualInterpretado numeroDoTopico,
            DadoTextualInterpretado descricaoLiteral) {
    }

    private static <T> List<T> copiar(List<T> valores) {
        return valores == null ? List.of() : List.copyOf(valores);
    }

    private static String limitar(String valor, int limite) {
        if (valor == null || valor.isBlank()) return null;
        String normalizado = valor.strip();
        return normalizado.length() <= limite
                ? normalizado : normalizado.substring(0, limite);
    }
}
