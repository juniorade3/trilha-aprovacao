package br.com.trilhaaprovacao.planejamento.dominio;

/** Falha de dominio usada quando a selecao automatica nao possui topico oficial valido. */
public final class SemTopicosElegiveisParaGeracao extends IllegalArgumentException {
    public SemTopicosElegiveisParaGeracao() {
        super("Nao ha topicos elegiveis para a geracao automatica.");
    }
}
