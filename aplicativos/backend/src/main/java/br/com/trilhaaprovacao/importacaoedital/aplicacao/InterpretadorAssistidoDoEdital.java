package br.com.trilhaaprovacao.importacaoedital.aplicacao;

/**
 * Porta opcional para interpretar um único cargo sem permitir que o provedor
 * externo altere o staging ou acesse ferramentas da aplicação.
 */
public interface InterpretadorAssistidoDoEdital {

    boolean disponivel();

    ResultadoDaInterpretacaoAssistidaDoEdital interpretar(
            SolicitacaoDeInterpretacaoAssistidaDoEdital solicitacao);
}
