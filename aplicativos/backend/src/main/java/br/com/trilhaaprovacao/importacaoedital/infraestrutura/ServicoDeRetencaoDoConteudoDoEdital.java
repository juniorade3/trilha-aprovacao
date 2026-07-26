package br.com.trilhaaprovacao.importacaoedital.infraestrutura;

import java.time.OffsetDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoDeRetencaoDoConteudoDoEdital {
    private final RepositorioDeImportacoesDeEdital importacoes;

    public ServicoDeRetencaoDoConteudoDoEdital(
            RepositorioDeImportacoesDeEdital importacoes) {
        this.importacoes = importacoes;
    }

    @Scheduled(fixedDelayString =
            "${trilha.importacao-de-edital.intervalo-da-limpeza:PT1H}")
    @Transactional
    public void executarAgendamento() {
        executar(OffsetDateTime.now(), 100);
    }

    @Transactional
    public int executar(OffsetDateTime agora, int limite) {
        if (agora == null || limite < 1 || limite > 1_000) {
            throw new IllegalArgumentException("Parametros da retencao invalidos.");
        }
        var expiradas = importacoes.encontrarConteudosExpirados(agora,
                PageRequest.of(0, limite));
        expiradas.forEach(ImportacaoDeEditalPersistida::descartarConteudoRetido);
        return expiradas.size();
    }
}
