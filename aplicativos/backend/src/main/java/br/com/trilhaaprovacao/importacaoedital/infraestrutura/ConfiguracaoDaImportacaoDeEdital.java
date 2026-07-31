package br.com.trilhaaprovacao.importacaoedital.infraestrutura;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfiguracaoDaImportacaoDeEdital {
    private final long limiteEmBytes;
    private final int limiteDePaginas;
    private final int limiteDeCaracteresExtraidos;
    private final Duration retencaoDoConteudo;
    private final Duration timeoutDaExtracao;

    public ConfiguracaoDaImportacaoDeEdital(
            @Value("${trilha.importacao-de-edital.limite-em-bytes:10485760}")
            long limiteEmBytes,
            @Value("${trilha.importacao-de-edital.limite-de-paginas:500}")
            int limiteDePaginas,
            @Value("${trilha.importacao-de-edital.limite-de-caracteres-extraidos:2000000}")
            int limiteDeCaracteresExtraidos,
            @Value("${trilha.importacao-de-edital.retencao-do-conteudo:P30D}")
            Duration retencaoDoConteudo,
            @Value("${trilha.importacao-de-edital.timeout-da-extracao:PT30S}")
            Duration timeoutDaExtracao) {
        if (limiteEmBytes < 1 || limiteEmBytes > 10_485_760L
                || limiteDePaginas < 1 || limiteDePaginas > 500
                || limiteDeCaracteresExtraidos < 1
                || retencaoDoConteudo == null
                || retencaoDoConteudo.isZero()
                || retencaoDoConteudo.isNegative()
                || timeoutDaExtracao == null || timeoutDaExtracao.isZero()
                || timeoutDaExtracao.isNegative()) {
            throw new IllegalArgumentException(
                    "Configuracao da importacao de edital invalida.");
        }
        this.limiteEmBytes = limiteEmBytes;
        this.limiteDePaginas = limiteDePaginas;
        this.limiteDeCaracteresExtraidos = limiteDeCaracteresExtraidos;
        this.retencaoDoConteudo = retencaoDoConteudo;
        this.timeoutDaExtracao = timeoutDaExtracao;
    }

    public long limiteEmBytes() { return limiteEmBytes; }
    public int limiteDePaginas() { return limiteDePaginas; }
    public int limiteDeCaracteresExtraidos() {
        return limiteDeCaracteresExtraidos;
    }
    public Duration retencaoDoConteudo() { return retencaoDoConteudo; }
    public Duration timeoutDaExtracao() { return timeoutDaExtracao; }
}
