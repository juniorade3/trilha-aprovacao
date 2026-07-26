package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.importacaoedital.dominio.ProblemaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.SeveridadeDoProblemaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.TipoDaFonteDoEdital;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.ConfiguracaoDaImportacaoDeEdital;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

@Service
public class ServicoDeExtracaoDoArquivoDoEdital {
    private static final byte[] CABECALHO_PDF = "%PDF-".getBytes(
            StandardCharsets.US_ASCII);

    private final ConfiguracaoDaImportacaoDeEdital configuracao;
    private final Optional<ServicoDeOcrDoEdital> ocr;
    private final Optional<VerificadorAntimalwareDoEdital> antimalware;
    private final ExecutorService executor = Executors.newFixedThreadPool(2,
            Thread.ofPlatform().daemon().name("extracao-edital-", 0).factory());

    public ServicoDeExtracaoDoArquivoDoEdital(
            ConfiguracaoDaImportacaoDeEdital configuracao,
            ObjectProvider<ServicoDeOcrDoEdital> ocr,
            ObjectProvider<VerificadorAntimalwareDoEdital> antimalware) {
        this.configuracao = configuracao;
        this.ocr = Optional.ofNullable(ocr.getIfAvailable());
        this.antimalware = Optional.ofNullable(antimalware.getIfAvailable());
    }

    public InspecaoDoArquivoDoEdital inspecionar(String nome, byte[] conteudo) {
        validarTamanho(conteudo);
        boolean pdf = possuiCabecalhoPdf(conteudo);
        if (!pdf) {
            decodificarTexto(conteudo);
        }
        List<ProblemaDaImportacao> problemas = verificarAntimalware(conteudo);
        return new InspecaoDoArquivoDoEdital(sanitizarNome(nome),
                pdf ? TipoDaFonteDoEdital.PDF_TEXTUAL
                        : TipoDaFonteDoEdital.TEXTO,
                pdf ? "application/pdf" : "text/plain", sha256(conteudo),
                conteudo.length, problemas);
    }

    public ResultadoDaExtracaoDoArquivo extrair(byte[] conteudo) {
        validarTamanho(conteudo);
        boolean pdf = possuiCabecalhoPdf(conteudo);
        if (!pdf) {
            String texto = normalizarQuebras(decodificarTexto(conteudo));
            validarQuantidadeDeCaracteres(texto);
            return new ResultadoDaExtracaoDoArquivo(
                    TipoDaFonteDoEdital.TEXTO, texto, paginas(texto),
                    avisoAntimalware());
        }
        Future<ResultadoDaExtracaoDoArquivo> tarefa = executor.submit(
                () -> extrairPdf(conteudo));
        try {
            ResultadoDaExtracaoDoArquivo resultado = tarefa.get(
                    configuracao.timeoutDaExtracao().toMillis(),
                    TimeUnit.MILLISECONDS);
            List<ProblemaDaImportacao> problemas = new ArrayList<>(
                    avisoAntimalware());
            problemas.addAll(resultado.problemas());
            return new ResultadoDaExtracaoDoArquivo(resultado.tipoDaFonte(),
                    resultado.texto(), resultado.quantidadeDePaginas(),
                    problemas);
        } catch (TimeoutException excecao) {
            tarefa.cancel(true);
            throw new FalhaNaExtracaoDoEdital("TEMPO_DA_EXTRACAO_EXCEDIDO",
                    "PDF excedeu o tempo maximo de extracao.", excecao);
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw new FalhaNaExtracaoDoEdital("EXTRACAO_INTERROMPIDA",
                    "Extracao do PDF foi interrompida.", excecao);
        } catch (ExecutionException excecao) {
            if (excecao.getCause() instanceof FalhaNaExtracaoDoEdital falha) {
                throw falha;
            }
            throw new FalhaNaExtracaoDoEdital("PDF_INVALIDO",
                    "Nao foi possivel ler o PDF.", excecao.getCause());
        }
    }

