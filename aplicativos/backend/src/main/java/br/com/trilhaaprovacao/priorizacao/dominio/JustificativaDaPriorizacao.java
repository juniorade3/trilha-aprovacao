package br.com.trilhaaprovacao.priorizacao.dominio;

public enum JustificativaDaPriorizacao {
    TOPICO_NUNCA_ESTUDADO("O topico ainda nao possui estudo ativo."),
    ESTUDO_SEM_EVIDENCIA("O topico possui estudo, mas ainda nao possui evidencia de aprendizagem."),
    EVIDENCIA_FORA_DA_JANELA_RECENTE("A evidencia mais recente esta fora da janela de 30 dias."),
    QUESTOES_RECENTES_INSUFICIENTES("Foram registradas menos de 20 questoes nos ultimos 30 dias."),
    PERCENTUAL_RECENTE_ABAIXO_DE_SETENTA("O percentual recente de acertos esta abaixo de 70%."),
    PERCENTUAL_RECENTE_ENTRE_SETENTA_E_OITENTA_E_CINCO(
            "O percentual recente de acertos esta entre 70% e menos de 85%."),
    PERCENTUAL_RECENTE_A_PARTIR_DE_OITENTA_E_CINCO(
            "O percentual recente de acertos e de pelo menos 85%."),
    RECORDACAO_RECENTE_BAIXA("A revisao recente registrou recordacao entre 1 e 2."),
    RECORDACAO_RECENTE_PARCIAL("A revisao recente registrou recordacao igual a 3."),
    RECORDACAO_RECENTE_ALTA("A revisao recente registrou recordacao entre 4 e 5."),
    PADRAO_DE_ERRO_REPETIDO("Ha padrao de erro repetido com ocorrencia nos ultimos 30 dias."),
    DIFICULDADE_PERCEBIDA_ALTA("A dificuldade percebida mais recente esta entre 4 e 5."),
    SEM_MATERIAL_ATIVO("O topico nao possui cobertura por material de estudo ativo.");

    private final String mensagem;

    JustificativaDaPriorizacao(String mensagem) {
        this.mensagem = mensagem;
    }

    public String mensagem() {
        return mensagem;
    }
}
