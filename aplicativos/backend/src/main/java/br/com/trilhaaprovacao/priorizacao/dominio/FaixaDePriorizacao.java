package br.com.trilhaaprovacao.priorizacao.dominio;

public enum FaixaDePriorizacao {
    SEM_ESTUDO(GrupoDePriorizacao.LACUNA, 0, AcaoSugerida.TEORIA),
    SEM_EVIDENCIA(GrupoDePriorizacao.LACUNA, 1, AcaoSugerida.QUESTOES),
    EVIDENCIA_DESATUALIZADA(GrupoDePriorizacao.LACUNA, 2, AcaoSugerida.QUESTOES),
    DADOS_INSUFICIENTES(GrupoDePriorizacao.LACUNA, 3, AcaoSugerida.QUESTOES),
    PRECISA_REFORCO(GrupoDePriorizacao.FRAQUEZA, 0, AcaoSugerida.QUESTOES),
    DESEMPENHO_PARCIAL(GrupoDePriorizacao.FRAQUEZA, 1, AcaoSugerida.QUESTOES),
    CONSOLIDADO(GrupoDePriorizacao.CONSOLIDADO, 0, AcaoSugerida.QUESTOES);

    private final GrupoDePriorizacao grupo;
    private final int ordem;
    private final AcaoSugerida acaoSugerida;

    FaixaDePriorizacao(GrupoDePriorizacao grupo, int ordem,
            AcaoSugerida acaoSugerida) {
        this.grupo = grupo;
        this.ordem = ordem;
        this.acaoSugerida = acaoSugerida;
    }

    public GrupoDePriorizacao grupo() {
        return grupo;
    }

    public int ordem() {
        return ordem;
    }

    public AcaoSugerida acaoSugerida() {
        return acaoSugerida;
    }
}