    private ResultadoDaExtracaoDoArquivo extrairPdf(byte[] conteudo) {
        try (PDDocument documento = Loader.loadPDF(conteudo, "", null, null,
                IOUtils.createMemoryOnlyStreamCache())) {
            if (documento.isEncrypted()) {
                throw new FalhaNaExtracaoDoEdital("PDF_PROTEGIDO",
                        "PDF protegido por senha nao pode ser importado.");
            }
            int paginas = documento.getNumberOfPages();
            if (paginas < 1 || paginas > configuracao.limiteDePaginas()) {
                throw new FalhaNaExtracaoDoEdital("LIMITE_DE_PAGINAS_EXCEDIDO",
                        "PDF excede o limite de paginas.");
            }
            String texto = normalizarQuebras(
                    new PDFTextStripper().getText(documento));
            validarQuantidadeDeCaracteres(texto);
            long caracteresVisiveis = texto.codePoints()
                    .filter(codigo -> !Character.isWhitespace(codigo)).count();
            if (caracteresVisiveis < 10) {
                if (ocr.isPresent()) {
                    String textoDoOcr;
                    try {
                        textoDoOcr = ocr.orElseThrow().extrairTexto(conteudo);
                    } catch (RuntimeException excecao) {
                        throw new FalhaNaExtracaoDoEdital("OCR_FALHOU",
                                "Servico de OCR falhou.", excecao);
                    }
                    if (textoDoOcr == null) textoDoOcr = "";
                    textoDoOcr = normalizarQuebras(textoDoOcr);
                    validarQuantidadeDeCaracteres(textoDoOcr);
                    if (textoDoOcr.codePoints().filter(codigo ->
                            !Character.isWhitespace(codigo)).count() >= 10) {
                        return new ResultadoDaExtracaoDoArquivo(
                                TipoDaFonteDoEdital.PDF_DIGITALIZADO,
                                textoDoOcr, paginas, List.of());
                    }
                }
                return new ResultadoDaExtracaoDoArquivo(
                        TipoDaFonteDoEdital.PDF_DIGITALIZADO, null, paginas,
                        List.of(new ProblemaDaImportacao(
                                SeveridadeDoProblemaDaImportacao.BLOQUEANTE,
                                "OCR_INDISPONIVEL",
                                "PDF sem camada textual; OCR nao esta disponivel.",
                                "fonte")));
            }
            return new ResultadoDaExtracaoDoArquivo(
                    TipoDaFonteDoEdital.PDF_TEXTUAL, texto, paginas,
                    List.of());
        } catch (FalhaNaExtracaoDoEdital excecao) {
            throw excecao;
        } catch (InvalidPasswordException excecao) {
            throw new FalhaNaExtracaoDoEdital("PDF_PROTEGIDO",
                    "PDF protegido por senha nao pode ser importado.", excecao);
        } catch (IOException | RuntimeException excecao) {
            throw new FalhaNaExtracaoDoEdital("PDF_INVALIDO",
                    "Nao foi possivel ler o PDF.", excecao);
        }
    }

    private void validarTamanho(byte[] conteudo) {
        if (conteudo == null || conteudo.length == 0) {
            throw new FalhaNaExtracaoDoEdital("ARQUIVO_VAZIO",
                    "Arquivo do edital esta vazio.");
        }
        if (conteudo.length > configuracao.limiteEmBytes()) {
            throw new FalhaNaExtracaoDoEdital("ARQUIVO_MUITO_GRANDE",
                    "Arquivo do edital excede o limite permitido.");
        }
    }

    private void validarQuantidadeDeCaracteres(String texto) {
        if (texto == null) return;
        if (texto.length() > configuracao.limiteDeCaracteresExtraidos()) {
            throw new FalhaNaExtracaoDoEdital(
                    "LIMITE_DE_CARACTERES_EXCEDIDO",
                    "Texto extraido excede o limite permitido.");
        }
    }

