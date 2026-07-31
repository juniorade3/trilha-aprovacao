package br.com.trilhaaprovacao.importacaoedital.api;

import br.com.trilhaaprovacao.autenticacao.aplicacao.IdentidadeDoUsuarioAtual;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.importacaoedital.api.RespostaDaImportacaoDeEdital.RespostaDaPreparacao;
import br.com.trilhaaprovacao.importacaoedital.api.RespostaDaImportacaoDeEdital.RespostaDaPrevia;
import br.com.trilhaaprovacao.importacaoedital.api.RespostaDaImportacaoDeEdital.RespostaDoRelatorio;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.DecisoesDaImportacaoDoEdital;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ConfirmacaoDeCampoDaExtracao;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.FalhaNaExtracaoDoEdital;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.PreparadorDaImportacaoCompletaDoEdital.SolicitacaoDePreparacaoDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ServicoDePreparacaoDaImportacaoCompletaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ServicoDeStagingDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ServicoDeInterpretacaoAssistidaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.EstadoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ModoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.PoliticaDeReutilizacao;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/importacoes-de-edital")
@Tag(name = "Importacoes de edital")
public class ControladorDeImportacoesDeEdital {
    private final ServicoDeStagingDaImportacaoDeEdital staging;
    private final ServicoDePreparacaoDaImportacaoCompletaDoEdital preparacao;
    private final ServicoDeInterpretacaoAssistidaDoEdital interpretacao;
    private final IdentidadeDoUsuarioAtual usuarioAtual;

    public ControladorDeImportacoesDeEdital(
            ServicoDeStagingDaImportacaoDeEdital staging,
            ServicoDePreparacaoDaImportacaoCompletaDoEdital preparacao,
            ServicoDeInterpretacaoAssistidaDoEdital interpretacao,
            IdentidadeDoUsuarioAtual usuarioAtual) {
        this.staging = staging;
        this.preparacao = preparacao;
        this.interpretacao = interpretacao;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RespostaDaImportacaoDeEdital> receberArquivo(
            @RequestPart("arquivo") MultipartFile arquivo,
            @RequestParam @NotNull ModoDaImportacaoDeEdital modo,
            @RequestParam(required = false)
            UUID identificadorDoConcursoExistente,
            Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        preparacao.validarDestino(usuario, modo,
                identificadorDoConcursoExistente);
        byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException excecao) {
            throw new FalhaNaExtracaoDoEdital("ARQUIVO_ILEGIVEL",
                    "Nao foi possivel ler o arquivo recebido.", excecao);
        }
        var importacao = staging.receber(usuario,
                arquivo.getOriginalFilename(), conteudo);
        preparacao.registrarDestinoInicial(usuario,
                importacao.identificador(), modo,
                identificadorDoConcursoExistente);
        extrairSeNecessario(usuario, importacao.identificador(),
                importacao.estado(), importacao.versaoAtualDaExtracao());
        RespostaDaImportacaoDeEdital resposta = RespostaDaImportacaoDeEdital
                .de(preparacao.consultar(usuario, importacao.identificador()),
                        interpretacao.disponivel());
        return ResponseEntity.created(URI.create(
                "/api/v1/importacoes-de-edital/" + importacao.identificador()))
                .body(resposta);
    }

