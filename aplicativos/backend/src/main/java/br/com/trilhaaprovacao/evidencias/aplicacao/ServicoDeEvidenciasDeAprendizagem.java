package br.com.trilhaaprovacao.evidencias.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.estudos.dominio.TipoDeEstudo;
import br.com.trilhaaprovacao.evidencias.dominio.EvidenciaDeAprendizagem;
import br.com.trilhaaprovacao.evidencias.infraestrutura.EvidenciaDeAprendizagemPersistida;
import br.com.trilhaaprovacao.evidencias.infraestrutura.OcorrenciaDePadraoDeErroPersistida;
import br.com.trilhaaprovacao.evidencias.infraestrutura.PadraoDeErroPersistido;
import br.com.trilhaaprovacao.evidencias.infraestrutura.RepositorioDeEvidencias;
import br.com.trilhaaprovacao.evidencias.infraestrutura.RepositorioDeOcorrenciasDePadroes;
import br.com.trilhaaprovacao.evidencias.infraestrutura.RepositorioDePadroesDeErro;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ServicoDeEvidenciasDeAprendizagem {
    private final RepositorioDeEvidencias evidencias;
    private final RepositorioDePadroesDeErro padroes;
    private final RepositorioDeOcorrenciasDePadroes ocorrencias;

    public ServicoDeEvidenciasDeAprendizagem(RepositorioDeEvidencias evidencias,
            RepositorioDePadroesDeErro padroes,
            RepositorioDeOcorrenciasDePadroes ocorrencias) {
        this.evidencias = evidencias;
        this.padroes = padroes;
        this.ocorrencias = ocorrencias;
    }

    public EvidenciaDeAprendizagem registrar(UUID usuario, UUID topico, UUID registro,
            TipoDeEstudo tipo, DadosDaEvidencia dados, boolean exigirResultado) {
        validarObrigatoriedade(tipo, dados, exigirResultado);
        if (dados == null) {
            return null;
        }
        EvidenciaDeAprendizagem evidencia;
        try {
            evidencia = EvidenciaDeAprendizagem.criar(registro,
                    dados.quantidadeDeQuestoes(), dados.quantidadeDeAcertos(),
                    dados.nivelDeRecordacao(), dados.dificuldadePercebida(),
                    dados.padroesDeErro());
        } catch (IllegalArgumentException excecao) {
            throw new RegraDeDominio("EVIDENCIA_INVALIDA", excecao.getMessage());
        }
        evidencias.saveAndFlush(new EvidenciaDeAprendizagemPersistida(evidencia));
        for (var dadoDoPadrao : evidencia.padroesDeErro()) {
            padroes.inserirSeAusente(UUID.randomUUID(), usuario, topico,
                    dadoDoPadrao.descricao(), dadoDoPadrao.descricaoNormalizada());
            PadraoDeErroPersistido padrao = padroes
                    .findByIdentificadorDoUsuarioAndIdentificadorDoTopicoAndDescricaoNormalizada(
                            usuario, topico, dadoDoPadrao.descricaoNormalizada())
                    .orElseThrow(() -> new IllegalStateException(
                            "O padrao de erro nao foi persistido."));
            ocorrencias.save(new OcorrenciaDePadraoDeErroPersistida(
                    evidencia.identificador(), padrao.identificador(),
                    dadoDoPadrao.quantidadeDeOcorrencias()));
        }
        return evidencia;
    }

    public Optional<EvidenciaDeAprendizagem> obterPorRegistro(UUID registro) {
        return evidencias.findByIdentificadorDoRegistroDeEstudo(registro)
                .map(persistida -> persistida.paraDominio(
                        ocorrencias.listarDaEvidencia(persistida.identificador())));
    }

    public List<String> sugerirPadroes(UUID usuario, UUID topico, String pesquisa) {
        return padroes
                .findTop20ByIdentificadorDoUsuarioAndIdentificadorDoTopicoAndDescricaoContainingIgnoreCaseOrderByDescricaoAsc(
                        usuario, topico, pesquisa == null ? "" : pesquisa.trim())
                .stream().map(PadraoDeErroPersistido::descricao).toList();
    }

    private void validarObrigatoriedade(
            TipoDeEstudo tipo, DadosDaEvidencia dados, boolean exigirResultado) {
        if (!exigirResultado) {
            return;
        }
        if (tipo.exigeResultadoDeQuestoes()
                && (dados == null || dados.quantidadeDeQuestoes() == null
                || dados.quantidadeDeAcertos() == null)) {
            throw new RegraDeDominio("RESULTADO_DE_QUESTOES_OBRIGATORIO",
                    "Informe a quantidade de questoes e de acertos.");
        }
        if (tipo.exigeRecordacao()
                && (dados == null || dados.nivelDeRecordacao() == null)) {
            throw new RegraDeDominio("RECORDACAO_OBRIGATORIA",
                    "Informe o nivel de recordacao da revisao.");
        }
    }
}