    private List<ProblemaDaImportacao> verificarAntimalware(byte[] conteudo) {
        if (antimalware.isEmpty()) {
            return avisoAntimalware();
        }
        final VerificadorAntimalwareDoEdital.ResultadoDaVerificacao resultado;
        try {
            resultado = antimalware.orElseThrow().verificar(conteudo);
        } catch (RuntimeException excecao) {
            throw new FalhaNaExtracaoDoEdital(
                    "VERIFICACAO_ANTIMALWARE_FALHOU",
                    "Verificacao antimalware falhou.", excecao);
        }
        if (resultado == null || !resultado.seguro()) {
            throw new FalhaNaExtracaoDoEdital("ARQUIVO_REPROVADO_PELO_ANTIMALWARE",
                    "Arquivo reprovado pelo verificador antimalware.");
        }
        return List.of();
    }

    private List<ProblemaDaImportacao> avisoAntimalware() {
        if (antimalware.isPresent()) return List.of();
        return List.of(new ProblemaDaImportacao(
                SeveridadeDoProblemaDaImportacao.AVISO,
                "VERIFICACAO_ANTIMALWARE_INDISPONIVEL",
                "Arquivo nao foi varrido: verificador antimalware indisponivel.",
                "fonte"));
    }

    private static String decodificarTexto(byte[] conteudo) {
        try {
            String texto = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(conteudo)).toString();
            boolean controleInvalido = texto.codePoints().anyMatch(codigo ->
                    Character.isISOControl(codigo) && codigo != '\n'
                            && codigo != '\r' && codigo != '\t'
                            && codigo != '\f');
            if (controleInvalido) {
                throw new FalhaNaExtracaoDoEdital("TIPO_DE_ARQUIVO_INVALIDO",
                        "Somente TXT UTF-8 ou PDF e aceito.");
            }
            return texto.startsWith("\uFEFF") ? texto.substring(1) : texto;
        } catch (CharacterCodingException excecao) {
            throw new FalhaNaExtracaoDoEdital("TIPO_DE_ARQUIVO_INVALIDO",
                    "Somente TXT UTF-8 ou PDF e aceito.", excecao);
        }
    }

    static String sanitizarNome(String nome) {
        String informado = nome == null ? "" : nome.replace('\\', '/');
        informado = informado.substring(informado.lastIndexOf('/') + 1);
        StringBuilder seguro = new StringBuilder();
        informado.codePoints().filter(codigo -> !Character.isISOControl(codigo))
                .forEach(seguro::appendCodePoint);
        String resultado = seguro.toString().strip();
        if (resultado.isEmpty() || resultado.equals(".")
                || resultado.equals("..")) {
            resultado = "edital";
        }
        return resultado.length() <= 255 ? resultado
                : resultado.substring(0, 255);
    }

    static String sha256(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (NoSuchAlgorithmException excecao) {
            throw new IllegalStateException("SHA-256 indisponivel.", excecao);
        }
    }

    private static boolean possuiCabecalhoPdf(byte[] conteudo) {
        int limite = Math.min(conteudo.length - CABECALHO_PDF.length, 1024);
        for (int inicio = 0; inicio <= limite; inicio++) {
            boolean igual = true;
            for (int indice = 0; indice < CABECALHO_PDF.length; indice++) {
                igual &= conteudo[inicio + indice] == CABECALHO_PDF[indice];
            }
            if (igual) return true;
        }
        return false;
    }

    private static String normalizarQuebras(String texto) {
        return texto.replace("\r\n", "\n").replace('\r', '\n');
    }

    private int paginas(String texto) {
        int paginas = 1;
        for (int indice = 0; indice < texto.length(); indice++) {
            if (texto.charAt(indice) == '\f') paginas++;
        }
        if (paginas > configuracao.limiteDePaginas()) {
            throw new FalhaNaExtracaoDoEdital("LIMITE_DE_PAGINAS_EXCEDIDO",
                    "Texto excede o limite de paginas logicas.");
        }
        return paginas;
    }

    @PreDestroy
    void encerrar() {
        executor.shutdownNow();
    }
}