    @PostMapping(path = "/textos",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RespostaDaImportacaoDeEdital> receberTexto(
            @Valid @RequestBody RequisicaoDeTexto requisicao,
            Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        preparacao.validarDestino(usuario, requisicao.modo(),
                requisicao.identificadorDoConcursoExistente());
        var importacao = staging.receber(usuario, requisicao.nomeDaFonte(),
                requisicao.texto().getBytes(StandardCharsets.UTF_8));
        preparacao.registrarDestinoInicial(usuario,
                importacao.identificador(), requisicao.modo(),
                requisicao.identificadorDoConcursoExistente());
        extrairSeNecessario(usuario, importacao.identificador(),
                importacao.estado(), importacao.versaoAtualDaExtracao());
        RespostaDaImportacaoDeEdital resposta = RespostaDaImportacaoDeEdital
                .de(preparacao.consultar(usuario, importacao.identificador()),
                        interpretacao.disponivel());
        return ResponseEntity.created(URI.create(
                "/api/v1/importacoes-de-edital/" + importacao.identificador()))
                .body(resposta);
    }

    @GetMapping("/{identificador}")
    public RespostaDaImportacaoDeEdital obter(
            @PathVariable UUID identificador,
            Authentication autenticacao) {
        return RespostaDaImportacaoDeEdital.de(preparacao.consultar(
                usuario(autenticacao), identificador),
                interpretacao.disponivel());
    }

    @PutMapping("/{identificador}/decisoes")
    public RespostaDaImportacaoDeEdital registrarDecisoes(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeDecisoes requisicao,
            Authentication autenticacao) {
        if (!requisicao.decisoesHumanas().isEmpty()) {
            throw new RegraDeDominio("CORRECAO_ESTRUTURADA_NECESSARIA",
                    "Corrija os campos da extracao; texto livre nao altera "
                            + "dados extraidos.");
        }
        UUID usuario = usuario(autenticacao);
        var selecionada = preparacao.registrarDecisoes(usuario, identificador,
                requisicao.chaveDoCargoSelecionado(), requisicao.modo(),
                requisicao.identificadorDoConcursoExistente(),
                requisicao.politicaDeReutilizacao(),
                requisicao.versaoDaExtracao());
        return RespostaDaImportacaoDeEdital.de(
                preparacao.consultar(usuario,
                        selecionada.importacao().identificador()),
                interpretacao.disponivel());
    }

    @PostMapping("/{identificador}/preparacao")
    public RespostaDaPreparacao previsualizar(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeDecisoes requisicao,
            Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        if (preparacao.consultar(usuario, identificador).staging()
                .importacao().versaoAtualDaExtracao()
                != requisicao.versaoDaExtracao()) {
            throw new br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio(
                    "EXTRACAO_DA_IMPORTACAO_DESATUALIZADA",
                    "A extracao mudou; revise novamente as decisoes.");
        }
        var previa = preparacao.previsualizar(
                new SolicitacaoDePreparacaoDaImportacao(usuario,
                        identificador,
                        requisicao.chaveDoCargoSelecionado(),
                        requisicao.modo(),
                        requisicao.identificadorDoConcursoExistente(),
                        requisicao.politicaDeReutilizacao(),
                        new DecisoesDaImportacaoDoEdital(
                                requisicao.recursosParaReutilizar(),
                                requisicao.definirEditalComoPrincipal(),
                                requisicao.selecionarCargoCriado())));
        RespostaDaPrevia respostaDaPrevia = RespostaDaPrevia.de(previa);
        var respostaDaImportacao = RespostaDaImportacaoDeEdital.de(
                preparacao.consultar(usuario, identificador),
                respostaDaPrevia, interpretacao.disponivel());
        return new RespostaDaPreparacao(respostaDaImportacao,
                respostaDaPrevia);
    }

    @PostMapping("/{identificador}/nova-tentativa")
    public RespostaDaImportacaoDeEdital iniciarNovaTentativa(
            @PathVariable UUID identificador,
            Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        preparacao.iniciarNovaTentativa(usuario, identificador);
        return RespostaDaImportacaoDeEdital.de(
                preparacao.consultar(usuario, identificador),
                interpretacao.disponivel());
    }

    @PutMapping("/{identificador}/extracao")
    public RespostaDaImportacaoDeEdital corrigirExtracao(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeCorrecaoDaExtracao requisicao,
            Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        staging.registrarCorrecaoManual(usuario, identificador,
                requisicao.versaoEsperada(), requisicao.extracao(),
                requisicao.confirmacoesDeCampos().stream()
                        .map(confirmacao -> new ConfirmacaoDeCampoDaExtracao(
                                confirmacao.tipoDoRecurso(),
                                confirmacao.chaveDoRecurso(),
                                confirmacao.campo()))
                        .toList());
        return RespostaDaImportacaoDeEdital.de(
                preparacao.consultar(usuario, identificador),
                interpretacao.disponivel());
    }

    @PostMapping("/{identificador}/extracao-assistida")
    public RespostaDaImportacaoDeEdital interpretarExtracao(
            @PathVariable UUID identificador,
            @Valid @RequestBody RequisicaoDeInterpretacaoAssistida requisicao,
            Authentication autenticacao) {
        UUID usuario = usuario(autenticacao);
        interpretacao.interpretar(usuario, identificador,
                requisicao.versaoEsperada(),
                requisicao.chaveDoCargoAlvo(),
                requisicao.descricaoDoCargoAlvo());
        return RespostaDaImportacaoDeEdital.de(
                preparacao.consultar(usuario, identificador),
                interpretacao.disponivel());
    }

    @GetMapping("/{identificador}/relatorio")
    public RespostaDoRelatorio obterRelatorio(
            @PathVariable UUID identificador,
            Authentication autenticacao) {
        return RespostaDoRelatorio.de(preparacao.obterRelatorio(
                usuario(autenticacao), identificador));
    }

    private void extrairSeNecessario(UUID usuario, UUID importacao,
            EstadoDaImportacaoDeEdital estado, int versao) {
        if (versao == 0 && estado == EstadoDaImportacaoDeEdital.RECEBIDA) {
            staging.extrair(usuario, importacao);
        }
    }

    private UUID usuario(Authentication autenticacao) {
        return usuarioAtual.obter(autenticacao);
    }

    public record RequisicaoDeTexto(
            @NotBlank @Size(max = 2_000_000) String texto,
            @NotBlank @Size(max = 255) String nomeDaFonte,
            @NotNull ModoDaImportacaoDeEdital modo,
            UUID identificadorDoConcursoExistente) {
    }

    public record RequisicaoDeDecisoes(
            @NotBlank @Size(max = 160) String chaveDoCargoSelecionado,
            @NotNull ModoDaImportacaoDeEdital modo,
            UUID identificadorDoConcursoExistente,
            @NotNull PoliticaDeReutilizacao politicaDeReutilizacao,
            @Min(1) int versaoDaExtracao,
            @Size(max = 1_000) Map<String, String> decisoesHumanas,
            @Size(max = 1_000) Map<String, UUID> recursosParaReutilizar,
            boolean definirEditalComoPrincipal,
            boolean selecionarCargoCriado) {

        public RequisicaoDeDecisoes {
            decisoesHumanas = decisoesHumanas == null
                    ? Map.of() : Map.copyOf(decisoesHumanas);
            recursosParaReutilizar = recursosParaReutilizar == null
                    ? Map.of() : Map.copyOf(recursosParaReutilizar);
        }
    }

    public record RequisicaoDeCorrecaoDaExtracao(
            @Min(1) int versaoEsperada,
            @NotNull @Valid ExtracaoEstruturadaDoEdital extracao,
            @Size(max = 500)
            List<@Valid RequisicaoDeConfirmacaoDeCampo>
                    confirmacoesDeCampos) {

        public RequisicaoDeCorrecaoDaExtracao {
            confirmacoesDeCampos = confirmacoesDeCampos == null
                    ? List.of() : List.copyOf(confirmacoesDeCampos);
        }
    }

    public record RequisicaoDeConfirmacaoDeCampo(
            @NotBlank @Size(max = 40) String tipoDoRecurso,
            @NotBlank @Size(max = 160) String chaveDoRecurso,
            @NotBlank @Size(max = 80) String campo) {
    }

    public record RequisicaoDeInterpretacaoAssistida(
            @Min(1) int versaoEsperada,
            @Size(max = 160) String chaveDoCargoAlvo,
            @Size(max = 1_000) String descricaoDoCargoAlvo) {
    }
}
