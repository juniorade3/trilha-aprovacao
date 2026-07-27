package br.com.trilhaaprovacao.importacaoedital.aplicacao;

public class FalhaNaInterpretacaoAssistidaDoEdital extends RuntimeException {
    private final Codigo codigo;

    public FalhaNaInterpretacaoAssistidaDoEdital(Codigo codigo,
            String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public FalhaNaInterpretacaoAssistidaDoEdital(Codigo codigo,
            String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.codigo = codigo;
    }

    public Codigo codigo() {
        return codigo;
    }

    public enum Codigo {
        IA_DESABILITADA,
        RECURSO_OCUPADO,
        FONTE_EXPIRADA,
        TEMPO_LIMITE_DA_IA,
        IA_INDISPONIVEL,
        RESPOSTA_RECUSADA_PELA_IA,
        RESPOSTA_INVALIDA_DA_IA
    }
}
