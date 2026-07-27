package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.CargoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.TipoDaFonteDoEdital;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;

@Service
public class ServicoDeInterpretacaoAssistidaDoEdital {
    private final ServicoDeStagingDaImportacaoDeEdital staging;
    private final InterpretadorAssistidoDoEdital interpretador;
    private final ConversorDaInterpretacaoAssistidaDoEdital conversor =
            new ConversorDaInterpretacaoAssistidaDoEdital();
    private final Semaphore operacaoEmAndamento = new Semaphore(1);

    public ServicoDeInterpretacaoAssistidaDoEdital(
            ServicoDeStagingDaImportacaoDeEdital staging,
            InterpretadorAssistidoDoEdital interpretador) {
        this.staging = staging;
        this.interpretador = interpretador;
    }

    public boolean disponivel() {
        return interpretador.disponivel();
    }

    public ResultadoDoStagingDaImportacao interpretar(UUID usuario,
            UUID identificador, int versaoEsperada,
            String chaveDoCargoAlvo, String descricaoDoCargoAlvo) {
        validarAlvo(chaveDoCargoAlvo, descricaoDoCargoAlvo);
        if (!interpretador.disponivel()) {
            throw new FalhaNaInterpretacaoAssistidaDoEdital(
                    FalhaNaInterpretacaoAssistidaDoEdital.Codigo.IA_DESABILITADA,
                    "A interpretacao assistida nao esta habilitada.");
        }
        if (!operacaoEmAndamento.tryAcquire()) {
            throw new FalhaNaInterpretacaoAssistidaDoEdital(
                    FalhaNaInterpretacaoAssistidaDoEdital.Codigo.RECURSO_OCUPADO,
                    "Ja existe uma interpretacao assistida em andamento.");
        }
        try {
            return interpretarExclusivamente(usuario, identificador,
                    versaoEsperada, chaveDoCargoAlvo,
                    descricaoDoCargoAlvo);
        } finally {
            operacaoEmAndamento.release();
        }
    }

    private ResultadoDoStagingDaImportacao interpretarExclusivamente(
            UUID usuario, UUID identificador, int versaoEsperada,
            String chaveDoCargoAlvo, String descricaoDoCargoAlvo) {
        FonteRetidaDaImportacaoDoEdital fonte = staging.obterFonteRetida(
                usuario, identificador, versaoEsperada);
        String descricao = descricaoDoCargoAlvo;
        String chaveExistente = null;
        if (chaveDoCargoAlvo != null && !chaveDoCargoAlvo.isBlank()) {
            CargoExtraido cargo = fonte.extracaoAtual().cargos().stream()
                    .filter(item -> chaveDoCargoAlvo.equals(item.chave()))
                    .findFirst()
                    .orElseThrow(() -> new RegraDeDominio(
                            "CARGO_ALVO_INVALIDO",
                            "O cargo alvo nao pertence a extracao atual."));
            chaveExistente = cargo.chave();
            descricao = descrever(cargo);
        }
        String textoParaOModelo = fonte.tipoDaFonte()
                == TipoDaFonteDoEdital.TEXTO
                ? fonte.textoExtraido() : null;
        ResultadoDaInterpretacaoAssistidaDoEdital resultado =
                interpretador.interpretar(
                        new SolicitacaoDeInterpretacaoAssistidaDoEdital(
                                fonte.tipoDaFonte(), fonte.nomeDoArquivo(),
                                fonte.conteudoOriginal(), textoParaOModelo,
                                descricao));
        var conversao = conversor.converter(fonte.extracaoAtual(),
                resultado.arvore(), chaveExistente, fonte.textoExtraido());
        return staging.registrarInterpretacaoAssistida(usuario,
                identificador, versaoEsperada, conversao.extracao(),
                conversao.problemasAdicionais());
    }

    private static void validarAlvo(String chave, String descricao) {
        boolean possuiChave = chave != null && !chave.isBlank();
        boolean possuiDescricao = descricao != null
                && !descricao.isBlank();
        if (possuiChave == possuiDescricao) {
            throw new RegraDeDominio("ALVO_DA_IA_INVALIDO",
                    "Informe exatamente um cargo existente ou uma descricao.");
        }
        if (possuiDescricao && descricao.strip().length() > 1_000) {
            throw new RegraDeDominio("ALVO_DA_IA_INVALIDO",
                    "A descricao do cargo excede o limite permitido.");
        }
    }

    private static String descrever(CargoExtraido cargo) {
        List<String> partes = new ArrayList<>();
        adicionar(partes, valor(cargo.nome()));
        adicionar(partes, valor(cargo.area()));
        adicionar(partes, valor(cargo.especialidade()));
        if (cargo.nivelDeEscolaridade() != null
                && cargo.nivelDeEscolaridade().valor() != null) {
            partes.add("Escolaridade: "
                    + cargo.nivelDeEscolaridade().valor().name());
        }
        return String.join(" — ", partes);
    }

    private static String valor(
            br.com.trilhaaprovacao.importacaoedital.dominio.ValorExtraido<String>
                    valor) {
        return valor == null ? null : valor.valor();
    }

    private static void adicionar(List<String> partes, String valor) {
        if (valor != null && !valor.isBlank()) partes.add(valor.strip());
    }
}
